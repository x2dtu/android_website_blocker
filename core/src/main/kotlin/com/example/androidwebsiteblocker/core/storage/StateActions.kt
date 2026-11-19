package com.example.androidwebsiteblocker.core.storage

import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.protection.ChangeOutcome
import com.example.androidwebsiteblocker.core.protection.Streak
import com.example.androidwebsiteblocker.core.protection.SuspensionCheck
import com.example.androidwebsiteblocker.core.protection.SuspensionPolicy
import com.example.androidwebsiteblocker.core.protection.isLoosening
import com.example.androidwebsiteblocker.core.protection.reviewChange
import com.example.androidwebsiteblocker.core.protection.streakDayFor
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * User actions and housekeeping as pure transitions on [AppState]. PIN checks happen in the UI
 * before calling these.
 */
object StateActions {

    fun addSet(state: AppState, set: BlockSet): AppState {
        require(state.sets.none { it.id == set.id }) { "Duplicate set id ${set.id}" }
        return state.copy(sets = state.sets + set)
    }

    /**
     * Submits an edit (including disable/enable). Tightening applies now; loosening becomes the
     * set's pending change. Any new edit replaces an existing pending change, since that change
     * was based on the older settings.
     */
    fun requestEdit(state: AppState, proposed: BlockSet, now: Instant, zone: ZoneId): Pair<AppState, ChangeOutcome> {
        val current = state.setById(proposed.id) ?: error("No set ${proposed.id}")
        val outcome = reviewChange(current, proposed, now)
        val withoutPending = state.copy(pendingChanges = state.pendingChanges - proposed.id)
        val next = when (outcome) {
            is ChangeOutcome.ApplyNow -> withoutPending.applySet(current, outcome.set, now, zone)
            is ChangeOutcome.Pending -> withoutPending.copy(pendingChanges = withoutPending.pendingChanges + (proposed.id to outcome.change))
        }
        return next to outcome
    }

    fun cancelPending(state: AppState, setId: String): AppState =
        state.copy(pendingChanges = state.pendingChanges - setId)

    /** Emergency suspension. Breaks the streak. */
    fun suspend(state: AppState, setId: String, now: Instant, zone: ZoneId): Pair<AppState, SuspensionCheck> {
        val set = state.setById(setId) ?: error("No set $setId")
        val check = SuspensionPolicy.check(set, state.suspensions[setId], now, zone)
        if (check !is SuspensionCheck.Allowed) return state to check
        val (suspended, suspension) = SuspensionPolicy.suspend(set, state.suspensions[setId], now, zone)
        val next = state.replaceSet(suspended).copy(
            suspensions = state.suspensions + (setId to suspension),
            streaks = state.streaks + (setId to state.streakOf(setId).broken(streakDayFor(set, now, zone))),
        )
        return next to check
    }

    /** Only disabled sets can be deleted, so deleting can't skip the disable wait. */
    fun deleteSet(state: AppState, setId: String): AppState {
        val set = state.setById(setId) ?: return state
        require(!set.enabled) { "Disable the block set before deleting it" }
        return AppState(
            sets = state.sets - set,
            usage = state.usage.filterKeys { it.setId != setId },
            suspensions = state.suspensions - setId,
            pendingChanges = state.pendingChanges - setId,
            streaks = state.streaks - setId,
            activeOccurrences = state.activeOccurrences - setId,
        )
    }

    /** Applies due pending changes and credits streaks for completed window occurrences. */
    fun maintain(state: AppState, now: Instant, zone: ZoneId): AppState {
        var s = state
        for ((id, change) in state.pendingChanges) {
            if (!change.isDue(now)) continue
            val current = s.setById(id)
            s = s.copy(pendingChanges = s.pendingChanges - id)
            if (current != null) s = s.applySet(current, change.applyTo(current), now, zone)
        }
        val occurrences = s.activeOccurrences.toMutableMap()
        val streaks = s.streaks.toMutableMap()
        for (set in s.sets) {
            val current = if (set.enabled) set.schedule.occurrenceAt(now, zone)?.key else null
            val previous = occurrences[set.id]
            if (previous != null && previous != current) {
                streaks[set.id] = (streaks[set.id] ?: Streak()).occurrenceCompleted(LocalDateTime.parse(previous).toLocalDate())
            }
            if (current != null) occurrences[set.id] = current else occurrences.remove(set.id)
        }
        occurrences.keys.retainAll(s.sets.map { it.id }.toSet())
        return s.copy(activeOccurrences = occurrences, streaks = streaks)
    }

    /** When [maintain] next needs to run for pending changes (window ends are covered by the engine's schedule). */
    fun nextPendingAt(state: AppState): Instant? = state.pendingChanges.values.minOfOrNull { it.appliesAt }

    private fun AppState.applySet(old: BlockSet, new: BlockSet, now: Instant, zone: ZoneId): AppState {
        val next = replaceSet(new)
        if (!isLoosening(old, new)) return next
        return next.copy(streaks = streaks + (old.id to streakOf(old.id).broken(streakDayFor(old, now, zone))))
    }
}

fun AppState.setById(id: String): BlockSet? = sets.firstOrNull { it.id == id }

fun AppState.streakOf(id: String): Streak = streaks[id] ?: Streak()

private fun AppState.replaceSet(set: BlockSet): AppState = copy(sets = sets.map { if (it.id == set.id) set else it })
