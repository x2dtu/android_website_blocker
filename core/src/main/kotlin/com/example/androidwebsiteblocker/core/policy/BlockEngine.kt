package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.WindowOccurrence
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Usage is tracked per (block set, target, window occurrence), so it resets every occurrence. */
data class UsageKey(val setId: String, val target: Target, val occurrenceKey: String)

/** The current soft-block open. [leftAt] is set while the user is away from the target (grace period). */
data class OpenSession(val used: Duration = Duration.ZERO, val leftAt: Instant? = null)

data class SoftUsage(
    val opensUsed: Int = 0,
    val timeUsed: Duration = Duration.ZERO,
    val session: OpenSession? = null,
)

/**
 * Decides whether the foreground app/site is allowed, and does the screen-time accounting.
 *
 * Driven by events from the app layer:
 *  - [onForegroundChanged] whenever the foreground app/site changes (null = screen off / nothing
 *    trackable). Transient system UI (notification shade, recents, keyboard) must NOT be reported.
 *  - [tick] when [nextEvaluationAt] is reached, so limits and window boundaries take effect on time.
 *  - [unlock] when the user presses "continue" on a soft block screen. The unlock delay countdown
 *    is enforced by the UI.
 *
 * Rules:
 *  - Most restrictive wins: any hard block (including an exhausted soft block) beats a soft gate,
 *    and a soft gate beats an open session. With several soft sets, all must be unlocked.
 *  - Time is only counted while the target is on screen inside an unlocked open, inside an active,
 *    enabled, non-suspended window. It counts against every soft set that matches the target.
 *  - Leaving and returning within the set's grace time continues the same open.
 */
