package com.example.androidwebsiteblocker.core.protection

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.ProtectionSettings
import com.example.androidwebsiteblocker.core.model.PuzzleDifficulty
import com.example.androidwebsiteblocker.core.model.PuzzleSettings
import com.example.androidwebsiteblocker.core.model.PuzzleType
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.TUESDAY
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test fun `hash verifies only the right pin`() {
        val stored = PinHasher.hash("4821")
        assertTrue(PinHasher.verify("4821", stored))
        assertFalse(PinHasher.verify("4822", stored))
        assertFalse(stored.contains("4821"))
    }

    @Test fun `same pin hashes differently each time`() {
        assertNotEquals(PinHasher.hash("1234"), PinHasher.hash("1234"))
    }

    @Test fun `pin validation`() {
        assertTrue(PinHasher.isValidPin("0000"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("12a4"))
        assertFalse(PinHasher.verify("1234", "garbage"))
    }
}

class SuspensionPolicyTest {
    private val zone = ZoneOffset.UTC
    private var now = Instant.parse("2026-11-16T10:00:00Z") // Monday
    private val set = BlockSet(
        "s", "Bedtime", BlockMode.HARD, setOf(Target.App("com.a")),
        WeeklySchedule(listOf(DayWindow(MONDAY, 0, MINUTES_PER_DAY - 60), DayWindow(TUESDAY, 0, MINUTES_PER_DAY - 60))),
        protection = ProtectionSettings(suspensionDuration = Duration.ofMinutes(1), maxSuspensions = 2, suspensionCooldown = Duration.ofSeconds(30)),
    )

    @Test fun `suspends for the configured duration`() {
        val (suspended, state) = SuspensionPolicy.suspend(set, null, now, zone)
        assertEquals(now.plusSeconds(60), suspended.suspendedUntil)
        assertEquals(1, state.used)
        assertEquals(SuspensionCheck.AlreadySuspended(now.plusSeconds(60)), SuspensionPolicy.check(suspended, state, now, zone))
    }

    @Test fun `cooldown after a suspension ends`() {
        val (suspended, state) = SuspensionPolicy.suspend(set, null, now, zone)
        now = now.plusSeconds(70) // suspension ended 10 s ago
        val endsAt = state.lastEndsAt!!
        assertEquals(SuspensionCheck.CoolingDown(endsAt.plusSeconds(30)), SuspensionPolicy.check(suspended, state, now, zone))
        now = endsAt.plusSeconds(30)
        assertEquals(SuspensionCheck.Allowed(0), SuspensionPolicy.check(suspended, state, now, zone))
    }

    @Test fun `limit per occurrence, fresh allowance next occurrence`() {
        var s = set
        var state: SuspensionState? = null
        repeat(2) {
            val r = SuspensionPolicy.suspend(s, state, now, zone)
            s = r.first; state = r.second
            now = now.plusSeconds(120)
        }
        assertEquals(SuspensionCheck.LimitReached(2), SuspensionPolicy.check(s, state, now, zone))
        now = Instant.parse("2026-11-17T01:00:00Z") // Tuesday's occurrence (Monday's ended at 23:00)
        assertEquals(SuspensionCheck.Allowed(1), SuspensionPolicy.check(s, state, now, zone))
    }

    @Test fun `nothing to suspend outside a window`() {
        now = Instant.parse("2026-11-18T10:00:00Z") // Wednesday
        assertEquals(SuspensionCheck.NotInWindow, SuspensionPolicy.check(set, null, now, zone))
    }
}

class ChangeReviewTest {
    private val now = Instant.parse("2026-11-16T10:00:00Z")
    private val night = WeeklySchedule(listOf(DayWindow(MONDAY, 22 * 60, MINUTES_PER_DAY)))
    private val longerNight = WeeklySchedule(listOf(DayWindow(MONDAY, 21 * 60, MINUTES_PER_DAY)))
    private val soft = BlockSet(
        "s", "Night Socials", BlockMode.SOFT, setOf(Target.App("com.a")), night,
        softLimits = SoftLimits(unlockDelay = Duration.ofSeconds(10), maxOpens = 4, perOpenLimit = Duration.ofMinutes(3), totalLimit = Duration.ofMinutes(15)),
    )

    private fun loosens(change: (BlockSet) -> BlockSet) = isLoosening(soft, change(soft))

    @Test fun `loosening edits`() {
        assertTrue(loosens { it.copy(enabled = false) })
        assertTrue(loosens { it.copy(targets = emptySet()) })
        assertTrue(loosens { it.copy(schedule = WeeklySchedule(listOf(DayWindow(MONDAY, 23 * 60, MINUTES_PER_DAY)))) })
        assertTrue(loosens { it.copy(softLimits = it.softLimits.copy(maxOpens = 5)) })
        assertTrue(loosens { it.copy(softLimits = it.softLimits.copy(maxOpens = null)) })
        assertTrue(loosens { it.copy(softLimits = it.softLimits.copy(totalLimit = Duration.ofMinutes(20))) })
        assertTrue(loosens { it.copy(softLimits = it.softLimits.copy(unlockDelay = Duration.ZERO)) })
        assertTrue(loosens { it.copy(softLimits = it.softLimits.copy(graceTime = Duration.ofMinutes(5))) })
        assertTrue(loosens { it.copy(protection = it.protection.copy(changeWait = Duration.ZERO)) })
        assertTrue(loosens { it.copy(protection = it.protection.copy(maxSuspensions = 10)) })
        assertTrue(isLoosening(soft.copy(mode = BlockMode.HARD), soft))
    }

