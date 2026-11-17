package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.policy.formatDuration
import com.example.androidwebsiteblocker.core.protection.ChangeOutcome
import com.example.androidwebsiteblocker.core.protection.SuspensionCheck
import com.example.androidwebsiteblocker.core.storage.StateActions
import com.example.androidwebsiteblocker.core.storage.setById
import com.example.androidwebsiteblocker.data.StateStore
import com.example.androidwebsiteblocker.ui.DebugLogScreen
import com.example.androidwebsiteblocker.ui.DetailScreen
import com.example.androidwebsiteblocker.ui.EditorScreen
import com.example.androidwebsiteblocker.ui.HomeScreen
import com.example.androidwebsiteblocker.ui.SetAction
import com.example.androidwebsiteblocker.ui.formatInstant
import com.example.androidwebsiteblocker.ui.theme.AndroidWebsiteBlockerTheme
import java.time.Instant
import java.time.ZoneId

class MainActivity : ComponentActivity() {

    /** Re-checked on every resume, since the user enables the service in Settings and comes back. */
    private val serviceEnabled = mutableStateOf(false)
    private val batteryExempt = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = StateStore.get(this)
        setContent {
            AndroidWebsiteBlockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(Modifier.padding(innerPadding)) {
                        AppRoot(store, serviceEnabled.value, batteryExempt.value)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        serviceEnabled.value = isServiceEnabled(this)
        batteryExempt.value = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }
}

fun isServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(AccessibilityManager::class.java)
    return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        val info = it.resolveInfo.serviceInfo
        info.packageName == context.packageName && info.name == NightBlockerService::class.java.name
    }
}

/** Screens: "home", "log", "new", "set:<id>", "edit:<id>". A string so it survives rotation. */
@Composable
private fun AppRoot(store: StateStore, serviceEnabled: Boolean, batteryExempt: Boolean) {
    val context = LocalContext.current
    val state by store.state.collectAsState()
    val connected by ForegroundMonitor.serviceConnected.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    val zone = ZoneId.systemDefault()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()

    when {
        screen == "log" -> DebugLogScreen(onBack = { screen = "home" })

        screen == "new" -> EditorScreen(
            original = null,
            otherSets = state.sets,
            onSave = { set ->
                store.update { StateActions.addSet(it, set) }
                toast("Created ${set.name}")
                screen = "home"
            },
            onCancel = { screen = "home" },
        )

        screen.startsWith("edit:") -> {
            val set = state.setById(screen.removePrefix("edit:"))
            if (set == null) { screen = "home"; return }
            EditorScreen(
                original = set,
                otherSets = state.sets.filter { it.id != set.id },
                onSave = { proposed ->
                    var outcome: ChangeOutcome? = null
                    store.update { s -> StateActions.requestEdit(s, proposed, Instant.now(), zone).also { outcome = it.second }.first }
                    toast(changeMessage(outcome, zone))
                    screen = "set:${set.id}"
                },
                onCancel = { screen = "set:${set.id}" },
            )
        }

        screen.startsWith("set:") -> {
            val set = state.setById(screen.removePrefix("set:"))
            if (set == null) { screen = "home"; return }
            DetailScreen(
                set = set,
                state = state,
                onAction = { action ->
                    val now = Instant.now()
                    when (action) {
                        SetAction.EDIT -> screen = "edit:${set.id}"
                        SetAction.SUSPEND -> {
                            var check: SuspensionCheck? = null
                            store.update { s -> StateActions.suspend(s, set.id, now, zone).also { check = it.second }.first }
                            if (check is SuspensionCheck.Allowed) {
                                toast("${set.name} paused for ${formatDuration(set.protection.suspensionDuration)}. Streak reset.")
                            } else {
                                toast("Can't pause right now")
                            }
                        }
                        SetAction.DISABLE, SetAction.ENABLE -> {
                            var outcome: ChangeOutcome? = null
                            store.update { s ->
                                StateActions.requestEdit(s, set.copy(enabled = action == SetAction.ENABLE), now, zone)
                                    .also { outcome = it.second }.first
                            }
                            toast(changeMessage(outcome, zone))
                        }
                        SetAction.DELETE -> {
                            store.update { StateActions.deleteSet(it, set.id) }
                            toast("Deleted ${set.name}")
                            screen = "home"
                        }
                    }
                },
                onCancelPending = { store.update { StateActions.cancelPending(it, set.id) } },
                onBack = { screen = "home" },
            )
        }

        else -> HomeScreen(
            state = state,
            serviceOk = serviceEnabled && connected,
            statusCard = { ServiceStatusCard(serviceEnabled, connected) },
            setupCard = if (batteryExempt) null else ({ BatteryCard() }),
            onOpenSet = { screen = "set:$it" },
            onCreate = { screen = "new" },
            onOpenLog = { screen = "log" },
        )
    }
}

private fun changeMessage(outcome: ChangeOutcome?, zone: ZoneId): String = when (outcome) {
    is ChangeOutcome.ApplyNow -> "Saved — in effect now"
    is ChangeOutcome.Pending -> "This blocks less, so it takes effect at ${formatInstant(outcome.change.appliesAt, zone)}. You can cancel it until then."
    null -> "Nothing changed"
}

/** Samsung can put apps to sleep; an exemption keeps blocking reliable. */
@SuppressLint("BatteryLife") // Personal app, never on the Play Store.
@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Keep blocking reliable", style = MaterialTheme.typography.titleMedium)
            Text(
                "Allow Night Blocker to run without battery restrictions so the phone doesn't put it to sleep. " +
                    "On Samsung, also make sure it isn't in Settings → Battery → Background usage limits → Sleeping apps.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.fromParts("package", context.packageName, null)),
                )
            }) { Text("Allow") }
        }
    }
}

@Composable
private fun ServiceStatusCard(enabled: Boolean, connected: Boolean) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (enabled) "Service enabled but not connected yet — try turning it off and on" else "Blocking service is off",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "1. Tap \"Open Accessibility settings\".\n" +
                    "2. Find Night Blocker — on Samsung it's under \"Installed apps\" (other phones: \"Downloaded apps\").\n" +
                    "3. Turn it on.\n\n" +
                    "If the switch is greyed out (\"Restricted setting\"): tap \"Open App info\", then the ⋮ menu → " +
                    "\"Allow restricted settings\", and try again.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                Text("Open Accessibility settings")
            }
            OutlinedButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }) { Text("Open App info") }
        }
    }
}
