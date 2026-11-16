package com.example.androidwebsiteblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.blocking.BlockController
import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.policy.formatDuration
import com.example.androidwebsiteblocker.core.protection.SuspensionCheck
import com.example.androidwebsiteblocker.core.protection.SuspensionPolicy
import com.example.androidwebsiteblocker.core.storage.AppState
import com.example.androidwebsiteblocker.core.storage.streakOf
import java.time.ZoneId

/** PIN-protected actions on one block set. */
enum class SetAction(val pinTitle: String) {
    EDIT("Enter PIN to edit"),
    SUSPEND("Enter PIN to suspend"),
    DISABLE("Enter PIN to disable"),
    ENABLE("Enter PIN to enable"),
    DELETE("Enter PIN to delete"),
}

@Composable
fun DetailScreen(
    set: BlockSet,
    state: AppState,
    onAction: (SetAction) -> Unit,
    onCancelPending: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val now = rememberNow(1_000)
    val zone = ZoneId.systemDefault()
    var pinFor by remember { mutableStateOf<SetAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(set.name, onBack = onBack)
        Text(statusLine(set, state, now, zone), style = MaterialTheme.typography.bodyLarge)
        val streak = state.streakOf(set.id).days
        if (streak > 0) Text("$streak-day streak", color = set.colorArgb.asColor(), style = MaterialTheme.typography.titleSmall)

        state.pendingChanges[set.id]?.let { pending ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val what = if (!pending.proposed.enabled) "Disabling this set" else "A change that blocks less"
                    Text("$what applies at ${formatInstant(pending.appliesAt, zone)}.")
                    OutlinedButton(onClick = onCancelPending) { Text("Cancel pending change") }
                }
            }
        }

        WeekBars(set.schedule.windows, set.colorArgb.asColor())

        Text(if (set.mode == BlockMode.HARD) "Hard block" else "Soft block", style = MaterialTheme.typography.titleMedium)
        if (set.mode == BlockMode.SOFT) {
            val l = set.softLimits
            val lines = listOfNotNull(
                "Unlock delay: ${formatDuration(l.unlockDelay)}",
                l.maxOpens?.let { "Max opens: $it" },
                BlockController.limitsSummary(l).takeIf { it.isNotBlank() },
                "Grace time: ${formatDuration(l.graceTime)}",
            )
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (set.message.isNotBlank()) Text("“${set.message}”", style = MaterialTheme.typography.bodyMedium)

        HorizontalDivider()
        set.targets.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetIcon(t)
                Text(targetLabel(context, t))
            }
        }
        HorizontalDivider()

        val suspension = SuspensionPolicy.check(set, state.suspensions[set.id], now, zone)
        Text(suspensionText(suspension, set, zone), style = MaterialTheme.typography.bodySmall)
        Button(
            enabled = suspension is SuspensionCheck.Allowed,
            onClick = { pinFor = SetAction.SUSPEND },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Emergency: suspend for ${formatDuration(set.protection.suspensionDuration)}") }

        OutlinedButton(onClick = { pinFor = SetAction.EDIT }, modifier = Modifier.fillMaxWidth()) { Text("Edit") }
        if (set.enabled) {
            OutlinedButton(onClick = { pinFor = SetAction.DISABLE }, modifier = Modifier.fillMaxWidth()) {
                Text("Disable (applies after ${formatDuration(set.protection.changeWait)})")
            }
        } else {
            OutlinedButton(onClick = { pinFor = SetAction.ENABLE }, modifier = Modifier.fillMaxWidth()) { Text("Enable") }
            OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
        }
    }

    pinFor?.let { action ->
        PinDialog(action.pinTitle, set.pinHash.orEmpty(), onVerified = { pinFor = null; onAction(action) }, onDismiss = { pinFor = null })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${set.name}?", "This removes the block set and its streak.", "Continue",
            onConfirm = { confirmDelete = false; pinFor = SetAction.DELETE }, onDismiss = { confirmDelete = false },
        )
    }
}

private fun suspensionText(check: SuspensionCheck, set: BlockSet, zone: ZoneId): String = when (check) {
    is SuspensionCheck.Allowed -> check.remainingAfter?.let { "Suspensions left this window: ${it + 1}" } ?: "Unlimited suspensions"
    SuspensionCheck.NotInWindow -> if (set.enabled) "Not active right now — nothing to suspend." else "Disabled."
    is SuspensionCheck.AlreadySuspended -> "Suspended until ${formatInstantSeconds(check.until, zone)}."
    is SuspensionCheck.LimitReached -> "All ${check.max} suspensions used this window."
    is SuspensionCheck.CoolingDown -> "Next suspension available at ${formatInstantSeconds(check.until, zone)}."
}
