package com.example.androidwebsiteblocker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.storage.AppState
import com.example.androidwebsiteblocker.core.storage.streakOf
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

/** Current time, refreshed every [periodMs], so status lines stay current. */
@Composable
fun rememberNow(periodMs: Long = 15_000): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(periodMs)
            now = Instant.now()
        }
    }
    return now
}

private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val DAY_TIME = DateTimeFormatter.ofPattern("EEE h:mm a", Locale.US)
private val TIME_SECONDS = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)

fun formatInstant(instant: Instant, zone: ZoneId): String = LocalDateTime.ofInstant(instant, zone).format(TIME)

fun formatInstantSeconds(instant: Instant, zone: ZoneId): String = LocalDateTime.ofInstant(instant, zone).format(TIME_SECONDS)

enum class SetState { ACTIVE, PAUSED, UPCOMING, DISABLED }

data class SetStatus(val state: SetState, val text: String, val pending: String?)

fun statusOf(set: BlockSet, state: AppState, now: Instant, zone: ZoneId): SetStatus {
    val pending = state.pendingChanges[set.id]?.let {
        val what = if (!it.proposed.enabled) "Turns off" else "Changes apply"
        "$what at ${formatInstant(it.appliesAt, zone)}"
    }
    val local = LocalDateTime.ofInstant(now, zone)
    return when {
        !set.enabled -> SetStatus(SetState.DISABLED, "Not blocking anything", pending)
        set.isSuspended(now) -> SetStatus(SetState.PAUSED, "Until ${formatInstantSeconds(set.suspendedUntil!!, zone)}", pending)
        else -> set.schedule.occurrenceAt(local)?.let { SetStatus(SetState.ACTIVE, "Until ${it.end.format(timeFormatFor(local, it.end))}", pending) }
            ?: SetStatus(
                SetState.UPCOMING,
                set.schedule.nextChange(local)?.let { "Starts ${it.format(timeFormatFor(local, it))}" } ?: "No schedule",
                pending,
            )
    }
}

/** Time only if it's today, otherwise include the weekday. */
private fun timeFormatFor(now: LocalDateTime, t: LocalDateTime) = if (now.toLocalDate() == t.toLocalDate()) TIME else DAY_TIME

@Composable
fun SetState.color(): Color = when (this) {
    SetState.ACTIVE -> MaterialTheme.colorScheme.secondary
    SetState.PAUSED -> MaterialTheme.colorScheme.tertiary
    SetState.UPCOMING -> MaterialTheme.colorScheme.primary
    SetState.DISABLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun BlockMode.shortLabel() = when (this) {
    BlockMode.HARD -> "Hard"
    BlockMode.SOFT -> "Soft"
    BlockMode.REMINDER -> "Reminder"
}

fun SetState.label(mode: BlockMode = BlockMode.HARD) = when (this) {
    SetState.ACTIVE -> if (mode == BlockMode.REMINDER) "Tracking now" else "Blocking now"
    SetState.PAUSED -> "Paused"
    SetState.UPCOMING -> "Waiting"
    SetState.DISABLED -> "Turned off"
}

@Composable
fun HomeScreen(
    state: AppState,
    serviceOk: Boolean,
    statusCard: @Composable () -> Unit,
    setupCard: (@Composable () -> Unit)?,
    onOpenSet: (String) -> Unit,
    onCreate: () -> Unit,
    onOpenLog: () -> Unit,
) {
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    val statuses = state.sets.associate { it.id to statusOf(it, state, now, zone) }
    val activeCount = statuses.values.count { it.state == SetState.ACTIVE }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ScreenHeader(
                "Night Blocker",
                onBack = null,
                subtitle = when {
                    state.sets.isEmpty() -> "Block apps and sites when you need to rest."
                    activeCount == 0 -> "Nothing is blocked right now"
                    activeCount == 1 -> "1 block set is on right now"
                    else -> "$activeCount block sets are on right now"
                },
            )
        }
        if (!serviceOk) item { statusCard() }
        if (setupCard != null) item { setupCard() }
        if (state.sets.isEmpty()) {
            item {
                SectionCard("Get started") {
                    Hint(
                        "A block set is a group of apps and websites plus a schedule. Start with bedtime — " +
                            "say, social apps from 11:30 PM to 8 AM — then add more for other parts of your day.",
                    )
                }
            }
        }
        items(state.sets, key = { it.id }) { set ->
            SetCard(set, state.streakOf(set.id).days, statuses.getValue(set.id)) { onOpenSet(set.id) }
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("+ New block set") }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (serviceOk) {
                    ColorDot(MaterialTheme.colorScheme.secondary, 8.dp)
                    Hint("Blocking service running", Modifier.padding(start = 8.dp).weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onOpenLog) { Text("Detection log") }
            }
        }
    }
}

@Composable
private fun SetCard(set: BlockSet, streak: Int, status: SetStatus, onClick: () -> Unit) {
    val accent = set.colorArgb.asColor()
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorDot(accent, 14.dp)
                Text(set.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Pill(set.mode.shortLabel(), accent)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(status.state.label(set.mode), status.state.color())
                Text(status.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status.pending?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                set.targets.take(MAX_TEASER).forEach { TargetIcon(it, 30.dp) }
                if (set.targets.size > MAX_TEASER) Hint("+${set.targets.size - MAX_TEASER}")
                Box(Modifier.weight(1f))
                if (streak > 0 && set.mode != BlockMode.REMINDER) Text("🔥 $streak-day streak", style = MaterialTheme.typography.labelLarge, color = accent)
            }
        }
    }
}

private const val MAX_TEASER = 7
