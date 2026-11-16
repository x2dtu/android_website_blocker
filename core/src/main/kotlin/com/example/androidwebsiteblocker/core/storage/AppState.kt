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
import com.example.androidwebsiteblocker.core.protection.Streak
import com.example.androidwebsiteblocker.core.protection.SuspensionState
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Everything the app persists. Maps are keyed by block set id. */
data class AppState(
    val sets: List<BlockSet> = emptyList(),
    val usage: Map<UsageKey, SoftUsage> = emptyMap(),
    val suspensions: Map<String, SuspensionState> = emptyMap(),
    val pendingChanges: Map<String, PendingChange> = emptyMap(),
    val streaks: Map<String, Streak> = emptyMap(),
)

/**
 * JSON encoding of [AppState]. Uses private DTOs so domain classes stay free of serialization
 * concerns and the file format can evolve independently ([CURRENT_VERSION]).
 */
object AppStateCodec {
    const val CURRENT_VERSION = 1

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(state: AppState): String = json.encodeToString(StateDto.serializer(), state.toDto())

    fun decode(text: String): AppState {
        val dto = json.decodeFromString(StateDto.serializer(), text)
        require(dto.version <= CURRENT_VERSION) { "State file version ${dto.version} is newer than this app" }
        return dto.toDomain()
    }
}

// --- DTOs. Durations are millis, instants are epoch millis, dates are ISO strings. ---

@Serializable
private data class StateDto(
    val version: Int = AppStateCodec.CURRENT_VERSION,
    val sets: List<SetDto> = emptyList(),
    val usage: List<UsageDto> = emptyList(),
    val suspensions: List<SuspensionDto> = emptyList(),
    val pendingChanges: List<PendingDto> = emptyList(),
    val streaks: List<StreakDto> = emptyList(),
)

@Serializable
private data class SetDto(
    val id: String,
    val name: String,
    val mode: String,
    val targets: List<TargetDto>,
    val windows: List<WindowDto>,
    val unlockDelayMs: Long,
    val maxOpens: Int?,
    val perOpenLimitMs: Long?,
    val totalLimitMs: Long?,
    val graceMs: Long,
    val suspensionMs: Long,
    val maxSuspensions: Int?,
    val suspensionCooldownMs: Long,
    val changeWaitMs: Long,
    val message: String,
    val colorArgb: Long,
    val pinHash: String?,
    val enabled: Boolean,
    val suspendedUntil: Long?,
)

@Serializable
private data class TargetDto(val type: String, val value: String)

@Serializable
private data class WindowDto(val day: Int, val start: Int, val end: Int)

@Serializable
private data class UsageDto(
    val setId: String,
    val target: TargetDto,
    val occurrence: String,
    val opensUsed: Int,
    val timeUsedMs: Long,
    val sessionUsedMs: Long? = null,
    val sessionLeftAt: Long? = null,
)

@Serializable
private data class SuspensionDto(val setId: String, val occurrence: String, val used: Int, val lastEndsAt: Long?)

@Serializable
private data class PendingDto(val proposed: SetDto, val requestedAt: Long, val appliesAt: Long)

@Serializable
private data class StreakDto(
    val setId: String,
    val days: Int,
    @SerialName("lastCounted") val lastCountedDay: String?,
    @SerialName("broken") val brokenDay: String?,
)

private fun AppState.toDto() = StateDto(
    sets = sets.map { it.toDto() },
    usage = usage.map { (k, u) ->
        UsageDto(k.setId, k.target.toDto(), k.occurrenceKey, u.opensUsed, u.timeUsed.toMillis(),
            u.session?.used?.toMillis(), u.session?.leftAt?.toEpochMilli())
    },
    suspensions = suspensions.map { (id, s) -> SuspensionDto(id, s.occurrenceKey, s.used, s.lastEndsAt?.toEpochMilli()) },
    pendingChanges = pendingChanges.values.map { PendingDto(it.proposed.toDto(), it.requestedAt.toEpochMilli(), it.appliesAt.toEpochMilli()) },
    streaks = streaks.map { (id, s) -> StreakDto(id, s.days, s.lastCountedDay?.toString(), s.brokenDay?.toString()) },
)

