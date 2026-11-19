package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWarningTest {
    private var now = Instant.parse("2026-11-16T10:00:00Z")
    private val allWeek = WeeklySchedule(DayOfWeek.entries.map { DayWindow(it, 0, MINUTES_PER_DAY) })
    private val app = Foreground("com.a")
    private fun min(n: Long) = Duration.ofMinutes(n)

    private fun engine(limits: SoftLimits) = BlockEngine(
        ZoneOffset.UTC, listOf(BlockSet("s", "Night Socials", BlockMode.SOFT, setOf(Target.App("com.a")), allWeek, limits)),
    ).also { it.onForegroundChanged(app, now); it.unlock(now) }

    private fun BlockEngine.advanceTo(minutes: Long): List<Duration> {
        now = Instant.parse("2026-11-16T10:00:00Z").plus(min(minutes))
        tick(now)
        return collectTimeWarnings(now).map { it.threshold }
    }

    @Test fun `warns at 10 and 1 minutes left, once each`() {
        val e = engine(SoftLimits(totalLimit = min(30)))
        assertEquals(emptyList<Duration>(), e.collectTimeWarnings(now))
        assertEquals(Instant.parse("2026-11-16T10:20:00Z"), e.nextEvaluationAt(now))
        assertEquals(emptyList<Duration>(), e.advanceTo(19))
        assertEquals(listOf(min(10)), e.advanceTo(20))
        assertEquals(emptyList<Duration>(), e.advanceTo(21))
        assertEquals(Instant.parse("2026-11-16T10:29:00Z"), e.nextEvaluationAt(now))
        assertEquals(listOf(min(1)), e.advanceTo(29))
        assertEquals(emptyList<Duration>(), e.advanceTo(29))
    }

    @Test fun `no 10-minute warning when the whole limit is shorter`() {
        val e = engine(SoftLimits(totalLimit = min(5)))
        assertEquals(emptyList<Duration>(), e.collectTimeWarnings(now))
        assertEquals(listOf(min(1)), e.advanceTo(4))
    }

    @Test fun `jumping past both marks sends only the urgent one`() {
        val e = engine(SoftLimits(totalLimit = min(30)))
        assertEquals(listOf(min(1)), e.advanceTo(29)) // e.g. a late tick
        assertEquals(emptyList<Duration>(), e.advanceTo(29))
    }

    @Test fun `off when disabled, no total limit, or not on screen`() {
        assertEquals(emptyList<Duration>(), engine(SoftLimits(totalLimit = min(30), timeWarnings = false)).advanceTo(25))
        assertEquals(emptyList<Duration>(), engine(SoftLimits(perOpenLimit = min(30))).advanceTo(25))
        val e = engine(SoftLimits(totalLimit = min(30)))
        e.advanceTo(15)
        e.onForegroundChanged(null, now)
        assertEquals(emptyList<Duration>(), e.collectTimeWarnings(now.plus(min(10))))
    }
}
