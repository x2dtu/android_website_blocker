package com.example.androidwebsiteblocker.core.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.BitSet
import java.util.Locale

const val MINUTES_PER_DAY = 24 * 60
const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

/**
 * A blocked segment within a single day, in local wall-clock minutes since midnight.
 * [endMinute] is exclusive; 1440 means "until midnight at the end of the day".
 *
 * A window that crosses midnight (Mon 11:30pm → Tue 8am) is stored as two segments
 * (Mon 23:30–24:00 and Tue 00:00–08:00). [WeeklySchedule] merges them back into one
 * continuous [WindowOccurrence]. This matches the 7-bar UI: each bar shows only its own day.
 */
data class DayWindow(val day: DayOfWeek, val startMinute: Int, val endMinute: Int) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range: $startMinute" }
        require(endMinute in (startMinute + 1)..MINUTES_PER_DAY) { "endMinute must be after start: $endMinute" }
    }
}

/**
 * One continuous active period of a schedule, in local wall-clock time.
 * [start] uniquely identifies the occurrence: all per-occurrence counters (opens, time used,
 * suspensions) are keyed on it and reset when a new occurrence begins.
 */
data class WindowOccurrence(val start: LocalDateTime, val end: LocalDateTime) {
    /** Stable key for persisting per-occurrence state. */
    val key: String get() = start.toString()
}

/**
 * The weekly time windows of a block set. The week runs Sunday 00:00 → Saturday 24:00.
 *
 * All math is done in local wall-clock time (LocalDateTime), so a 12am–8am window is always
 * 12am–8am regardless of daylight saving. Converting boundaries to real instants (for alarms)
 * goes through [toInstant], which handles DST gaps and overlaps.
 *
 * Fall-back caveat: if a window ends inside the repeated hour (e.g. ends at 1:30am on the night
 * clocks go back), it ends at the first 1:30 and re-activates for the repeat. It's still the same
 * occurrence (same start), so counters carry over.
 *
 * If every minute of the week is covered, there is one occurrence per week starting Sunday 00:00.
 */
class WeeklySchedule(val windows: List<DayWindow>) {

    /** Half-open interval in minute-of-week. [start] is in [0, WEEK); [end] may exceed WEEK if it wraps past Saturday. */
    private data class Span(val start: Int, val end: Int)

    private val intervals: List<Span> = merge(windows)

    val isEmpty: Boolean get() = intervals.isEmpty()

    fun occurrenceAt(now: LocalDateTime): WindowOccurrence? {
        for (candidate in candidateOccurrences(now)) {
            if (!now.isBefore(candidate.start) && now.isBefore(candidate.end)) return candidate
        }
        return null
    }

    fun occurrenceAt(now: Instant, zone: ZoneId): WindowOccurrence? =
        occurrenceAt(LocalDateTime.ofInstant(now, zone))

    fun isActive(now: LocalDateTime): Boolean = occurrenceAt(now) != null

    /** The next wall-clock time strictly after [now] at which the schedule turns on or off, or null if it never changes. */
    fun nextChange(now: LocalDateTime): LocalDateTime? =
        candidateOccurrences(now)
            .flatMap { listOf(it.start, it.end) }
            .filter { it.isAfter(now) }
            .minOrNull()

    fun nextChange(now: Instant, zone: ZoneId): Instant? =
        nextChange(LocalDateTime.ofInstant(now, zone))?.let { toInstant(it, zone) }

    /** Times (in the weekly pattern) when both this and [other] are active. Wrap-around spans are joined. */
    fun overlapWith(other: WeeklySchedule): List<WeekSpan> {
        val both = coverage().apply { and(other.coverage()) }
        return spansOf(both)
    }

    /** True if every minute [other] is active, this is too. */
    fun covers(other: WeeklySchedule): Boolean =
        other.coverage().apply { andNot(this@WeeklySchedule.coverage()) }.isEmpty

    /** Equal when active at exactly the same times, regardless of how the windows were entered. */
    override fun equals(other: Any?): Boolean = other is WeeklySchedule && intervals == other.intervals
    override fun hashCode(): Int = intervals.hashCode()

    private fun coverage(): BitSet = BitSet(MINUTES_PER_WEEK).apply {
        for (span in intervals) {
            if (span.end <= MINUTES_PER_WEEK) {
                set(span.start, span.end)
            } else {
                set(span.start, MINUTES_PER_WEEK)
                set(0, span.end - MINUTES_PER_WEEK)
            }
        }
    }

