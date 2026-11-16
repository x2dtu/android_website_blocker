package com.example.androidwebsiteblocker.core.storage

import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.ProtectionSettings
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.policy.OpenSession
import com.example.androidwebsiteblocker.core.policy.SoftUsage
import com.example.androidwebsiteblocker.core.policy.UsageKey
import com.example.androidwebsiteblocker.core.protection.PendingChange
import com.example.androidwebsiteblocker.core.protection.PinHasher
import com.example.androidwebsiteblocker.core.protection.Streak
import com.example.androidwebsiteblocker.core.protection.SuspensionState
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.MINUTES_PER_DAY
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStateCodecTest {
    private val now = Instant.parse("2026-11-16T10:00:00Z")

    private val soft = BlockSet(
        id = "s1", name = "Night Socials", mode = BlockMode.SOFT,
        targets = setOf(Target.App("com.instagram.android"), Target.Website("reddit.com")),
        schedule = WeeklySchedule(listOf(DayWindow(DayOfWeek.SUNDAY, 0, 480), DayWindow(DayOfWeek.SATURDAY, 1380, MINUTES_PER_DAY))),
        softLimits = SoftLimits(Duration.ofSeconds(10), 4, Duration.ofMinutes(3), Duration.ofMinutes(12), Duration.ofSeconds(20)),
        protection = ProtectionSettings(Duration.ofMinutes(2), null, Duration.ofSeconds(45), Duration.ofMinutes(7)),
        message = "Sleep is a superpower",
        colorArgb = 0xFF00897B,
        pinHash = PinHasher.hash("1234"),
        suspendedUntil = now.plusSeconds(60),
    )
    private val hard = BlockSet("h1", "Bedtime", BlockMode.HARD, setOf(Target.App("com.a")), WeeklySchedule(emptyList()), enabled = false)

    private val state = AppState(
        sets = listOf(soft, hard),
        usage = mapOf(
            UsageKey("s1", Target.Website("reddit.com"), "2026-11-14T23:00") to
                SoftUsage(2, Duration.ofMinutes(5), OpenSession(Duration.ofSeconds(30), now)),
            UsageKey("s1", Target.App("com.instagram.android"), "2026-11-14T23:00") to SoftUsage(1, Duration.ofMinutes(1)),
        ),
        suspensions = mapOf("s1" to SuspensionState("2026-11-14T23:00", 1, now)),
        pendingChanges = mapOf("s1" to PendingChange("s1", soft.copy(enabled = false), now, now.plusSeconds(300))),
        streaks = mapOf("s1" to Streak(5, LocalDate.parse("2026-11-15"), LocalDate.parse("2026-11-01"))),
        activeOccurrences = mapOf("s1" to "2026-11-14T23:00"),
    )

    @Test fun `round trips all state`() {
        val decoded = AppStateCodec.decode(AppStateCodec.encode(state))
        assertEquals(state, decoded)
        assertTrue(PinHasher.verify("1234", decoded.sets.first().pinHash!!))
    }

    @Test fun `empty state round trips`() {
        assertEquals(AppState(), AppStateCodec.decode(AppStateCodec.encode(AppState())))
    }

    @Test fun `ignores unknown fields from newer minor changes`() {
        val text = AppStateCodec.encode(AppState()).replaceFirst("{", "{\"futureField\":true,")
        assertEquals(AppState(), AppStateCodec.decode(text))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects newer file versions`() {
        AppStateCodec.decode("{\"version\":99}")
    }
}
