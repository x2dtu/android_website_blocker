package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.ui.theme.AndroidWebsiteBlockerTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    /** Re-checked on every resume, since the user enables the service in Settings and comes back. */
    private val serviceEnabled = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AndroidWebsiteBlockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(
                        serviceEnabled = serviceEnabled.value,
                        onOpenAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onOpenAppInfo = {
                            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isServiceEnabled(this)
    }
}

fun isServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(AccessibilityManager::class.java)
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        val info = it.resolveInfo.serviceInfo
        info.packageName == context.packageName && info.name == NightBlockerService::class.java.name
    }
}

@Composable
private fun HomeScreen(
    serviceEnabled: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected by ForegroundMonitor.serviceConnected.collectAsState()
    val current by ForegroundMonitor.current.collectAsState()
    val log by ForegroundMonitor.log.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Night Blocker", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp)) }
        item { ServiceStatusCard(serviceEnabled, connected, onOpenAccessibility, onOpenAppInfo) }
        item {
            Text("Live detection", style = MaterialTheme.typography.titleMedium)
            Text("On screen now: ${current.label()}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Switch between apps and sites, pull down notifications, lock the screen — then check this log matches what you did.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        items(log.withIndex().toList()) { (i, entry) ->
            // Log is newest first, so the entry before this one in the list is when it ended.
            val endedAt = log.getOrNull(i - 1)?.atMillis
            LogRow(entry, endedAt)
            HorizontalDivider()
        }
    }
}

@Composable
private fun ServiceStatusCard(
    enabled: Boolean,
    connected: Boolean,
    onOpenAccessibility: () -> Unit,
    onOpenAppInfo: () -> Unit,
) {
    val ok = enabled && connected
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    ok -> "Blocking service is running"
                    enabled -> "Service enabled but not connected yet — try toggling it off and on"
                    else -> "Blocking service is off"
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (!enabled) {
                Text(
                    "1. Tap \"Open Accessibility settings\".\n" +
                        "2. Find Night Blocker — on Samsung it's under \"Installed apps\" (other phones: \"Downloaded apps\").\n" +
                        "3. Turn it on.\n\n" +
                        "If the switch is greyed out (\"Restricted setting\"): tap \"Open App info\", then the ⋮ menu → " +
                        "\"Allow restricted settings\", and try again.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenAccessibility) { Text("Open Accessibility settings") }
                }
                OutlinedButton(onClick = onOpenAppInfo) { Text("Open App info") }
            }
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
