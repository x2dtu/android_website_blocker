package com.example.androidwebsiteblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.ProtectionSettings
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.model.domainFromUserInput
import com.example.androidwebsiteblocker.core.policy.findOverlaps
import com.example.androidwebsiteblocker.core.protection.PinHasher
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import java.time.Duration
import java.util.UUID
import kotlinx.coroutines.delay

/** Editable copy of a block set. Numbers are kept as text so fields can be empty (= no limit). */
private class Draft(set: BlockSet?) {
    val id = set?.id ?: UUID.randomUUID().toString()
    var name by mutableStateOf(set?.name ?: "")
    var color by mutableStateOf(set?.colorArgb ?: SET_COLORS.first())
    var mode by mutableStateOf(set?.mode ?: BlockMode.HARD)
    var message by mutableStateOf(set?.message ?: "")
    var targets by mutableStateOf(set?.targets?.toList() ?: emptyList())
    var windows by mutableStateOf(set?.schedule?.windows ?: emptyList<DayWindow>())

    private val soft = set?.softLimits ?: SoftLimits()
    var unlockDelaySec by mutableStateOf(soft.unlockDelay.seconds.toString())
    var maxOpens by mutableStateOf(soft.maxOpens?.toString() ?: "")
    var perOpenMin by mutableStateOf(soft.perOpenLimit?.toMinutes()?.toString() ?: "")
    var totalMin by mutableStateOf(soft.totalLimit?.toMinutes()?.toString() ?: "")
    var graceSec by mutableStateOf(soft.graceTime.seconds.toString())

    private val p = set?.protection ?: ProtectionSettings()
    var suspensionMin by mutableStateOf(p.suspensionDuration.toMinutes().toString())
    var maxSuspensions by mutableStateOf(p.maxSuspensions?.toString() ?: "")
    var cooldownSec by mutableStateOf(p.suspensionCooldown.seconds.toString())
    var changeWaitMin by mutableStateOf(p.changeWait.toMinutes().toString())

    var pin by mutableStateOf("")
    var pinConfirm by mutableStateOf("")

    /** Builds the set, or returns the list of problems. */
    fun build(original: BlockSet?): Pair<BlockSet?, List<String>> {
        val errors = mutableListOf<String>()
        fun num(text: String, field: String, min: Long = 0): Long? {
            if (text.isBlank()) return null
            val n = text.toLongOrNull()
            if (n == null || n < min) {
                errors += "$field must be a number ≥ $min"
                return null
            }
            return n
        }
        if (name.isBlank()) errors += "Give the block set a name"
        if (targets.isEmpty()) errors += "Add at least one app or website"
        if (windows.isEmpty()) errors += "Add at least one time window"
        val softLimits = SoftLimits(
            unlockDelay = Duration.ofSeconds(num(unlockDelaySec, "Unlock delay") ?: 0),
            maxOpens = num(maxOpens, "Max opens", 1)?.toInt(),
            perOpenLimit = num(perOpenMin, "Time per open", 1)?.let(Duration::ofMinutes),
            totalLimit = num(totalMin, "Total time", 1)?.let(Duration::ofMinutes),
            graceTime = Duration.ofSeconds(num(graceSec, "Grace time") ?: 15),
        )
        val protection = ProtectionSettings(
            suspensionDuration = Duration.ofMinutes(num(suspensionMin, "Suspension length", 1) ?: 1),
            maxSuspensions = num(maxSuspensions, "Max suspensions")?.toInt(),
            suspensionCooldown = Duration.ofSeconds(num(cooldownSec, "Suspension cooldown") ?: 30),
            changeWait = Duration.ofMinutes(num(changeWaitMin, "Change wait") ?: 5),
        )
        val pinHash = if (original == null) {
            when {
                !PinHasher.isValidPin(pin) -> { errors += "PIN must be ${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits"; null }
                pin != pinConfirm -> { errors += "PINs don't match"; null }
                else -> PinHasher.hash(pin)
            }
        } else original.pinHash
        if (errors.isNotEmpty()) return null to errors
        val base = original ?: BlockSet(id, name, mode, emptySet(), WeeklySchedule(emptyList()))
        return base.copy(
            name = name.trim(), mode = mode, targets = targets.toSet(), schedule = WeeklySchedule(windows),
            softLimits = softLimits, protection = protection, message = message.trim(), colorArgb = color, pinHash = pinHash,
        ) to emptyList()
    }

    /** Best-effort set for live overlap warnings (ignores invalid number fields). */
    fun preview(): BlockSet = BlockSet(id, name.ifBlank { "This set" }, mode, targets.toSet(), WeeklySchedule(windows))
}

