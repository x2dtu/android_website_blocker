package com.example.androidwebsiteblocker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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

fun formatInstant(instant: Instant, zone: ZoneId): String = LocalDateTime.ofInstant(instant, zone).format(TIME)

private val TIME_SECONDS = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)

fun formatInstantSeconds(instant: Instant, zone: ZoneId): String = LocalDateTime.ofInstant(instant, zone).format(TIME_SECONDS)

fun statusLine(set: BlockSet, state: AppState, now: Instant, zone: ZoneId): String {
    val pending = state.pendingChanges[set.id]?.let { " · change applies at ${formatInstant(it.appliesAt, zone)}" } ?: ""
    val base = when {
        !set.enabled -> "Disabled"
        set.isSuspended(now) -> "Suspended until ${formatInstant(set.suspendedUntil!!, zone)}"
        else -> {
            val local = LocalDateTime.ofInstant(now, zone)
            val occurrence = set.schedule.occurrenceAt(local)
            when {
                occurrence != null -> "Active until ${occurrence.end.format(DAY_TIME)}"
                else -> set.schedule.nextChange(local)?.let { "Next: ${it.format(DAY_TIME)}" } ?: "No time windows"
            }
        }
    }
    return base + pending
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
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ScreenHeader("Night Blocker", onBack = null) }
        if (!serviceOk) item { statusCard() }
        if (setupCard != null) item { setupCard() }
        if (state.sets.isEmpty()) {
            item { Text("No block sets yet. Create one to start blocking apps and websites at set times.") }
        }
        items(state.sets, key = { it.id }) { set ->
            SetCard(set, state.streakOf(set.id).days, statusLine(set, state, now, zone)) { onOpenSet(set.id) }
        }
        item {
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text("New block set") }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (serviceOk) "Blocking service is running" else "", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenLog) { Text("Detection log") }
            }
        }
    }
}

@Composable
private fun SetCard(set: BlockSet, streak: Int, status: String, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(8.dp).fillMaxHeight().background(set.colorArgb.asColor()))
            Column(Modifier.padding(12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(set.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(if (set.mode == BlockMode.HARD) "Hard" else "Soft", style = MaterialTheme.typography.labelMedium)
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val shown = set.targets.take(MAX_TEASER)
                    shown.forEach { TargetIcon(it, 28.dp) }
                    if (set.targets.size > MAX_TEASER) Text("+${set.targets.size - MAX_TEASER}", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.weight(1f))
                    if (streak > 0) Text("$streak-day streak", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private const val MAX_TEASER = 7
