package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.Target
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What to do about the current foreground app/site. */
sealed interface Decision {
    /** Nothing applies. */
    data object Allow : Decision

    /**
     * Inside an unlocked soft-block open. [remaining] is the time until the open (or the total
     * limit) runs out, or null if unlimited — re-evaluate after it elapses.
     */
    data class InSession(val remaining: Duration?) : Decision

    /** Show the block screen with only a "go home" button. [blockers] is sorted; the first is the primary reason. */
    data class Blocked(val blockers: List<Blocker>) : Decision {
        init { require(blockers.isNotEmpty()) }
        val primary: Blocker get() = blockers.first()
    }

    /**
     * Show the soft block screen. Pressing continue (after [unlockDelay]) uses one open from every
     * gate in [gates]. [opens] is the most constrained open count among them, for the circles UI.
     */
    data class SoftGate(val gates: List<Gate>, val unlockDelay: Duration, val opens: OpenCount?) : Decision {
        init { require(gates.isNotEmpty()) }
        val primary: Gate get() = gates.first()
    }
}

data class Blocker(
    val setId: String,
    val setName: String,
    val target: Target,
    val reason: BlockReason,
    /** When this block lifts (end of the window occurrence), in local wall-clock time. */
    val until: LocalDateTime,
) {
    /** Human-readable "why", e.g. "Night Socials: screen time exceeded 15 min". */
    fun describe(): String = "$setName: " + when (reason) {
        BlockReason.Scheduled -> "blocked until ${until.format(TIME_FORMAT)}"
        is BlockReason.TotalTimeExceeded -> "screen time exceeded ${formatDuration(reason.limit)}"
        is BlockReason.OpensExhausted -> "all ${reason.maxOpens} opens used"
    }
}

data class Gate(
    val setId: String,
    val setName: String,
    val target: Target,
    val opensUsed: Int,
    val maxOpens: Int?,
    /** Time used this window, for "8 min of 12 min left". */
    val timeUsed: Duration = Duration.ZERO,
    val totalLimit: Duration? = null,
) {
    val timeLeft: Duration? get() = totalLimit?.let { (it - timeUsed).coerceAtLeast(Duration.ZERO) }
}

data class OpenCount(val used: Int, val max: Int) {
    val remaining: Int get() = max - used
}

sealed interface BlockReason {
    /** Hard block set within its window. */
    data object Scheduled : BlockReason
    /** Soft block whose total time limit is used up. */
    data class TotalTimeExceeded(val limit: Duration) : BlockReason
    /** Soft block whose opens are all used. */
    data class OpensExhausted(val maxOpens: Int) : BlockReason
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun formatDuration(d: Duration): String {
    val h = d.toHours()
    val m = d.toMinutes() % 60
    val s = d.seconds % 60
    return buildList {
        if (h > 0) add("$h h")
        if (m > 0) add("$m min")
        if (s > 0 || (h == 0L && m == 0L)) add("$s s")
    }.joinToString(" ")
}
