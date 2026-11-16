package com.example.androidwebsiteblocker.core.schedule

import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Reference week: Sun 2026-11-15 … Sat 2026-11-21.
class WeeklyScheduleTest {

    private fun at(text: String) = LocalDateTime.parse(text)
    private fun hm(h: Int, m: Int = 0) = h * 60 + m
    private val la = ZoneId.of("America/Los_Angeles")

    @Test fun `start is inclusive, end is exclusive`() {
        val s = WeeklySchedule(listOf(DayWindow(MONDAY, hm(9), hm(17))))
        assertFalse(s.isActive(at("2026-11-16T08:59:59")))
        assertTrue(s.isActive(at("2026-11-16T09:00")))
        assertTrue(s.isActive(at("2026-11-16T16:59:59")))
        assertFalse(s.isActive(at("2026-11-16T17:00")))
        assertFalse(s.isActive(at("2026-11-17T10:00")))
    }

    @Test fun `empty schedule is never active`() {
        val s = WeeklySchedule(emptyList())
        assertTrue(s.isEmpty)
        assertNull(s.occurrenceAt(at("2026-11-16T10:00")))
        assertNull(s.nextChange(at("2026-11-16T10:00")))
    }

    @Test fun `window crossing midnight is one occurrence`() {
        val s = WeeklySchedule(listOf(
            DayWindow(MONDAY, hm(23, 30), MINUTES_PER_DAY),
            DayWindow(TUESDAY, 0, hm(8)),
        ))
        val beforeMidnight = s.occurrenceAt(at("2026-11-16T23:45"))!!
        val afterMidnight = s.occurrenceAt(at("2026-11-17T03:00"))!!
        assertEquals(beforeMidnight, afterMidnight)
        assertEquals(at("2026-11-16T23:30"), afterMidnight.start)
        assertEquals(at("2026-11-17T08:00"), afterMidnight.end)
    }

    @Test fun `consecutive nights are separate occurrences with different keys`() {
        val s = WeeklySchedule(listOf(
            DayWindow(MONDAY, hm(23, 30), MINUTES_PER_DAY), DayWindow(TUESDAY, 0, hm(8)),
            DayWindow(TUESDAY, hm(23, 30), MINUTES_PER_DAY), DayWindow(java.time.DayOfWeek.WEDNESDAY, 0, hm(8)),
        ))
        val mon = s.occurrenceAt(at("2026-11-17T01:00"))!!
        val tue = s.occurrenceAt(at("2026-11-18T01:00"))!!
        assertTrue(mon.key != tue.key)
    }

    @Test fun `saturday night wraps into sunday morning`() {
        val s = WeeklySchedule(listOf(
            DayWindow(SUNDAY, 0, hm(8)),
            DayWindow(SATURDAY, hm(22), MINUTES_PER_DAY),
        ))
        val sat = s.occurrenceAt(at("2026-11-21T23:00"))!!
        val sun = s.occurrenceAt(at("2026-11-22T07:00"))!!
        assertEquals(sat, sun)
        assertEquals(at("2026-11-21T22:00"), sun.start)
        assertEquals(at("2026-11-22T08:00"), sun.end)
        // Sunday morning at the start of the reference week belongs to the previous Saturday.
        assertEquals(at("2026-11-14T22:00"), s.occurrenceAt(at("2026-11-15T07:00"))!!.start)
    }

    @Test fun `adjacent and overlapping windows merge`() {
        val s = WeeklySchedule(listOf(
            DayWindow(FRIDAY, hm(9), hm(12)),
            DayWindow(FRIDAY, hm(12), hm(14)),
            DayWindow(FRIDAY, hm(13), hm(15)),
        ))
        val occ = s.occurrenceAt(at("2026-11-20T12:30"))!!
        assertEquals(at("2026-11-20T09:00"), occ.start)
        assertEquals(at("2026-11-20T15:00"), occ.end)
    }

    @Test fun `whole week covered resets each sunday`() {
        val s = WeeklySchedule(java.time.DayOfWeek.entries.map { DayWindow(it, 0, MINUTES_PER_DAY) })
        assertEquals(at("2026-11-15T00:00"), s.occurrenceAt(at("2026-11-21T23:59"))!!.start)
        assertEquals(at("2026-11-22T00:00"), s.occurrenceAt(at("2026-11-22T00:00"))!!.start)
    }

    @Test fun `next change finds upcoming start and end`() {
        val s = WeeklySchedule(listOf(DayWindow(MONDAY, hm(9), hm(17))))
        assertEquals(at("2026-11-16T09:00"), s.nextChange(at("2026-11-16T08:00")))
        assertEquals(at("2026-11-16T17:00"), s.nextChange(at("2026-11-16T09:00")))
        assertEquals(at("2026-11-23T09:00"), s.nextChange(at("2026-11-16T17:00")))
    }

    // --- Daylight saving (US 2026: spring forward Sun Mar 8, fall back Sun Nov 1) ---

    @Test fun `spring forward keeps wall-clock window`() {
        val s = WeeklySchedule(listOf(DayWindow(SUNDAY, 0, hm(8))))
        val instant7am = ZonedDateTime.of(2026, 3, 8, 7, 59, 0, 0, la).toInstant()
        val instant8am = ZonedDateTime.of(2026, 3, 8, 8, 0, 0, 0, la).toInstant()
        assertTrue(s.occurrenceAt(instant7am, la) != null)
        assertNull(s.occurrenceAt(instant8am, la))
        // Window end converts to 8:00 PDT (15:00 UTC), not shifted by an hour.
        val end = s.nextChange(ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, la).toInstant(), la)
        assertEquals(instant8am, end)
    }

    @Test fun `fall back keeps wall-clock window`() {
        val s = WeeklySchedule(listOf(DayWindow(SUNDAY, 0, hm(8))))
        val end = s.nextChange(ZonedDateTime.of(2026, 11, 1, 1, 0, 0, 0, la).toInstant(), la)
        assertEquals(ZonedDateTime.of(2026, 11, 1, 8, 0, 0, 0, la).toInstant(), end)
        // Both passes through the repeated 1:30am are in the same occurrence.
        val first = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, la).withEarlierOffsetAtOverlap().toInstant()
        val second = ZonedDateTime.of(2026, 11, 1, 1, 30, 0, 0, la).withLaterOffsetAtOverlap().toInstant()
        assertEquals(s.occurrenceAt(first, la), s.occurrenceAt(second, la))
    }

    @Test fun `start inside spring-forward gap converts to end of gap`() {
        val s = WeeklySchedule(listOf(DayWindow(SUNDAY, hm(2, 30), hm(8))))
        val start = s.nextChange(ZonedDateTime.of(2026, 3, 8, 1, 0, 0, 0, la).toInstant(), la)
        // 2:30am doesn't exist; the window becomes active at 3:00am PDT.
        assertEquals(ZonedDateTime.of(2026, 3, 8, 3, 0, 0, 0, la).toInstant(), start)
        assertTrue(s.occurrenceAt(start!!, la) != null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects empty day window`() {
        DayWindow(MONDAY, hm(9), hm(9))
    }
}
