package com.example.androidwebsiteblocker.core.protection

import com.example.androidwebsiteblocker.core.model.BlockSet
import java.time.Instant
import java.time.ZoneId

/** Suspensions used in one window occurrence. A different occurrence key means a fresh allowance. */
data class SuspensionState(val occurrenceKey: String, val used: Int = 0, val lastEndsAt: Instant? = null)

sealed interface SuspensionCheck {
    /** [remainingAfter] = suspensions left after this one, null if unlimited. */
    data class Allowed(val remainingAfter: Int?) : SuspensionCheck
    /** Nothing to suspend: the set isn't in a window (or is disabled). */
    data object NotInWindow : SuspensionCheck
    data class AlreadySuspended(val until: Instant) : SuspensionCheck
    data class LimitReached(val max: Int) : SuspensionCheck
    data class CoolingDown(val until: Instant) : SuspensionCheck
}

/**
 * Emergency suspension of a whole block set (the PIN is checked by the caller first).
 * Limited per window occurrence, with a cooldown between suspensions.
 */
object SuspensionPolicy {

    fun check(set: BlockSet, state: SuspensionState?, now: Instant, zone: ZoneId): SuspensionCheck {
        if (!set.enabled) return SuspensionCheck.NotInWindow
        val occurrence = set.schedule.occurrenceAt(now, zone) ?: return SuspensionCheck.NotInWindow
        set.suspendedUntil?.takeIf { it.isAfter(now) }?.let { return SuspensionCheck.AlreadySuspended(it) }
        val current = state?.takeIf { it.occurrenceKey == occurrence.key } ?: SuspensionState(occurrence.key)
        val p = set.protection
        if (p.maxSuspensions != null && current.used >= p.maxSuspensions) {
            return SuspensionCheck.LimitReached(p.maxSuspensions)
        }
        current.lastEndsAt?.plus(p.suspensionCooldown)?.takeIf { it.isAfter(now) }?.let {
            return SuspensionCheck.CoolingDown(it)
        }
        return SuspensionCheck.Allowed(p.maxSuspensions?.let { it - current.used - 1 })
    }

    /** Returns the suspended set and the updated state. Throws if [check] doesn't allow it. */
    fun suspend(set: BlockSet, state: SuspensionState?, now: Instant, zone: ZoneId): Pair<BlockSet, SuspensionState> {
        val result = check(set, state, now, zone)
        check(result is SuspensionCheck.Allowed) { "Suspension not allowed: $result" }
        val key = set.schedule.occurrenceAt(now, zone)!!.key
        val used = state?.takeIf { it.occurrenceKey == key }?.used ?: 0
        val until = now.plus(set.protection.suspensionDuration)
        return set.copy(suspendedUntil = until) to SuspensionState(key, used + 1, until)
    }
}
