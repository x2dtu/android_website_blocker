package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.LimitScope
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.protection.isLoosening
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderTest {
    private val start = Instant.parse("2026-11-16T10:00:00Z")
    private var now = start
    private val allWeek = WeeklySchedule(DayOfWeek.entries.map { DayWindow(it, 0, MINUTES_PER_DAY) })
    private val insta = Foreground("com.instagram.android")
    private val tiktok = Foreground("com.zhiliaoapp.musically")
    private fun min(n: Long) = Duration.ofMinutes(n)

    private fun reminderSet(scope: LimitScope = LimitScope.EACH_TARGET, interval: Duration? = min(15)) = BlockSet(
        "r", "Awareness", BlockMode.REMINDER,
        setOf(Target.App(insta.packageName), Target.App(tiktok.packageName)), allWeek,
        SoftLimits(reminderInterval = interval), limitScope = scope,
    )

    private fun BlockEngine.at(minutes: Long): List<Duration> {
        now = start.plus(min(minutes))
        tick(now)
        return collectReminders(now).map { it.used }
    }

    @Test fun `reminder sets never block and remind every interval`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(reminderSet()))
        assertEquals(Decision.Allow, e.onForegroundChanged(insta, now))
        assertEquals(start.plus(min(15)), e.nextEvaluationAt(now))
        assertEquals(emptyList<Duration>(), e.at(14))
        assertEquals(listOf(min(15)), e.at(15))
        assertEquals(emptyList<Duration>(), e.at(20))
        assertEquals(listOf(min(30)), e.at(30))
    }

    @Test fun `time only counts while on screen, per app by default`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(reminderSet()))
        e.onForegroundChanged(insta, now)
        e.at(10)
        e.onForegroundChanged(null, now) // screen off
        e.at(60)
        e.onForegroundChanged(tiktok, now)
        assertEquals(emptyList<Duration>(), e.at(70)) // TikTok has its own 10 min
        e.onForegroundChanged(insta, now)
        assertEquals(listOf(min(15)), e.at(75)) // Instagram: 10 + 5
    }

    @Test fun `shared scope adds all apps together`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(reminderSet(LimitScope.WHOLE_SET)))
        e.onForegroundChanged(insta, now)
        e.at(10)
        e.onForegroundChanged(tiktok, now)
        val r = run { now = start.plus(min(15)); e.tick(now); e.collectReminders(now) }.single()
        assertEquals(min(15), r.used)
        assertEquals(Target.WholeSet, r.target)
    }

    @Test fun `soft sets can remind during an open, not on the block screen`() {
        val soft = BlockSet("s", "Night Socials", BlockMode.SOFT, setOf(Target.App(insta.packageName)), allWeek,
            SoftLimits(reminderInterval = min(5)))
        val e = BlockEngine(ZoneOffset.UTC, listOf(soft))
        e.onForegroundChanged(insta, now)
        assertEquals(emptyList<Duration>(), e.at(10)) // sitting on the block screen doesn't count
        e.unlock(now)
        assertEquals(listOf(min(5)), e.at(15))
    }

    @Test fun `reminders don't change another set's block`() {
        val hard = BlockSet("h", "Bedtime", BlockMode.HARD, setOf(Target.App(insta.packageName)), allWeek)
        val e = BlockEngine(ZoneOffset.UTC, listOf(reminderSet(), hard))
        assertTrue(e.onForegroundChanged(insta, now) is Decision.Blocked)
        assertEquals(emptyList<Duration>(), e.at(30)) // blocked time isn't usage
    }

    @Test fun `switching to reminder mode is loosening, away from it is not`() {
        val soft = reminderSet().copy(mode = BlockMode.SOFT)
        assertTrue(isLoosening(soft, reminderSet()))
        assertTrue(isLoosening(soft.copy(mode = BlockMode.HARD), soft))
        assertFalse(isLoosening(reminderSet(), soft))
    }
}