    /** Occurrences anchored to last, this, and next week — enough to cover any wrap-around. */
    private fun candidateOccurrences(now: LocalDateTime): List<WindowOccurrence> {
        val thisWeek = now.toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            .atStartOfDay()
        return listOf(thisWeek.minusWeeks(1), thisWeek, thisWeek.plusWeeks(1)).flatMap { weekStart ->
            intervals.map { span ->
                WindowOccurrence(
                    start = weekStart.plusMinutes(span.start.toLong()),
                    end = weekStart.plusMinutes(span.end.toLong()),
                )
            }
        }
    }

    companion object {
        private fun spansOf(bits: BitSet): List<WeekSpan> {
            val spans = mutableListOf<WeekSpan>()
            var start = bits.nextSetBit(0)
            while (start >= 0) {
                val end = bits.nextClearBit(start).coerceAtMost(MINUTES_PER_WEEK)
                spans += WeekSpan(start, end)
                start = bits.nextSetBit(end)
            }
            if (spans.size > 1 && spans.first().start == 0 && spans.last().end == MINUTES_PER_WEEK) {
                val first = spans.removeAt(0)
                val last = spans.removeAt(spans.lastIndex)
                spans += WeekSpan(last.start, MINUTES_PER_WEEK + first.end)
            }
            return spans
        }

        /** Sunday = 0 … Saturday = 6. */
        fun dayIndex(day: DayOfWeek): Int = day.value % 7

        private fun merge(windows: List<DayWindow>): List<Span> {
            val sorted = windows
                .map { w -> dayIndex(w.day) * MINUTES_PER_DAY + w.startMinute to dayIndex(w.day) * MINUTES_PER_DAY + w.endMinute }
                .sortedBy { it.first }
            val merged = mutableListOf<Pair<Int, Int>>()
            for ((s, e) in sorted) {
                val last = merged.lastOrNull()
                if (last != null && s <= last.second) {
                    merged[merged.lastIndex] = last.first to maxOf(last.second, e)
                } else {
                    merged += s to e
                }
            }
            // Saturday-night window running into Sunday morning: join last and first.
            if (merged.size > 1 && merged.first().first == 0 && merged.last().second == MINUTES_PER_WEEK) {
                val first = merged.removeAt(0)
                val last = merged.removeAt(merged.lastIndex)
                merged += last.first to (MINUTES_PER_WEEK + first.second)
            }
            return merged.map { (s, e) -> Span(s, e) }
        }

        /**
         * Converts a wall-clock boundary to a real instant. If the time falls in a DST gap
         * (doesn't exist, e.g. 2:30am on spring-forward night), returns the moment the gap ends,
         * which is the first real instant at or after that wall-clock time.
         */
        fun toInstant(local: LocalDateTime, zone: ZoneId): Instant {
            val rules = zone.rules
            if (rules.getValidOffsets(local).isEmpty()) {
                return rules.getTransition(local).instant
            }
            return local.atZone(zone).withEarlierOffsetAtOverlap().toInstant()
        }
    }
}

/**
 * A span of the weekly pattern in minutes since Sunday 00:00. [end] is exclusive and may exceed
 * one week when the span wraps from Saturday night into Sunday.
 */
data class WeekSpan(val start: Int, val end: Int) {
    /** e.g. "Mon 11:30 PM – Tue 8:00 AM", or "Fri 9:00 AM – 5:00 PM" within one day. */
    fun describe(): String {
        val endMinute = end - 1 // label the end by the day of its last covered minute
        val startDay = start / MINUTES_PER_DAY
        val endDay = endMinute / MINUTES_PER_DAY
        val startLabel = "${dayName(startDay)} ${clock(start % MINUTES_PER_DAY)}"
        val endLabel = clock(endMinute % MINUTES_PER_DAY + 1)
        return if (startDay == endDay) "$startLabel – $endLabel" else "$startLabel – ${dayName(endDay)} $endLabel"
    }

    private fun dayName(index: Int): String =
        DayOfWeek.SUNDAY.plus((index % 7).toLong()).getDisplayName(TextStyle.SHORT, Locale.US)

    private fun clock(minuteOfDay: Int): String {
        val h24 = (minuteOfDay / 60) % 24
        val m = minuteOfDay % 60
        val h12 = if (h24 % 12 == 0) 12 else h24 % 12
        val ampm = if (h24 < 12) "AM" else "PM"
        return "%d:%02d %s".format(h12, m, ampm)
    }
}