/** Create ([original] null) or edit a block set. PIN for edits is checked before opening this. */
@Composable
fun EditorScreen(original: BlockSet?, otherSets: List<BlockSet>, onSave: (BlockSet) -> Unit, onCancel: () -> Unit) {
    val draft = remember(original?.id) { Draft(original) }
    var errors by remember { mutableStateOf(emptyList<String>()) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        AppPickerScreen(
            selected = draft.targets.filterIsInstance<Target.App>().map { it.packageName }.toSet(),
            onDone = { pkgs ->
                draft.targets = draft.targets.filterIsInstance<Target.Website>() + pkgs.sorted().map { Target.App(it) }
                picking = false
            },
        )
        return
    }
    BackHandler(onBack = onCancel)

    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeader(if (original == null) "New block set" else "Edit ${original.name}", onBack = onCancel)

        OutlinedTextField(draft.name, { draft.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Color")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SET_COLORS.forEach { c ->
                Box(
                    Modifier.size(32.dp).background(c.asColor(), CircleShape)
                        .border(if (c == draft.color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { draft.color = c },
                )
            }
        }

        Section("Type")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(draft.mode == BlockMode.HARD, { draft.mode = BlockMode.HARD }, label = { Text("Hard block") })
            FilterChip(draft.mode == BlockMode.SOFT, { draft.mode = BlockMode.SOFT }, label = { Text("Soft block") })
        }
        Text(
            if (draft.mode == BlockMode.HARD) "Blocked for the whole window, no way in."
            else "You can still get in, with a delay and limits. Each app/site is limited separately.",
            style = MaterialTheme.typography.bodySmall,
        )

        Section("Apps and websites")
        draft.targets.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetIcon(t)
                Text(targetLabel(context, t), modifier = Modifier.weight(1f))
                TextButton(onClick = { draft.targets = draft.targets - t }) { Text("Remove") }
            }
        }
        OutlinedButton(onClick = { picking = true }) { Text("Choose apps") }
        WebsiteAdder { domain -> if (draft.targets.none { it == Target.Website(domain) }) draft.targets = draft.targets + Target.Website(domain) }

        Section("Schedule")
        ScheduleEditor(draft.windows, draft.color.asColor()) { draft.windows = it }

        val warnings = remember(draft.targets, draft.windows, otherSets) { findOverlaps(draft.preview(), otherSets) }
        if (warnings.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Overlaps with other block sets", style = MaterialTheme.typography.titleSmall)
                    warnings.forEach { Text("• " + it.describe(targetLabel(context, it.target)), style = MaterialTheme.typography.bodySmall) }
                    Text("The most restrictive one wins while they overlap.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (draft.mode == BlockMode.SOFT) {
            Section("Soft block limits (leave empty for no limit)")
            NumberField(draft.unlockDelaySec, { draft.unlockDelaySec = it }, "Unlock delay (seconds)")
            NumberField(draft.maxOpens, { draft.maxOpens = it }, "Max opens per window")
            NumberField(draft.perOpenMin, { draft.perOpenMin = it }, "Time per open (minutes)")
            NumberField(draft.totalMin, { draft.totalMin = it }, "Total time per window (minutes)")
            NumberField(draft.graceSec, { draft.graceSec = it }, "Grace time to return without a new open (seconds)")
        }

        OutlinedTextField(draft.message, { draft.message = it }, label = { Text("Block screen message") }, modifier = Modifier.fillMaxWidth())

        Section("Protection")
        NumberField(draft.suspensionMin, { draft.suspensionMin = it }, "Emergency suspension length (minutes)")
        NumberField(draft.maxSuspensions, { draft.maxSuspensions = it }, "Max suspensions per window (empty = unlimited)")
        NumberField(draft.cooldownSec, { draft.cooldownSec = it }, "Cooldown between suspensions (seconds)")
        NumberField(draft.changeWaitMin, { draft.changeWaitMin = it }, "Wait before disabling/loosening applies (minutes)")
        if (original == null) {
            PinField(draft.pin, { draft.pin = it }, "PIN (needed to edit, suspend, or disable)")
            PinField(draft.pinConfirm, { draft.pinConfirm = it }, "Confirm PIN")
            Text("There's no PIN recovery — forgetting it means clearing the app's data.", style = MaterialTheme.typography.bodySmall)
        } else {
            Text(
                "Changes that block less (or weaken protection) wait ${original.protection.changeWait.toMinutes()} min before applying.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = {
                val (set, problems) = draft.build(original)
                errors = problems
                if (set != null) onSave(set)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (original == null) "Create block set" else "Save changes") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String) {
    HorizontalDivider()
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(6)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WebsiteAdder(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val domain = domainFromUserInput(text)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            text, { text = it }, label = { Text("Website, e.g. reddit.com") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.weight(1f),
        )
        Button(enabled = domain != null, onClick = { domain?.let(onAdd); text = "" }) { Text("Add") }
    }
    if (domain != null) Text("Blocks $domain and its subdomains", style = MaterialTheme.typography.bodySmall)
}

/** Searchable list of installed apps; search runs shortly after you stop typing. */
@Composable
private fun AppPickerScreen(selected: Set<String>, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(selected) }
    var query by remember { mutableStateOf("") }
    var appliedQuery by remember { mutableStateOf("") }
    val apps by produceState<List<InstalledApp>?>(null) { value = loadInstalledApps(context) }
    LaunchedEffect(query) {
        delay(250)
        appliedQuery = query
    }
    BackHandler { onDone(chosen) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        ScreenHeader("Choose apps", onBack = { onDone(chosen) })
        OutlinedTextField(query, { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        val list = apps
        if (list == null) {
            Text("Loading apps…", modifier = Modifier.padding(16.dp))
        } else {
            val filtered = list.filter {
                appliedQuery.isBlank() || it.label.contains(appliedQuery, ignoreCase = true) || it.packageName.contains(appliedQuery, ignoreCase = true)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chosen = if (app.packageName in chosen) chosen - app.packageName else chosen + app.packageName
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(app.packageName, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(app.label)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                        Checkbox(checked = app.packageName in chosen, onCheckedChange = null)
                    }
                }
            }
        }
        Button(onClick = { onDone(chosen) }, modifier = Modifier.fillMaxWidth()) { Text("Done (${chosen.size} selected)") }
    }
}

