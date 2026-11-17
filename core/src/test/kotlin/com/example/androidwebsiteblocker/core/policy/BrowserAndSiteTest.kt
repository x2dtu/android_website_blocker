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
import org.junit.Assert.assertTrue
import org.junit.Test

/** The user's real setup: Chrome soft-blocked (4 × 4 min) and reddit.com soft-blocked (2 × 3 min). */
class BrowserAndSiteTest {
    private var now = Instant.parse("2026-11-16T22:00:00Z")
    private val allWeek = WeeklySchedule(DayOfWeek.entries.map { DayWindow(it, 0, MINUTES_PER_DAY) })
    private val chromeSet = BlockSet("chrome", "Browser", BlockMode.SOFT, setOf(Target.App("com.android.chrome")), allWeek,
        SoftLimits(maxOpens = 4, perOpenLimit = Duration.ofMinutes(4)))
    private val redditSet = BlockSet("reddit", "Reddit", BlockMode.SOFT, setOf(Target.Website("reddit.com")), allWeek,
        SoftLimits(maxOpens = 2, perOpenLimit = Duration.ofMinutes(3)))
    private val engine = BlockEngine(ZoneOffset.UTC, listOf(chromeSet, redditSet))
    private val google = Foreground("com.android.chrome", "www.google.com")
    private val reddit = Foreground("com.android.chrome", "www.reddit.com")

    private fun opens(setId: String) = engine.usage.filterKeys { it.setId == setId }.values.sumOf { it.opensUsed }
    private fun advance(min: Long) { now = now.plus(Duration.ofMinutes(min)) }

    @Test fun `going to reddit during a chrome open only uses a reddit open`() {
        engine.onForegroundChanged(google, now)
        engine.unlock(now)
        advance(1)
        val gate = engine.onForegroundChanged(reddit, now) as Decision.SoftGate
        assertEquals(listOf("reddit"), gate.gates.map { it.setId })
        engine.unlock(now)
        assertEquals(1, opens("chrome"))
        assertEquals(1, opens("reddit"))
    }

    @Test fun `time on reddit also counts against the chrome open`() {
        engine.onForegroundChanged(google, now)
        engine.unlock(now) // chrome open 1
        advance(1)
        engine.onForegroundChanged(reddit, now)
        engine.unlock(now) // reddit open 1
        advance(3)
        // Reddit's 3 min are up — and Chrome's open has now been used for 4 min, so it ends too.
        val gate = engine.tick(now) as Decision.SoftGate
        assertEquals(setOf("chrome", "reddit"), gate.gates.map { it.setId }.toSet())
        engine.unlock(now)
        assertEquals(2, opens("chrome")) // Continue used a Chrome open as well
        assertEquals(2, opens("reddit"))
    }

    @Test fun `opening reddit directly with no chrome open uses one of each`() {
        val gate = engine.onForegroundChanged(reddit, now) as Decision.SoftGate
        assertEquals(2, gate.gates.size)
        engine.unlock(now)
        assertEquals(1, opens("chrome"))
        assertEquals(1, opens("reddit"))
        assertTrue(engine.tick(now) is Decision.InSession)
    }

    @Test fun `open history records start and on-screen time`() {
        val start = now
        engine.onForegroundChanged(google, now)
        engine.unlock(now)
        advance(2)
        engine.onForegroundChanged(null, now) // screen off: not counted
        advance(10)
        engine.onForegroundChanged(google, now) // past grace → new open needed
        engine.unlock(now)
        advance(1)
        engine.tick(now)
        val history = engine.usage.values.single().opens
        assertEquals(listOf(OpenRecord(start, Duration.ofMinutes(2)), OpenRecord(start.plus(Duration.ofMinutes(12)), Duration.ofMinutes(1))), history)
    }
}
