package com.example.androidwebsiteblocker.core.schedule

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * A time range as the user thinks of it, applied to several days: "Sun–Thu, 11:30 PM – 8:00 AM".
 * [start]/[end] are minutes of the day. If [end] <= [start] the range runs past midnight and ends
 * the next day. Storage stays per-day ([DayWindow]); this is the editing/display view.
 */
data class WindowGroup(val days: Set<DayOfWeek>, val start: Int, val end: Int) {
    init {
        require(days.isNotEmpty())
        require(start in 0 until MINUTES_PER_DAY && end in 0..MINUTES_PER_DAY)
    }

    val overnight: Boolean get() = end in 1..start

    fun toWindows(): List<DayWindow> = days.flatMap { windowsFor(it, start, end) }

    /** "11:30 PM – 8:00 AM (next day)" */
    fun describeTimes(): String =
        if (start == 0 && end == MINUTES_PER_DAY) "All day"
        else "${clockLabel(start)} – ${clockLabel(end)}" + if (overnight) " (next day)" else ""

    /** "Every day", "Weekdays", "Weekends", or compact runs like "Sun–Thu, Sat". */
    fun describeDays(): String = describeDays(days)
}

/** One day's window, split at midnight if [end] <= [start] (and [end] > 0). */
fun windowsFor(day: DayOfWeek, start: Int, end: Int): List<DayWindow> = when {
    end > start -> listOf(DayWindow(day, start, end))
    end == 0 -> listOf(DayWindow(day, start, MINUTES_PER_DAY))
    else -> listOf(DayWindow(day, start, MINUTES_PER_DAY), DayWindow(day.plus(1), 0, end))
}

/**
 * Reassembles per-day windows into groups: the before- and after-midnight pieces of an overnight
 * window become one range, and identical ranges on different days are merged.
 */
fun groupWindows(windows: List<DayWindow>): List<WindowGroup> {
    val pieces = windows.distinct().sortedWith(compareBy({ WeeklySchedule.dayIndex(it.day) }, { it.startMinute }))
    val used = BooleanArray(pieces.size)
    val ranges = mutableListOf<Triple<DayOfWeek, Int, Int>>()

    // Pair "… → midnight" pieces with the next day's "midnight → …" piece.
    for ((i, p) in pieces.withIndex()) {
        if (p.endMinute != MINUTES_PER_DAY || p.startMinute == 0) continue
        val j = pieces.indices.firstOrNull { k ->
            val q = pieces[k]
            !used[k] && k != i && q.day == p.day.plus(1) && q.startMinute == 0 && q.endMinute <= p.startMinute
        } ?: continue
        used[i] = true
        used[j] = true
        ranges += Triple(p.day, p.startMinute, pieces[j].endMinute)
    }
    for ((i, p) in pieces.withIndex()) {
        if (!used[i]) ranges += Triple(p.day, p.startMinute, p.endMinute)
    }

    return ranges.groupBy({ it.second to it.third }, { it.first })
        .map { (times, days) -> WindowGroup(days.toSet(), times.first, times.second) }
        .sortedWith(compareBy({ g -> g.days.minOf { WeeklySchedule.dayIndex(it) } }, { it.start }))
}

private val WEEKDAYS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

fun describeDays(days: Set<DayOfWeek>): String {
    if (days.size == 7) return "Every day"
    if (days == WEEKDAYS) return "Weekdays"
    if (days == WEEKEND) return "Weekends"
    val sorted = days.sortedBy { WeeklySchedule.dayIndex(it) }
    val runs = mutableListOf<MutableList<DayOfWeek>>()
    for (d in sorted) {
        val last = runs.lastOrNull()
        if (last != null && WeeklySchedule.dayIndex(last.last()) + 1 == WeeklySchedule.dayIndex(d)) last += d else runs += mutableListOf(d)
    }
    return runs.joinToString(", ") { run ->
        when (run.size) {
            1 -> run[0].shortName()
            2 -> "${run[0].shortName()}, ${run[1].shortName()}"
            else -> "${run.first().shortName()}–${run.last().shortName()}"
        }
    }
}

fun DayOfWeek.shortName(): String = getDisplayName(TextStyle.SHORT, Locale.US)

/** "11:30 PM"; 1440 (end of day) reads as "12:00 AM". */
fun clockLabel(minuteOfDay: Int): String {
    val h24 = (minuteOfDay / 60) % 24
    val m = minuteOfDay % 60
    val h12 = if (h24 % 12 == 0) 12 else h24 % 12
    return "%d:%02d %s".format(h12, m, if (h24 < 12) "AM" else "PM")
}
