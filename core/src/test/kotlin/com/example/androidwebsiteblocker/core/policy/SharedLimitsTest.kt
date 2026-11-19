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

class SharedLimitsTest {
    private var now = Instant.parse("2026-11-16T10:00:00Z")
    private val allWeek = WeeklySchedule(DayOfWeek.entries.map { DayWindow(it, 0, MINUTES_PER_DAY) })
    private val insta = Foreground("com.instagram.android")
    private val tiktok = Foreground("com.zhiliaoapp.musically")
    private val reddit = Foreground("com.android.chrome", "www.reddit.com")
    private fun min(n: Long) = Duration.ofMinutes(n)

    private fun social(scope: LimitScope, limits: SoftLimits) = BlockSet(
        "social", "Social media", BlockMode.SOFT,
        setOf(Target.App(insta.packageName), Target.App(tiktok.packageName), Target.Website("reddit.com")),
        allWeek, limits, limitScope = scope,
    )

    @Test fun `shared opens are used across all apps in the set`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(social(LimitScope.WHOLE_SET, SoftLimits(maxOpens = 2, perOpenLimit = min(5)))))
        e.onForegroundChanged(insta, now)
        e.unlock(now)
        now = now.plus(min(5))
        e.tick(now)
        val gate = e.onForegroundChanged(tiktok, now) as Decision.SoftGate
        assertEquals(OpenCount(1, 2), gate.opens) // Instagram's open counted for TikTok too
        assertEquals(Target.WholeSet, gate.primary.target)
    }

    @Test fun `shared total time adds up across apps and sites`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(social(LimitScope.WHOLE_SET, SoftLimits(totalLimit = min(20)))))
        e.onForegroundChanged(insta, now)
        e.unlock(now)
        now = now.plus(min(12))
        e.onForegroundChanged(reddit, now) // switching within the set keeps the same open
        assertEquals(Decision.InSession(min(8)), e.tick(now))
        now = now.plus(min(8))
        val d = e.tick(now) as Decision.Blocked
        assertEquals(BlockReason.TotalTimeExceeded(min(20)), d.primary.reason)
        assertTrue(e.onForegroundChanged(tiktok, now) is Decision.Blocked)
        assertEquals(1, e.usage.size)
    }

    @Test fun `each-target scope keeps separate counts (default)`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(social(LimitScope.EACH_TARGET, SoftLimits(maxOpens = 2))))
        e.onForegroundChanged(insta, now)
        e.unlock(now)
        val gate = e.onForegroundChanged(tiktok, now) as Decision.SoftGate
        assertEquals(OpenCount(0, 2), gate.opens)
    }

    @Test fun `leaving the set entirely ends the shared open after grace`() {
        val e = BlockEngine(ZoneOffset.UTC, listOf(social(LimitScope.WHOLE_SET, SoftLimits(maxOpens = 3))))
        e.onForegroundChanged(insta, now)
        e.unlock(now)
        e.onForegroundChanged(Foreground("com.launcher"), now)
        now = now.plusSeconds(60)
        val gate = e.onForegroundChanged(tiktok, now) as Decision.SoftGate
        assertEquals(OpenCount(1, 3), gate.opens)
    }

    @Test fun `shared to per-app is loosening, per-app to shared is not`() {
        val shared = social(LimitScope.WHOLE_SET, SoftLimits(maxOpens = 2))
        val each = shared.copy(limitScope = LimitScope.EACH_TARGET)
        assertTrue(isLoosening(shared, each))
        assertFalse(isLoosening(each, shared))
    }
}
