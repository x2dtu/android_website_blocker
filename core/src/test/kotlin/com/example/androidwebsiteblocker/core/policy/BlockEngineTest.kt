package com.example.androidwebsiteblocker.core.policy

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEngineTest {

    // Monday 2026-11-16, UTC.
    private val zone = ZoneOffset.UTC
    private var now: Instant = Instant.parse("2026-11-16T10:00:00Z")

    private fun advance(d: Duration) { now = now.plus(d) }
    private fun min(n: Long) = Duration.ofMinutes(n)
    private fun sec(n: Long) = Duration.ofSeconds(n)

    private val allMonday = WeeklySchedule(listOf(DayWindow(MONDAY, 0, MINUTES_PER_DAY)))
    private val mondayAndTuesday = WeeklySchedule(listOf(DayWindow(MONDAY, 0, MINUTES_PER_DAY - 60), DayWindow(TUESDAY, 0, MINUTES_PER_DAY)))

    private val appA = Target.App("com.a")
    private val appB = Target.App("com.b")
    private val fgA = Foreground("com.a")
    private val fgB = Foreground("com.b")
    private val home = Foreground("com.launcher")

    private fun soft(
        limits: SoftLimits,
        id: String = "soft",
        targets: Set<Target> = setOf(appA),
        schedule: WeeklySchedule = allMonday,
    ) = BlockSet(id, "Night Socials", BlockMode.SOFT, targets, schedule, limits)

    private fun hard(id: String = "hard", targets: Set<Target> = setOf(appA)) =
        BlockSet(id, "Bedtime", BlockMode.HARD, targets, allMonday)

    private fun engine(vararg sets: BlockSet) = BlockEngine(zone, sets.toList())

    // --- Hard blocks ---

    @Test fun `no sets allows everything`() {
        assertEquals(Decision.Allow, engine().onForegroundChanged(fgA, now))
    }

    @Test fun `hard block inside window`() {
        val d = engine(hard()).onForegroundChanged(fgA, now) as Decision.Blocked
        assertEquals(BlockReason.Scheduled, d.primary.reason)
        assertEquals(LocalDateTime.parse("2026-11-17T00:00"), d.primary.until)
        assertEquals("Bedtime: blocked until 12:00 AM", d.primary.describe())
    }

    @Test fun `hard block outside window allows`() {
        now = Instant.parse("2026-11-17T10:00:00Z")
        assertEquals(Decision.Allow, engine(hard()).onForegroundChanged(fgA, now))
    }

    @Test fun `other apps are not affected`() {
        assertEquals(Decision.Allow, engine(hard()).onForegroundChanged(fgB, now))
    }

    // --- Soft blocks: gate and opens ---

    @Test fun `soft block shows gate with delay and open count`() {
        val e = engine(soft(SoftLimits(unlockDelay = sec(10), maxOpens = 4)))
        val d = e.onForegroundChanged(fgA, now) as Decision.SoftGate
        assertEquals(sec(10), d.unlockDelay)
        assertEquals(OpenCount(0, 4), d.opens)
    }

    @Test fun `soft block without limits still gates then allows unlimited`() {
        val e = engine(soft(SoftLimits()))
        assertTrue(e.onForegroundChanged(fgA, now) is Decision.SoftGate)
        assertEquals(Decision.InSession(null), e.unlock(now))
    }

    @Test fun `per-open limit ends the open and re-gates`() {
        val e = engine(soft(SoftLimits(maxOpens = 4, perOpenLimit = min(3))))
        e.onForegroundChanged(fgA, now)
        assertEquals(Decision.InSession(min(3)), e.unlock(now))
        advance(min(3))
        val d = e.tick(now) as Decision.SoftGate
        assertEquals(OpenCount(1, 4), d.opens)
    }

    @Test fun `each open starts fresh at the per-open limit`() {
        val e = engine(soft(SoftLimits(perOpenLimit = min(3))))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        advance(min(1))
        e.onForegroundChanged(home, now)
        advance(min(5)) // well past grace
        assertTrue(e.onForegroundChanged(fgA, now) is Decision.SoftGate)
        assertEquals(Decision.InSession(min(3)), e.unlock(now))
    }

    @Test fun `opens exhausted becomes a hard block with reason`() {
        val e = engine(soft(SoftLimits(maxOpens = 2, perOpenLimit = min(1))))
        e.onForegroundChanged(fgA, now)
        repeat(2) {
            e.unlock(now)
            advance(min(1))
            e.tick(now)
        }
        val d = e.tick(now) as Decision.Blocked
        assertEquals(BlockReason.OpensExhausted(2), d.primary.reason)
        assertEquals("Night Socials: all 2 opens used", d.primary.describe())
    }

    // --- Grace period ---

    @Test fun `returning within grace continues the open without counting time away`() {
        val e = engine(soft(SoftLimits(maxOpens = 4, perOpenLimit = min(3), graceTime = sec(15))))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        advance(min(1))
        e.onForegroundChanged(home, now)
        advance(sec(10))
        assertEquals(Decision.InSession(min(2)), e.onForegroundChanged(fgA, now))
        assertEquals(1, e.usage.values.single().opensUsed)
    }

    @Test fun `returning after grace needs a new open`() {
        val e = engine(soft(SoftLimits(maxOpens = 4, graceTime = sec(15))))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        e.onForegroundChanged(home, now)
        advance(sec(16))
        val d = e.onForegroundChanged(fgA, now) as Decision.SoftGate
        assertEquals(OpenCount(1, 4), d.opens)
    }

    @Test fun `grace time is configurable`() {
        val e = engine(soft(SoftLimits(graceTime = min(2))))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        e.onForegroundChanged(home, now)
        advance(sec(90))
        assertTrue(e.onForegroundChanged(fgA, now) is Decision.InSession)
    }

    // --- Total time limit ---

    @Test fun `total limit cuts the last open short`() {
        // 3 opens x 5 min, 12 min total.
        val e = engine(soft(SoftLimits(maxOpens = 3, perOpenLimit = min(5), totalLimit = min(12))))
        e.onForegroundChanged(fgA, now)
        repeat(2) {
            e.unlock(now)
            advance(min(5))
            e.tick(now)
        }
        assertEquals(Decision.InSession(min(2)), e.unlock(now))
        advance(min(2))
        val d = e.tick(now) as Decision.Blocked
        assertEquals(BlockReason.TotalTimeExceeded(min(12)), d.primary.reason)
        assertEquals("Night Socials: screen time exceeded 12 min", d.primary.describe())
    }

    @Test fun `time on the block screen and with screen off does not count`() {
        val e = engine(soft(SoftLimits(totalLimit = min(10))))
        e.onForegroundChanged(fgA, now)
        advance(min(30)) // sitting on the gate
        assertEquals(Decision.InSession(min(10)), e.unlock(now))
        advance(min(1))
        e.onForegroundChanged(null, now) // screen off
        advance(min(30))
        e.onForegroundChanged(fgA, now)
        assertEquals(Decision.InSession(min(9)), e.unlock(now))
    }

    // --- Per-target independence and resets ---

    @Test fun `targets in a set are limited independently`() {
        val e = engine(soft(SoftLimits(maxOpens = 1, perOpenLimit = min(1)), targets = setOf(appA, appB)))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        advance(min(1))
        assertTrue(e.tick(now) is Decision.Blocked)
        val d = e.onForegroundChanged(fgB, now) as Decision.SoftGate
        assertEquals(OpenCount(0, 1), d.opens)
    }

    @Test fun `usage resets on the next window occurrence`() {
        val e = engine(soft(SoftLimits(maxOpens = 1, perOpenLimit = min(1)), schedule = mondayAndTuesday))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        advance(min(1))
        assertTrue(e.tick(now) is Decision.Blocked)
        // Monday's window ends at 23:00; Tuesday's starts at 00:00.
        now = Instant.parse("2026-11-16T23:30:00Z")
        assertEquals(Decision.Allow, e.tick(now))
        now = Instant.parse("2026-11-17T00:00:00Z")
        val d = e.tick(now) as Decision.SoftGate
        assertEquals(OpenCount(0, 1), d.opens)
    }

    @Test fun `window ending mid-session allows`() {
        now = Instant.parse("2026-11-16T23:59:00Z")
        val e = engine(soft(SoftLimits(perOpenLimit = min(5))))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        advance(min(1))
        assertEquals(Decision.Allow, e.tick(now))
    }

    // --- Most restrictive wins ---

    @Test fun `hard beats soft and names the set`() {
        val e = engine(soft(SoftLimits()), hard())
        val d = e.onForegroundChanged(fgA, now) as Decision.Blocked
        assertEquals("Bedtime", d.primary.setName)
    }

    @Test fun `exhausted soft set beats another set's open session`() {
        val strict = soft(SoftLimits(totalLimit = min(1)), id = "strict")
        val loose = soft(SoftLimits(), id = "loose")
        val e = engine(strict, loose)
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        advance(min(1))
        val d = e.tick(now) as Decision.Blocked
        assertEquals("strict", d.primary.setId)
    }

    @Test fun `two soft sets both gate, unlock uses one open from each`() {
        val a = soft(SoftLimits(maxOpens = 5, unlockDelay = sec(5)), id = "a")
        val b = soft(SoftLimits(maxOpens = 2, unlockDelay = sec(30)), id = "b")
        val e = engine(a, b)
        val gate = e.onForegroundChanged(fgA, now) as Decision.SoftGate
        assertEquals(sec(30), gate.unlockDelay)
        assertEquals(OpenCount(0, 2), gate.opens) // most constrained
        assertEquals("b", gate.primary.setId)
        e.unlock(now)
        assertEquals(setOf(1), e.usage.values.map { it.opensUsed }.toSet())
        assertEquals(2, e.usage.size)
    }

    @Test fun `usage counts against every matching soft set`() {
        val e = engine(soft(SoftLimits(totalLimit = min(10)), id = "a"), soft(SoftLimits(totalLimit = min(4)), id = "b"))
        e.onForegroundChanged(fgA, now)
        assertEquals(Decision.InSession(min(4)), e.unlock(now))
        advance(min(4))
        assertEquals("b", (e.tick(now) as Decision.Blocked).primary.setId)
        assertEquals(setOf(min(4)), e.usage.values.map { it.timeUsed }.toSet())
    }

    // --- Suspension / disabled ---

    @Test fun `suspended and disabled sets are ignored`() {
        val suspended = hard().copy(suspendedUntil = now.plus(min(1)))
        val e = engine(suspended)
        assertEquals(Decision.Allow, e.onForegroundChanged(fgA, now))
        advance(min(1))
        assertTrue(e.tick(now) is Decision.Blocked)
        assertEquals(Decision.Allow, engine(hard().copy(enabled = false)).onForegroundChanged(fgA, now))
    }

    // --- Websites ---

    @Test fun `website target matches domain and subdomains only`() {
        val e = engine(hard(targets = setOf(Target.Website("reddit.com"))))
        assertTrue(e.onForegroundChanged(Foreground("com.android.chrome", "reddit.com"), now) is Decision.Blocked)
        assertTrue(e.onForegroundChanged(Foreground("com.android.chrome", "old.reddit.com"), now) is Decision.Blocked)
        assertEquals(Decision.Allow, e.onForegroundChanged(Foreground("com.android.chrome", "notreddit.com"), now))
        assertEquals(Decision.Allow, e.onForegroundChanged(Foreground("com.android.chrome", "google.com"), now))
    }

    @Test fun `subdomains share one website's usage`() {
        val e = engine(soft(SoftLimits(maxOpens = 3), targets = setOf(Target.Website("reddit.com"))))
        e.onForegroundChanged(Foreground("com.android.chrome", "www.reddit.com"), now)
        e.unlock(now)
        e.onForegroundChanged(Foreground("com.android.chrome", "old.reddit.com"), now)
        assertTrue(e.tick(now) is Decision.InSession)
        assertEquals(1, e.usage.size)
    }

    // --- Scheduling hints ---

    @Test fun `next evaluation is the earlier of session end and window change`() {
        val e = engine(soft(SoftLimits(perOpenLimit = min(3))))
        e.onForegroundChanged(fgA, now)
        e.unlock(now)
        assertEquals(now.plus(min(3)), e.nextEvaluationAt(now))
        now = Instant.parse("2026-11-16T23:58:00Z")
        e.tick(now)
        e.unlock(now)
        assertEquals(Instant.parse("2026-11-17T00:00:00Z"), e.nextEvaluationAt(now))
    }

    @Test fun `duration formatting`() {
        assertEquals("15 min", formatDuration(min(15)))
        assertEquals("1 h 30 min", formatDuration(min(90)))
        assertEquals("45 s", formatDuration(sec(45)))
        assertEquals("0 s", formatDuration(Duration.ZERO))
    }
}
