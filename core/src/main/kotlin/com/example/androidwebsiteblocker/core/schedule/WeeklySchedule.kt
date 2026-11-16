package com.example.androidwebsiteblocker.core.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

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
class WeeklySchedule(windows: List<DayWindow>) {

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
