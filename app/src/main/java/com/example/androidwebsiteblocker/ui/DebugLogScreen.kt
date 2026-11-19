package com.example.androidwebsiteblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.ForegroundLogEntry
import com.example.androidwebsiteblocker.ForegroundMonitor
import com.example.androidwebsiteblocker.core.model.Foreground
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Live log of what the service thinks is on screen, for checking tracking accuracy. */
@Composable
fun DebugLogScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val current by ForegroundMonitor.current.collectAsState()
    val log by ForegroundMonitor.log.collectAsState()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { ScreenHeader("Detection log", onBack = onBack) }
        item {
            Text("On screen now: ${current.label()}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Switch apps and sites, pull down notifications, lock the screen — then check this matches what you did. " +
                    "Kept in memory only (clears when the app restarts).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(log.withIndex().toList()) { (i, entry) ->
            // Newest first, so the previous list item is when this entry ended.
            LogRow(entry, log.getOrNull(i - 1)?.atMillis)
            HorizontalDivider()
        }
    }
}

private val TIME = SimpleDateFormat("HH:mm:ss", Locale.US)

@Composable
private fun LogRow(entry: ForegroundLogEntry, endedAtMillis: Long?) {
    val duration = endedAtMillis?.let { formatMillis(it - entry.atMillis) } ?: "now"
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(TIME.format(Date(entry.atMillis)), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
        Text(entry.foreground.label(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
        Text(duration, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
    }
}

private fun Foreground?.label(): String = when {
    this == null -> "Screen off / locked"
    host != null -> "$packageName · $host"
    else -> packageName
}

private fun formatMillis(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}
