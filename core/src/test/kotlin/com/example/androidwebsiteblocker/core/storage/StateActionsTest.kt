package com.example.androidwebsiteblocker.core.storage

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.protection.ChangeOutcome
import com.example.androidwebsiteblocker.core.protection.PinHasher
import com.example.androidwebsiteblocker.core.protection.Streak
import com.example.androidwebsiteblocker.core.protection.SuspensionCheck
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateActionsTest {
    private val zone = ZoneOffset.UTC
    private val mon = Instant.parse("2026-11-16T22:30:00Z") // Monday, inside 22:00–24:00
    private val pin = PinHasher.hash("1234")
    // Every night 22:00–24:00.
    private val nightly = WeeklySchedule(DayOfWeek.entries.map { DayWindow(it, 22 * 60, MINUTES_PER_DAY) })
    private val set = BlockSet("s", "Bedtime", BlockMode.HARD, setOf(Target.App("com.a")), nightly, pinHash = pin)
    private val base = StateActions.addSet(AppState(), set)

    @Test fun `move reorders sets and clamps the index`() {
        val three = listOf("b", "c").fold(base) { st, id -> StateActions.addSet(st, set.copy(id = id)) }
        assertEquals(listOf("b", "s", "c"), StateActions.moveSet(three, "s", 1).sets.map { it.id })
        assertEquals(listOf("b", "c", "s"), StateActions.moveSet(three, "s", 99).sets.map { it.id })
        assertEquals(listOf("c", "s", "b"), StateActions.moveSet(three, "c", -1).sets.map { it.id })
        assertEquals(three, StateActions.moveSet(three, "missing", 0))
    }

    @Test fun `tightening edit applies now and cancels pending`() {
        val (pending, _) = StateActions.requestEdit(base, set.copy(enabled = false), mon, zone)
        assertTrue(pending.pendingChanges.containsKey("s"))
        val (s, outcome) = StateActions.requestEdit(pending, set.copy(targets = set.targets + Target.App("com.b")), mon, zone)
        assertTrue(outcome is ChangeOutcome.ApplyNow)
        assertEquals(2, s.setById("s")!!.targets.size)
        assertTrue(s.pendingChanges.isEmpty())
    }

    @Test fun `disable waits, then applies in maintenance and breaks streak`() {
        val withStreak = base.copy(streaks = mapOf("s" to Streak(4)))
        val (pending, outcome) = StateActions.requestEdit(withStreak, set.copy(enabled = false), mon, zone)
        val appliesAt = (outcome as ChangeOutcome.Pending).change.appliesAt
        assertEquals(appliesAt, StateActions.nextPendingAt(pending))
        assertTrue(StateActions.maintain(pending, appliesAt.minusSeconds(1), zone).setById("s")!!.enabled)
        val applied = StateActions.maintain(pending, appliesAt, zone)
        assertFalse(applied.setById("s")!!.enabled)
        assertEquals(0, applied.streakOf("s").days)
        assertTrue(applied.pendingChanges.isEmpty())
    }

    @Test fun `suspension breaks streak and is limited`() {
        var s = base.copy(streaks = mapOf("s" to Streak(4)))
        val (suspended, check) = StateActions.suspend(s, "s", mon, zone)
        assertTrue(check is SuspensionCheck.Allowed)
        assertEquals(0, suspended.streakOf("s").days)
        assertEquals(mon.plusSeconds(60), suspended.setById("s")!!.suspendedUntil)
        s = suspended
        val (_, again) = StateActions.suspend(s, "s", mon.plusSeconds(10), zone)
        assertTrue(again is SuspensionCheck.AlreadySuspended)
    }

    @Test fun `streak counts completed nights`() {
        var s = StateActions.maintain(base, mon, zone)
        assertEquals("2026-11-16T22:00", s.activeOccurrences["s"])
        s = StateActions.maintain(s, Instant.parse("2026-11-17T00:00:00Z"), zone) // Monday's window ended
        assertEquals(1, s.streakOf("s").days)
        assertNull(s.activeOccurrences["s"])
        s = StateActions.maintain(s, Instant.parse("2026-11-17T23:00:00Z"), zone)
        s = StateActions.maintain(s, Instant.parse("2026-11-18T01:00:00Z"), zone)
        assertEquals(2, s.streakOf("s").days)
        assertEquals(LocalDate.parse("2026-11-17"), s.streakOf("s").lastCountedDay)
    }

    @Test fun `suspended night does not count`() {
        var s = StateActions.maintain(base, mon, zone)
        s = StateActions.suspend(s, "s", mon, zone).first
        s = StateActions.maintain(s, Instant.parse("2026-11-17T00:00:00Z"), zone)
        assertEquals(0, s.streakOf("s").days)
    }

    @Test fun `delete requires disabled and clears everything`() {
        try {
            StateActions.deleteSet(base, "s")
            throw AssertionError("expected failure")
        } catch (_: IllegalArgumentException) {
        }
        val disabled = base.copy(sets = listOf(set.copy(enabled = false)), streaks = mapOf("s" to Streak(2)))
        assertEquals(AppState(), StateActions.deleteSet(disabled, "s"))
    }

    @Test fun `a PIN is optional`() {
        val s = StateActions.addSet(AppState(), set.copy(id = "x", pinHash = null))
        assertNull(s.setById("x")!!.pinHash)
    }

    @Test fun `removing a PIN waits like other loosening edits`() {
        val (s, outcome) = StateActions.requestEdit(base, set.copy(pinHash = null), mon, zone)
        assertTrue(outcome is ChangeOutcome.Pending)
        assertEquals(pin, s.setById("s")!!.pinHash)
    }
}
