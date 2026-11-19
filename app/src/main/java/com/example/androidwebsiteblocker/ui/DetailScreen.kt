package com.example.androidwebsiteblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.DEFAULT_REMINDER_INTERVAL
import com.example.androidwebsiteblocker.core.model.LimitScope
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.policy.SoftUsage
import com.example.androidwebsiteblocker.core.policy.UsageKey
import com.example.androidwebsiteblocker.core.policy.formatDuration
import com.example.androidwebsiteblocker.core.protection.SuspensionCheck
import com.example.androidwebsiteblocker.core.protection.SuspensionPolicy
import com.example.androidwebsiteblocker.core.schedule.groupWindows
import com.example.androidwebsiteblocker.core.storage.AppState
import com.example.androidwebsiteblocker.core.storage.streakOf
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** PIN-protected actions on one block set. */
enum class SetAction(val pinTitle: String) {
    EDIT("Enter PIN to edit"),
    SUSPEND("Enter PIN to pause"),
    DISABLE("Enter PIN to turn off"),
    ENABLE("Enter PIN to turn on"),
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
    val accent = set.colorArgb.asColor()
    val status = statusOf(set, state, now, zone)
    var pinFor by remember { mutableStateOf<SetAction?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(set.name, onBack = onBack)

        // Status hero
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(status.state.label(set.mode), status.state.color())
                    Pill(set.mode.shortLabel(), accent)
                }
                Text(status.text, style = MaterialTheme.typography.titleLarge)
                val streak = state.streakOf(set.id).days
                if (set.mode != BlockMode.REMINDER) Text(
                    if (streak > 0) "🔥 $streak-day streak" else "No streak yet — get through a full window without pausing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (streak > 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (set.message.isNotBlank()) Text("“${set.message}”", style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.pendingChanges[set.id]?.let { pending ->
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val what = if (!pending.proposed.enabled) "Turning this block set off" else "Your change (it blocks less)"
                    Text("$what takes effect at ${formatInstant(pending.appliesAt, zone)}.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = onCancelPending) { Text("Cancel — keep it as is") }
                }
            }
        }

        SectionCard("Schedule") {
            WeekBars(set.schedule.windows, accent)
            groupWindows(set.schedule.windows).forEach { WindowGroupRow(it, accent) }
        }

        SectionCard("Blocks", subtitle = "${set.targets.size} apps and websites") {
            set.targets.sortedBy { it !is Target.App }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TargetIcon(t, 36.dp)
                    Text(targetLabel(context, t), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        if (set.mode == BlockMode.SOFT) {
            UsageSection(set, state, now, zone)
            SectionCard(
                "Soft block limits",
                subtitle = if (set.limitScope == LimitScope.WHOLE_SET) "Shared across the whole set, reset each window" else "Per app or site, reset each window",
            ) {
                val l = set.softLimits
                LimitRow("Wait before Continue unlocks", if (l.unlockDelay.isZero) "None" else formatDuration(l.unlockDelay))
                LimitRow("Opens allowed", l.maxOpens?.toString() ?: "Unlimited")
                LimitRow("Time per open", l.perOpenLimit?.let(::formatDuration) ?: "No limit")
                LimitRow("Total time", l.totalLimit?.let(::formatDuration) ?: "No limit")
                LimitRow("Grace period", formatDuration(l.graceTime))
                LimitRow("Usage reminders", l.reminderInterval?.let { "Every ${formatDuration(it)}" } ?: "Off")
            }
        }

        if (set.mode == BlockMode.REMINDER) {
            UsageSection(set, state, now, zone)
            SectionCard("Reminders") {
                LimitRow("Remind me every", formatDuration(set.softLimits.reminderInterval ?: DEFAULT_REMINDER_INTERVAL))
                LimitRow("Counting", if (set.limitScope == LimitScope.WHOLE_SET) "All apps together" else "Each app separately")
            }
        }

        if (set.mode != BlockMode.REMINDER) SectionCard(
            "Emergency pause",
            info = "Pauses the whole block set for ${formatDuration(set.protection.suspensionDuration)}, then it turns itself back on. " +
                "Needs your PIN, is limited per window, and resets your streak.",
        ) {
            val check = SuspensionPolicy.check(set, state.suspensions[set.id], now, zone)
            Hint(pauseText(check, set, zone))
            Button(
                enabled = check is SuspensionCheck.Allowed,
                onClick = { pinFor = SetAction.SUSPEND },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Pause for ${formatDuration(set.protection.suspensionDuration)}") }
        }

        SectionCard("Manage") {
            OutlinedButton(onClick = { pinFor = SetAction.EDIT }, modifier = Modifier.fillMaxWidth()) { Text("Edit block set") }
            if (set.enabled) {
                OutlinedButton(onClick = { pinFor = SetAction.DISABLE }, modifier = Modifier.fillMaxWidth()) { Text("Turn off") }
                Hint("Turning off takes effect after ${formatDuration(set.protection.changeWait)}. You can cancel it until then.")
            } else {
                Button(onClick = { pinFor = SetAction.ENABLE }, modifier = Modifier.fillMaxWidth()) { Text("Turn on") }
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Delete") }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    pinFor?.let { action ->
        PinDialog(action.pinTitle, set.pinHash.orEmpty(), onVerified = { pinFor = null; onAction(action) }, onDismiss = { pinFor = null })
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${set.name}?", "This removes the block set and its streak for good.", "Continue",
            onConfirm = { confirmDelete = false; pinFor = SetAction.DELETE }, onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun LimitRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = Color.Unspecified)
    }
}

private fun pauseText(check: SuspensionCheck, set: BlockSet, zone: ZoneId): String = when (check) {
    is SuspensionCheck.Allowed -> check.remainingAfter?.let { "${it + 1} left this window." } ?: "Unlimited this window."
    SuspensionCheck.NotInWindow -> if (set.enabled) "Only available while the block set is on." else "The block set is turned off."
    is SuspensionCheck.AlreadySuspended -> "Paused until ${formatInstantSeconds(check.until, zone)}."
    is SuspensionCheck.LimitReached -> "All ${check.max} pauses used this window."
    is SuspensionCheck.CoolingDown -> "Next pause available at ${formatInstantSeconds(check.until, zone)}."
}

/**
 * Live usage for the current window: per app/site, every open with its start time and on-screen
 * duration, plus what's left. Lets you check the counting against what you remember doing.
 */
@Composable
private fun UsageSection(set: BlockSet, state: AppState, now: Instant, zone: ZoneId) {
    val context = LocalContext.current
    val occurrence = set.schedule.occurrenceAt(now, zone)
    SectionCard(
        "This window's usage",
        subtitle = occurrence?.let { "Since ${it.start.format(USAGE_TIME)} · resets when the next window starts" },
        info = "Each app and site has its own count. Time is only counted while it's actually on screen after you tap Continue — " +
            "not on the block screen, with the screen off, or during the grace period.\n\n" +
            "If an app is in more than one soft block set (like Chrome and a website you open in it), time and opens " +
            "count against each set.",
    ) {
        if (occurrence == null || !set.enabled) {
            Hint("Usage shows up here while the block set is on.")
            return@SectionCard
        }
        val l = set.softLimits
        val usageTargets = if (set.limitScope == LimitScope.WHOLE_SET) listOf(Target.WholeSet) else set.targets.sortedBy { it !is Target.App }
        usageTargets.forEach { target ->
            val u = state.usage[UsageKey(set.id, target, occurrence.key)] ?: SoftUsage()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TargetIcon(target, 32.dp)
                Column(Modifier.weight(1f)) {
                    Text(targetLabel(context, target), style = MaterialTheme.typography.bodyLarge)
                    if (set.mode == BlockMode.REMINDER) Hint("${formatDuration(u.timeUsed)} on screen this window") else Hint(
                        listOfNotNull(
                            l.maxOpens?.let { "${(it - u.opensUsed).coerceAtLeast(0)} of $it opens left" } ?: "${u.opensUsed} opens",
                            l.totalLimit?.let { "${formatDuration((it - u.timeUsed).coerceAtLeast(Duration.ZERO))} of ${formatDuration(it)} left" }
                                ?: "${formatDuration(u.timeUsed)} used",
                        ).joinToString(" · "),
                    )
                }
            }
            val live = u.session?.let { s ->
                (s.leftAt == null || Duration.between(s.leftAt, now) <= l.graceTime) &&
                    (l.perOpenLimit == null || s.used < l.perOpenLimit)
            } == true
            u.opens.forEachIndexed { i, open ->
                val current = live && i == u.opens.lastIndex
                val perOpen = l.perOpenLimit
                val tail = when {
                    current && perOpen != null -> " · open now, ${formatDuration(perOpen - open.used)} left"
                    current -> " · open now"
                    else -> ""
                }
                Text(
                    "Open ${i + 1} · ${LocalDateTime.ofInstant(open.startedAt, zone).format(USAGE_TIME)} · ${formatDuration(open.used)}$tail",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (current) set.colorArgb.asColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp),
                )
            }
        }
    }
}

private val USAGE_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
