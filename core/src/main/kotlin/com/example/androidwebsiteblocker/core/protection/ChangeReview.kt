package com.example.androidwebsiteblocker.core.protection

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.LimitScope
import java.time.Instant

/** A disable or loosening edit waiting out the set's change wait. One per set; a newer edit replaces it. */
data class PendingChange(val setId: String, val proposed: BlockSet, val requestedAt: Instant, val appliesAt: Instant) {
    fun isDue(now: Instant): Boolean = !now.isBefore(appliesAt)

    /** Applies on top of the current set, keeping runtime state like an active suspension. */
    fun applyTo(current: BlockSet): BlockSet = proposed.copy(suspendedUntil = current.suspendedUntil)
}

sealed interface ChangeOutcome {
    data class ApplyNow(val set: BlockSet) : ChangeOutcome
    data class Pending(val change: PendingChange) : ChangeOutcome
}

/**
 * Decides whether an edit (already PIN-verified) applies now or waits.
 * Tightening and cosmetic edits apply immediately. Anything that loosens — even mixed with
 * tightening — waits for the *current* set's change wait, so lowering the wait itself also waits.
 */
fun reviewChange(current: BlockSet, proposed: BlockSet, now: Instant): ChangeOutcome {
    require(current.id == proposed.id)
    if (!isLoosening(current, proposed)) return ChangeOutcome.ApplyNow(proposed.copy(suspendedUntil = current.suspendedUntil))
    val wait = current.protection.changeWait
    if (wait.isZero) return ChangeOutcome.ApplyNow(proposed.copy(suspendedUntil = current.suspendedUntil))
    return ChangeOutcome.Pending(PendingChange(current.id, proposed, now, now.plus(wait)))
}

/** True if [new] would block anything less than [old] does, or weakens its protections. */
fun isLoosening(old: BlockSet, new: BlockSet): Boolean {
    if (old.enabled && !new.enabled) return true
    if (!new.enabled) return false // editing an already-disabled set can't loosen anything further
    if (!old.enabled) return false // re-enabling is tightening
    if (new.mode.strictness < old.mode.strictness) return true
    if (!new.targets.containsAll(old.targets)) return true
    if (!new.schedule.covers(old.schedule)) return true

    if (old.mode == BlockMode.SOFT && new.mode == BlockMode.SOFT) {
        val o = old.softLimits
        val n = new.softLimits
        if (n.unlockDelay < o.unlockDelay) return true
        if (looser(o.maxOpens, n.maxOpens)) return true
        if (looser(o.perOpenLimit, n.perOpenLimit)) return true
        if (looser(o.totalLimit, n.totalLimit)) return true
        if (n.graceTime > o.graceTime) return true
        if (old.limitScope == LimitScope.WHOLE_SET && new.limitScope == LimitScope.EACH_TARGET) return true
    }

    val o = old.protection
    val n = new.protection
    if (n.suspensionDuration > o.suspensionDuration) return true
    if (looser(o.maxSuspensions, n.maxSuspensions)) return true
    if (n.suspensionCooldown < o.suspensionCooldown) return true
    if (n.changeWait < o.changeWait) return true
    return false
}

/** For limits where null means unlimited. */
private fun <T : Comparable<T>> looser(old: T?, new: T?): Boolean = old != null && (new == null || new > old)

