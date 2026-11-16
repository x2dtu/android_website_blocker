package com.example.androidwebsiteblocker.core.protection

import com.example.androidwebsiteblocker.core.model.BlockSet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Daily streak for a block set.
 *  - A day counts when a window occurrence that *started* that day completes cleanly.
 *  - Days with no window neither count nor break the streak.
 *  - A suspension, disable, or loosening edit breaks it, and that day can't count.
 */
data class Streak(val days: Int = 0, val lastCountedDay: LocalDate? = null, val brokenDay: LocalDate? = null) {

    /** [day] = start date of the occurrence the break happened in (or today if outside a window). */
    fun broken(day: LocalDate): Streak = copy(days = 0, brokenDay = day)

    /** Call when a window occurrence ends. [day] = the date it started. */
    fun occurrenceCompleted(day: LocalDate): Streak = when (day) {
        brokenDay, lastCountedDay -> this
        else -> copy(days = days + 1, lastCountedDay = day)
    }
}

/** The day a streak break is charged to: the start date of the current occurrence, or today if outside a window. */
fun streakDayFor(set: BlockSet, now: Instant, zone: ZoneId): LocalDate =
    set.schedule.occurrenceAt(now, zone)?.start?.toLocalDate() ?: now.atZone(zone).toLocalDate()
