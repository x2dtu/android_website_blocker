package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlapCheckTest {
    private fun hm(h: Int, m: Int = 0) = h * 60 + m
    private val insta = Target.App("com.instagram.android")

    private fun set(id: String, targets: Set<Target>, vararg windows: DayWindow) =
        BlockSet(id, id.replaceFirstChar { it.uppercase() }, BlockMode.HARD, targets, WeeklySchedule(windows.toList()))

    @Test fun `reports overlapping times for a shared app`() {
        val bedtime = set("bedtime", setOf(insta),
            DayWindow(MONDAY, hm(23), MINUTES_PER_DAY), DayWindow(TUESDAY, 0, hm(8)))
        val edited = set("night", setOf(insta),
            DayWindow(MONDAY, hm(23, 30), MINUTES_PER_DAY), DayWindow(TUESDAY, 0, hm(9)))
        val w = findOverlaps(edited, listOf(bedtime)).single()
        assertEquals("Instagram is also in Bedtime: Mon 11:30 PM – Tue 8:00 AM", w.describe("Instagram"))
    }

    @Test fun `same app at non-overlapping times is fine`() {
        val day = set("day", setOf(insta), DayWindow(FRIDAY, hm(9), hm(17)))
        val night = set("night", setOf(insta), DayWindow(FRIDAY, hm(17), hm(23)))
        assertTrue(findOverlaps(night, listOf(day)).isEmpty())
    }

    @Test fun `different apps at the same time is fine`() {
        val a = set("a", setOf(Target.App("com.a")), DayWindow(FRIDAY, hm(9), hm(17)))
        val b = set("b", setOf(Target.App("com.b")), DayWindow(FRIDAY, hm(9), hm(17)))
        assertTrue(findOverlaps(a, listOf(b)).isEmpty())
    }

    @Test fun `subdomain websites overlap`() {
        val a = set("a", setOf(Target.Website("reddit.com")), DayWindow(FRIDAY, hm(9), hm(17)))
        val b = set("b", setOf(Target.Website("old.reddit.com")), DayWindow(FRIDAY, hm(12), hm(13)))
        assertEquals("Fri 12:00 PM – 1:00 PM", findOverlaps(a, listOf(b)).single().spans.single().describe())
        val c = set("c", setOf(Target.Website("notreddit.com")), DayWindow(FRIDAY, hm(12), hm(13)))
        assertTrue(findOverlaps(a, listOf(c)).isEmpty())
    }

    @Test fun `ignores itself and joins saturday-sunday wrap`() {
        val windows = arrayOf(DayWindow(SATURDAY, hm(22), MINUTES_PER_DAY), DayWindow(SUNDAY, 0, hm(8)))
        val a = set("a", setOf(insta), *windows)
        val b = set("b", setOf(insta), *windows)
        assertTrue(findOverlaps(a, listOf(a)).isEmpty())
        assertEquals("Sat 10:00 PM – Sun 8:00 AM", findOverlaps(a, listOf(b)).single().spans.single().describe())
    }

    @Test fun `window ending at midnight reads as 12 AM`() {
        val a = set("a", setOf(insta), DayWindow(FRIDAY, hm(22), MINUTES_PER_DAY))
        assertEquals("Fri 10:00 PM – 12:00 AM", findOverlaps(a, listOf(a.copy(id = "b"))).single().spans.single().describe())
    }
}