private fun StateDto.toDomain() = AppState(
    sets = sets.map { it.toDomain() },
    usage = usage.associate { u ->
        UsageKey(u.setId, u.target.toDomain(), u.occurrence) to SoftUsage(
            opensUsed = u.opensUsed,
            timeUsed = Duration.ofMillis(u.timeUsedMs),
            session = u.sessionUsedMs?.let { OpenSession(Duration.ofMillis(it), u.sessionLeftAt?.let(Instant::ofEpochMilli)) },
        )
    },
    suspensions = suspensions.associate { it.setId to SuspensionState(it.occurrence, it.used, it.lastEndsAt?.let(Instant::ofEpochMilli)) },
    pendingChanges = pendingChanges.associate {
        val set = it.proposed.toDomain()
        set.id to PendingChange(set.id, set, Instant.ofEpochMilli(it.requestedAt), Instant.ofEpochMilli(it.appliesAt))
    },
    streaks = streaks.associate { it.setId to Streak(it.days, it.lastCountedDay?.let(LocalDate::parse), it.brokenDay?.let(LocalDate::parse)) },
)

private fun BlockSet.toDto() = SetDto(
    id = id, name = name, mode = mode.name,
    targets = targets.map { it.toDto() },
    windows = schedule.windows.map { WindowDto(it.day.value, it.startMinute, it.endMinute) },
    unlockDelayMs = softLimits.unlockDelay.toMillis(),
    maxOpens = softLimits.maxOpens,
    perOpenLimitMs = softLimits.perOpenLimit?.toMillis(),
    totalLimitMs = softLimits.totalLimit?.toMillis(),
    graceMs = softLimits.graceTime.toMillis(),
    suspensionMs = protection.suspensionDuration.toMillis(),
    maxSuspensions = protection.maxSuspensions,
    suspensionCooldownMs = protection.suspensionCooldown.toMillis(),
    changeWaitMs = protection.changeWait.toMillis(),
    message = message, colorArgb = colorArgb, pinHash = pinHash, enabled = enabled,
    suspendedUntil = suspendedUntil?.toEpochMilli(),
)

private fun SetDto.toDomain() = BlockSet(
    id = id, name = name, mode = BlockMode.valueOf(mode),
    targets = targets.map { it.toDomain() }.toSet(),
    schedule = WeeklySchedule(windows.map { DayWindow(DayOfWeek.of(it.day), it.start, it.end) }),
    softLimits = SoftLimits(
        unlockDelay = Duration.ofMillis(unlockDelayMs),
        maxOpens = maxOpens,
        perOpenLimit = perOpenLimitMs?.let(Duration::ofMillis),
        totalLimit = totalLimitMs?.let(Duration::ofMillis),
        graceTime = Duration.ofMillis(graceMs),
    ),
    protection = ProtectionSettings(
        suspensionDuration = Duration.ofMillis(suspensionMs),
        maxSuspensions = maxSuspensions,
        suspensionCooldown = Duration.ofMillis(suspensionCooldownMs),
        changeWait = Duration.ofMillis(changeWaitMs),
    ),
    message = message, colorArgb = colorArgb, pinHash = pinHash, enabled = enabled,
    suspendedUntil = suspendedUntil?.let(Instant::ofEpochMilli),
)

private fun Target.toDto() = when (this) {
    is Target.App -> TargetDto("app", packageName)
    is Target.Website -> TargetDto("web", domain)
}

private fun TargetDto.toDomain(): Target = when (type) {
    "app" -> Target.App(value)
    "web" -> Target.Website(value)
    else -> error("Unknown target type: $type")
}