    @Test fun `tightening and cosmetic edits`() {
        assertFalse(loosens { it.copy(name = "Renamed", message = "Sleep!", colorArgb = 0xFF000000) })
        assertFalse(loosens { it.copy(targets = it.targets + Target.App("com.b")) })
        assertFalse(loosens { it.copy(schedule = longerNight) })
        assertFalse(loosens { it.copy(softLimits = it.softLimits.copy(maxOpens = 2, totalLimit = Duration.ofMinutes(10))) })
        assertFalse(loosens { it.copy(mode = BlockMode.HARD) })
        assertFalse(isLoosening(soft.copy(enabled = false), soft))
    }

    @Test fun `puzzle changes`() {
        val on = soft.copy(softLimits = soft.softLimits.copy(puzzle = PuzzleSettings(enabled = true, harderEachOpen = true)))
        fun loosensPuzzle(change: (PuzzleSettings) -> PuzzleSettings) =
            isLoosening(on, on.copy(softLimits = on.softLimits.copy(puzzle = change(on.softLimits.puzzle))))
        assertTrue(loosensPuzzle { it.copy(enabled = false) })
        assertTrue(loosensPuzzle { it.copy(types = setOf(PuzzleType.MATH)) })
        assertTrue(loosensPuzzle { it.copy(difficulty = PuzzleDifficulty.EASY) })
        assertTrue(loosensPuzzle { it.copy(harderEachOpen = false) })
        assertTrue(loosensPuzzle { it.copy(wrongAnswerLockout = Duration.ZERO) })
        assertFalse(loosensPuzzle { it.copy(difficulty = PuzzleDifficulty.HARD, wrongAnswerLockout = Duration.ofSeconds(30)) })
        assertFalse(isLoosening(soft, on)) // turning it on
        // Settings of a puzzle that's off don't matter.
        val off = soft.softLimits.puzzle.copy(difficulty = PuzzleDifficulty.HARD)
        assertFalse(isLoosening(soft.copy(softLimits = soft.softLimits.copy(puzzle = off)), soft))
    }

    @Test fun `mixed edit counts as loosening`() {
        assertTrue(loosens { it.copy(targets = it.targets + Target.App("com.b"), softLimits = it.softLimits.copy(maxOpens = 8)) })
    }

    @Test fun `loosening waits for the current change wait`() {
        val proposed = soft.copy(enabled = false, protection = soft.protection.copy(changeWait = Duration.ZERO))
        val outcome = reviewChange(soft, proposed, now) as ChangeOutcome.Pending
        assertEquals(now.plus(Duration.ofMinutes(5)), outcome.change.appliesAt)
        assertFalse(outcome.change.isDue(now.plus(Duration.ofMinutes(4))))
        assertTrue(outcome.change.isDue(now.plus(Duration.ofMinutes(5))))
    }

    @Test fun `tightening applies now and keeps active suspension`() {
        val current = soft.copy(suspendedUntil = now.plusSeconds(30))
        val outcome = reviewChange(current, current.copy(suspendedUntil = null, schedule = longerNight), now) as ChangeOutcome.ApplyNow
        assertEquals(now.plusSeconds(30), outcome.set.suspendedUntil)
    }

    @Test fun `schedule equality ignores how windows were entered`() {
        val split = WeeklySchedule(listOf(DayWindow(MONDAY, 22 * 60, 23 * 60), DayWindow(MONDAY, 23 * 60, MINUTES_PER_DAY)))
        assertEquals(night, split)
    }
}

class StreakTest {
    private val mon = LocalDate.parse("2026-11-16")

    @Test fun `counts once per day with a completed window`() {
        val s = Streak().occurrenceCompleted(mon).occurrenceCompleted(mon).occurrenceCompleted(mon.plusDays(1))
        assertEquals(2, s.days)
    }

    @Test fun `days without windows do not break it`() {
        val s = Streak().occurrenceCompleted(mon).occurrenceCompleted(mon.plusDays(3))
        assertEquals(2, s.days)
    }

    @Test fun `break resets and the broken day cannot count`() {
        val s = Streak().occurrenceCompleted(mon).broken(mon.plusDays(1)).occurrenceCompleted(mon.plusDays(1))
        assertEquals(0, s.days)
        assertEquals(1, s.occurrenceCompleted(mon.plusDays(2)).days)
    }
}