class BlockEngine(
    private val zone: ZoneId,
    sets: List<BlockSet> = emptyList(),
    usage: Map<UsageKey, SoftUsage> = emptyMap(),
) {
    var sets: List<BlockSet> = sets
        private set

    private val _usage = usage.toMutableMap()
    val usage: Map<UsageKey, SoftUsage> get() = _usage.toMap()

    var foreground: Foreground? = null
        private set

    private var lastAccrualAt: Instant? = null

    fun updateSets(newSets: List<BlockSet>, now: Instant): Decision {
        accrue(now)
        sets = newSets
        prune(now)
        return evaluate(foreground, now)
    }

    fun onForegroundChanged(newForeground: Foreground?, now: Instant): Decision {
        accrue(now)
        val old = foreground
        foreground = newForeground
        for ((key, u) in _usage.entries.toList()) {
            val session = u.session ?: continue
            val wasOn = old != null && key.target.matches(old)
            val isOn = newForeground != null && key.target.matches(newForeground)
            val limits = setById(key.setId)?.softLimits ?: continue
            when {
                wasOn && !isOn -> _usage[key] = u.copy(session = session.copy(leftAt = now))
                isOn && session.leftAt != null -> _usage[key] = u.copy(
                    session = if (liveSession(u, limits, now) != null) session.copy(leftAt = null) else null,
                )
            }
        }
        return evaluate(foreground, now)
    }

    fun tick(now: Instant): Decision {
        accrue(now)
        prune(now)
        return evaluate(foreground, now)
    }

    /** User pressed continue on the soft block screen. Starts a new open for every gate. */
    fun unlock(now: Instant): Decision {
        accrue(now)
        val fg = foreground ?: return Decision.Allow
        val decision = evaluate(fg, now)
        if (decision !is Decision.SoftGate) return decision
        for (pair in activePairs(fg, now)) {
            if (pair.set.mode != BlockMode.SOFT) continue
            val key = pair.key
            val u = _usage[key] ?: SoftUsage()
            if (liveSession(u, pair.set.softLimits, now) != null) continue
            _usage[key] = u.copy(opensUsed = u.opensUsed + 1, session = OpenSession())
        }
        return evaluate(fg, now)
    }

    /** Read-only decision for [fg] at [now]. */
    fun evaluate(fg: Foreground?, now: Instant): Decision {
        if (fg == null) return Decision.Allow
        val blockers = mutableListOf<Blocker>()
        val gates = mutableListOf<Pair<Gate, Duration>>()
        val sessions = mutableListOf<Duration?>()

        for (pair in activePairs(fg, now)) {
            val set = pair.set
            val until = pair.occurrence.end
            if (set.mode == BlockMode.HARD) {
                blockers += Blocker(set.id, set.name, pair.target, BlockReason.Scheduled, until)
                continue
            }
            val limits = set.softLimits
            val u = _usage[pair.key] ?: SoftUsage()
            val session = liveSession(u, limits, now)
            when {
                limits.totalLimit != null && u.timeUsed >= limits.totalLimit ->
                    blockers += Blocker(set.id, set.name, pair.target, BlockReason.TotalTimeExceeded(limits.totalLimit), until)
                session != null -> sessions += listOfNotNull(
                    limits.perOpenLimit?.minus(session.used),
                    limits.totalLimit?.minus(u.timeUsed),
                ).minOrNull()
                limits.maxOpens != null && u.opensUsed >= limits.maxOpens ->
                    blockers += Blocker(set.id, set.name, pair.target, BlockReason.OpensExhausted(limits.maxOpens), until)
                else -> gates += Gate(set.id, set.name, pair.target, u.opensUsed, limits.maxOpens) to limits.unlockDelay
            }
        }

        if (blockers.isNotEmpty()) {
            return Decision.Blocked(blockers.sortedByDescending { it.until })
        }
        if (gates.isNotEmpty()) {
            val sorted = gates.map { it.first }
                .sortedBy { g -> g.maxOpens?.let { it - g.opensUsed } ?: Int.MAX_VALUE }
            val opens = sorted.first().let { g -> g.maxOpens?.let { OpenCount(g.opensUsed, it) } }
            return Decision.SoftGate(sorted, gates.maxOf { it.second }, opens)
        }
        if (sessions.isNotEmpty()) {
            return Decision.InSession(sessions.filterNotNull().minOrNull())
        }
        return Decision.Allow
    }

    /** When the app should call [tick] next, or null if nothing will change on its own. */
    fun nextEvaluationAt(now: Instant): Instant? {
        val candidates = mutableListOf<Instant>()
        for (set in sets) {
            if (!set.enabled) continue
            set.schedule.nextChange(now, zone)?.let { candidates += it }
            set.suspendedUntil?.takeIf { it.isAfter(now) }?.let { candidates += it }
        }
        (evaluate(foreground, now) as? Decision.InSession)?.remaining?.let { candidates += now.plus(it) }
        return candidates.minOrNull()
    }

    private data class ActivePair(val set: BlockSet, val target: Target, val occurrence: WindowOccurrence) {
        val key get() = UsageKey(set.id, target, occurrence.key)
    }

    private fun activePairs(fg: Foreground, now: Instant): List<ActivePair> = buildList {
        for (set in sets) {
            if (!set.enabled || set.isSuspended(now)) continue
            val occurrence = set.schedule.occurrenceAt(now, zone) ?: continue
            for (target in set.targets) {
                if (target.matches(fg)) add(ActivePair(set, target, occurrence))
            }
        }
    }

    /** The open still counts if the user hasn't been away longer than the grace time and it has time left. */
    private fun liveSession(u: SoftUsage, limits: SoftLimits, now: Instant): OpenSession? {
        val s = u.session ?: return null
        if (s.leftAt != null && Duration.between(s.leftAt, now) > limits.graceTime) return null
        if (limits.perOpenLimit != null && s.used >= limits.perOpenLimit) return null
        return s
    }

    /** Adds on-screen time since the last event to every open session the foreground is using. */
    private fun accrue(now: Instant) {
        val from = lastAccrualAt
        lastAccrualAt = now
        val fg = foreground ?: return
        if (from == null || !now.isAfter(from)) return
        if (evaluate(fg, from) !is Decision.InSession) return
        val elapsed = Duration.between(from, now)
        for (pair in activePairs(fg, from)) {
            if (pair.set.mode != BlockMode.SOFT) continue
            val u = _usage[pair.key] ?: continue
            val session = liveSession(u, pair.set.softLimits, from) ?: continue
            _usage[pair.key] = u.copy(
                timeUsed = u.timeUsed + elapsed,
                session = session.copy(used = session.used + elapsed),
            )
        }
    }

    /** Drops usage from past occurrences and deleted sets. */
    private fun prune(now: Instant) {
        val current = sets.associate { it.id to it.schedule.occurrenceAt(now, zone)?.key }
        _usage.keys.removeAll { key -> current[key.setId] != key.occurrenceKey }
    }

    private fun setById(id: String) = sets.firstOrNull { it.id == id }
}
