package com.example.androidwebsiteblocker.core.schedule

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import org.junit.Assert.assertEquals
import org.junit.Test

class WindowGroupsTest {
    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    @Test fun `overnight windows on several days become one group`() {
        val days = setOf(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, SATURDAY)
        val group = WindowGroup(days, hm(23, 30), hm(8))
        val windows = group.toWindows()
        assertEquals(12, windows.size)
        val groups = groupWindows(windows.shuffled())
        assertEquals(listOf(group), groups)
        assertEquals("Sun–Thu, Sat", groups.single().describeDays())
        assertEquals("11:30 PM – 8:00 AM (next day)", groups.single().describeTimes())
    }

    @Test fun `saturday night pairs with sunday morning`() {
        val windows = WindowGroup(setOf(SATURDAY), hm(22), hm(7)).toWindows()
        assertEquals(listOf(WindowGroup(setOf(SATURDAY), hm(22), hm(7))), groupWindows(windows))
    }

    @Test fun `separate ranges stay separate and sorted`() {
        val windows = WindowGroup(setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY), hm(9), hm(17)).toWindows() +
            WindowGroup(setOf(FRIDAY, SATURDAY), hm(22), hm(2)).toWindows()
        val groups = groupWindows(windows)
        assertEquals(2, groups.size)
        assertEquals("Weekdays", groups[0].describeDays())
        assertEquals("9:00 AM – 5:00 PM", groups[0].describeTimes())
        assertEquals("Fri, Sat", groups[1].describeDays())
    }

    @Test fun `a lone morning window is not mistaken for an overnight tail`() {
        val windows = listOf(DayWindow(MONDAY, 0, hm(8)), DayWindow(SUNDAY, hm(12), hm(13)))
        val groups = groupWindows(windows)
        assertEquals(setOf(WindowGroup(setOf(MONDAY), 0, hm(8)), WindowGroup(setOf(SUNDAY), hm(12), hm(13))), groups.toSet())
    }

    @Test fun `until midnight and all day`() {
        assertEquals("10:00 PM – 12:00 AM", WindowGroup(setOf(FRIDAY), hm(22), MINUTES_PER_DAY).describeTimes())
        assertEquals(listOf(DayWindow(FRIDAY, hm(22), MINUTES_PER_DAY)), WindowGroup(setOf(FRIDAY), hm(22), 0).toWindows())
        val allWeek = WindowGroup(DayOfWeek.entries.toSet(), 0, MINUTES_PER_DAY)
        assertEquals("Every day", allWeek.describeDays())
        assertEquals("All day", allWeek.describeTimes())
        assertEquals(listOf(allWeek), groupWindows(allWeek.toWindows()))
    }

    @Test fun `round trip preserves coverage for arbitrary input`() {
        val windows = listOf(
            DayWindow(MONDAY, hm(23), MINUTES_PER_DAY), DayWindow(TUESDAY, 0, hm(6)),
            DayWindow(TUESDAY, hm(12), hm(13)), DayWindow(WEDNESDAY, 0, hm(3)),
        )
        val back = groupWindows(windows).flatMap { it.toWindows() }
        assertEquals(WeeklySchedule(windows), WeeklySchedule(back))
    }
}
