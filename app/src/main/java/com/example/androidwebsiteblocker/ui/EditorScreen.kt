package com.example.androidwebsiteblocker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidwebsiteblocker.Browsers
import com.example.androidwebsiteblocker.isTv
import com.example.androidwebsiteblocker.core.model.BlockMode
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.DEFAULT_REMINDER_INTERVAL
import com.example.androidwebsiteblocker.core.model.LimitScope
import com.example.androidwebsiteblocker.core.model.ProtectionSettings
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.model.domainFromUserInput
import com.example.androidwebsiteblocker.core.policy.findOverlaps
import com.example.androidwebsiteblocker.core.protection.PinHasher
import com.example.androidwebsiteblocker.core.schedule.DayWindow
import com.example.androidwebsiteblocker.core.schedule.WeeklySchedule
import com.example.androidwebsiteblocker.data.AdultSiteList
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
    var timeWarnings by mutableStateOf(soft.timeWarnings)
    var limitScope by mutableStateOf(set?.limitScope ?: LimitScope.EACH_TARGET)
    /** Soft sets: whether usage reminders are on. Reminder sets always remind. */
    var softReminders by mutableStateOf(set?.mode == BlockMode.SOFT && soft.reminderInterval != null)
    var reminderMin by mutableStateOf((soft.reminderInterval ?: DEFAULT_REMINDER_INTERVAL).toMinutes().toString())

    private val p = set?.protection ?: ProtectionSettings()
    var suspensionMin by mutableStateOf(p.suspensionDuration.toMinutes().toString())
    var maxSuspensions by mutableStateOf(p.maxSuspensions?.toString() ?: "")
    var cooldownSec by mutableStateOf(p.suspensionCooldown.seconds.toString())
    var changeWaitMin by mutableStateOf(p.changeWait.toMinutes().toString())

    var usePin by mutableStateOf(true)
    var pin by mutableStateOf("")
    var pinConfirm by mutableStateOf("")

    /** Builds the set, or returns the list of problems. */
    fun build(original: BlockSet?): Pair<BlockSet?, List<String>> {
        val errors = mutableListOf<String>()
        fun num(text: String, field: String, min: Long = 0): Long? {
            if (text.isBlank()) return null
            val n = text.toLongOrNull()
            if (n == null || n < min) {
                errors += "$field must be at least $min"
                return null
            }
            return n
        }
        if (name.isBlank()) errors += "Give the block set a name"
        if (targets.isEmpty()) errors += "Add at least one app or website"
        if (windows.isEmpty()) errors += "Add at least one time window"
        val perOpen = num(perOpenMin, "Time per open", 1)
        val total = num(totalMin, "Total time", 1)
        val reminderInterval: Duration? = when {
            mode == BlockMode.REMINDER || (mode == BlockMode.SOFT && softReminders) -> {
                val m = num(reminderMin, "Reminder interval", 1)
                if (m == null && reminderMin.isBlank()) errors += "Enter how often to remind you"
                m?.let(Duration::ofMinutes)
            }
            else -> null
        }
        if (mode == BlockMode.SOFT && reminderInterval != null) {
            // A reminder that can never come up would be confusing.
            if (perOpen != null && reminderInterval >= Duration.ofMinutes(perOpen)) {
                errors += "Reminder interval must be shorter than the time per open ($perOpen min)"
            }
            if (total != null && reminderInterval >= Duration.ofMinutes(total)) {
                errors += "Reminder interval must be shorter than the total time ($total min)"
            }
        }
        val softLimits = SoftLimits(
            unlockDelay = Duration.ofSeconds(num(unlockDelaySec, "Unlock wait") ?: 0),
            maxOpens = num(maxOpens, "Opens allowed", 1)?.toInt(),
            perOpenLimit = perOpen?.let(Duration::ofMinutes),
            totalLimit = total?.let(Duration::ofMinutes),
            graceTime = Duration.ofSeconds(num(graceSec, "Grace period") ?: 15),
            timeWarnings = timeWarnings,
            reminderInterval = reminderInterval,
        )
        val protection = ProtectionSettings(
            suspensionDuration = Duration.ofMinutes(num(suspensionMin, "Emergency pause length", 1) ?: 1),
            maxSuspensions = num(maxSuspensions, "Emergency pauses allowed")?.toInt(),
            suspensionCooldown = Duration.ofSeconds(num(cooldownSec, "Wait between pauses") ?: 30),
            changeWait = Duration.ofMinutes(num(changeWaitMin, "Cooling-off period") ?: 5),
        )
        val pinHash = if (original == null) {
            when {
                !usePin -> null
                !PinHasher.isValidPin(pin) -> { errors += "PIN must be ${PinHasher.MIN_LENGTH}–${PinHasher.MAX_LENGTH} digits"; null }
                pin != pinConfirm -> { errors += "PINs don't match"; null }
                else -> PinHasher.hash(pin)
            }
        } else original.pinHash
        if (errors.isNotEmpty()) return null to errors
        val base = original ?: BlockSet(id, name, mode, emptySet(), WeeklySchedule(emptyList()))
        return base.copy(
            name = name.trim(), mode = mode, targets = targets.toSet(), schedule = WeeklySchedule(windows),
            softLimits = softLimits, limitScope = limitScope, protection = protection, message = message.trim(), colorArgb = color, pinHash = pinHash,
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
    val accent = draft.color.asColor()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ScreenHeader(if (original == null) "New block set" else "Edit block set", onBack = onCancel)

        SectionCard("Name & color") {
            OutlinedTextField(
                draft.name, { draft.name = it }, label = { Text("Name") }, placeholder = { Text("e.g. Bedtime, Work focus") },
                singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = formImeAction()),
                keyboardActions = formKeyboardActions(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SET_COLORS.forEach { c ->
                    Box(
                        Modifier.size(34.dp).background(c.asColor(), CircleShape)
                            .border(if (c == draft.color) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { draft.color = c },
                    )
                }
            }
        }

        SectionCard(
            "How strict?",
            info = "Hard block: during the schedule, the apps and sites can't be opened at all. The block screen only lets you leave.\n\n" +
                "Soft block: you can still get in by tapping Continue on the block screen, but with the limits you choose below " +
                "(a wait before Continue unlocks, a number of opens, and time limits).\n\n" +
                "Reminder: never blocks. A small banner tells you how long you've been on an app every so often, " +
                "e.g. \"Instagram · 30 minutes so far\".\n\n" +
                "Need different rules for different apps? Make more block sets.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceCard(
                    "Hard block", "No way in during the schedule.",
                    draft.mode == BlockMode.HARD, { draft.mode = BlockMode.HARD }, Modifier.fillMaxWidth(),
                )
                ChoiceCard(
                    "Soft block", "Let me in, with a wait and limits.",
                    draft.mode == BlockMode.SOFT, { draft.mode = BlockMode.SOFT }, Modifier.fillMaxWidth(),
                )
                ChoiceCard(
                    "Reminder", "Never blocks — just tells me how long I've been on.",
                    draft.mode == BlockMode.REMINDER, { draft.mode = BlockMode.REMINDER }, Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard(
            "Apps & websites",
            subtitle = "${draft.targets.size} selected",
            info = "Apps are blocked whenever they're on screen. Websites include all their subdomains " +
                "(reddit.com also covers old.reddit.com). When a website is blocked, leaving opens a new tab so your other tabs stay put.\n\n" +
                Browsers.supportText,
        ) {
            draft.targets.filter { it != Target.AdultWebsites }.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TargetIcon(t, 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(targetLabel(context, t), style = MaterialTheme.typography.bodyLarge)
                        Hint(if (t is Target.Website) "Website" else "App")
                    }
                    TextButton(onClick = { draft.targets = draft.targets - t }) { Text("Remove") }
                }
            }
            // Full width so the remote's Down reaches it from anything above (not just from the left).
            FilledTonalButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text("+ Choose apps") }
            WebsiteAdder { domain -> if (Target.Website(domain) !in draft.targets) draft.targets = draft.targets + Target.Website(domain) }
            AdultSitesToggle(
                checked = Target.AdultWebsites in draft.targets,
                onChange = { on ->
                    draft.targets = if (on) draft.targets + Target.AdultWebsites else draft.targets - Target.AdultWebsites
                },
            )
        }

        SectionCard(
            "Schedule",
            subtitle = "When this block set is active",
            info = "The block set only applies during these times. A window that ends earlier than it starts (like 11:30 PM – 8:00 AM) " +
                "runs overnight into the next morning.\n\nTimes follow your phone's clock, so daylight saving never shifts them. " +
                "Soft block limits reset each time a window starts.",
        ) {
            ScheduleEditor(draft.windows, accent) { draft.windows = it }
        }

        val warnings = remember(draft.targets, draft.windows, otherSets) { findOverlaps(draft.preview(), otherSets) }
        if (warnings.isNotEmpty()) {
            Card(
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Heads up: overlapping block sets", style = MaterialTheme.typography.titleSmall)
                    warnings.forEach { Text("• " + it.describe(targetLabel(context, it.target)), style = MaterialTheme.typography.bodySmall) }
                    Text("While they overlap, the strictest one wins.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (draft.mode == BlockMode.SOFT) {
            SectionCard(
                "Soft block limits",
                subtitle = "Everything resets when the next window starts.",
            ) {
                LimitScopeChooser(draft.limitScope) { draft.limitScope = it }
                NumberSetting(
                    "Wait before Continue unlocks", draft.unlockDelaySec, { draft.unlockDelaySec = it }, "sec",
                    hint = "0 = Continue is available right away",
                    info = "When you open a blocked app, the Continue button stays locked for this long — a moment to change your mind. " +
                        "If you leave the block screen, the wait starts over next time.",
                )
                NumberSetting(
                    "Opens allowed", draft.maxOpens, { draft.maxOpens = it }, "opens",
                    hint = if (draft.limitScope == LimitScope.WHOLE_SET) "Shared, per window" else "Per app, per window",
                    blankMeans = "Unlimited opens",
                    info = "How many times you can tap Continue for each app or site during one window. " +
                        "Shown as circles on the block screen. Once used up, it's blocked until the window ends.",
                )
                NumberSetting(
                    "Time per open", draft.perOpenMin, { draft.perOpenMin = it }, "min",
                    hint = "Block screen returns after this", blankMeans = "No limit per open",
                    info = "Each open lasts at most this long, then the block screen comes back. " +
                        "Leftover time isn't saved — every open starts fresh at this limit.",
                )
                NumberSetting(
                    "Total time", draft.totalMin, { draft.totalMin = it }, "min",
                    hint = if (draft.limitScope == LimitScope.WHOLE_SET) "Shared, per window, across all opens" else "Per app, per window, across all opens",
                    blankMeans = "No total limit",
                    info = "Combined time across all opens during one window. When it runs out, the current open is cut short " +
                        "and the app stays blocked until the window ends.",
                )
                NumberSetting(
                    "Grace period", draft.graceSec, { draft.graceSec = it }, "sec",
                    hint = "Come back within this and it's the same open",
                    info = "If you leave the app briefly — to answer a text, say — and return within this time, " +
                        "it continues the same open instead of using a new one. Time away doesn't count against you.",
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Time warnings", style = MaterialTheme.typography.bodyLarge)
                        Hint(if (draft.totalMin.isBlank()) "Needs a total time limit" else "Banner at 10 min and 1 min of total time left")
                    }
                    InfoButton(
                        "Time warnings",
                        "Shows a small banner over the app when 10 minutes and 1 minute of its total time are left in this window, " +
                            "so the block doesn't catch you mid-scroll. It fades after a few seconds and you can keep using the app. " +
                            "Each warning comes once per window; the 10-minute one is skipped if the total limit is 10 minutes or less.",
                    )
                    Switch(checked = draft.timeWarnings, onCheckedChange = { draft.timeWarnings = it })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Usage reminders", style = MaterialTheme.typography.bodyLarge)
                        Hint("\"N minutes so far\" banner while you're in the app")
                    }
                    InfoButton(
                        "Usage reminders",
                        "While you're using an app after tapping Continue, shows a small banner every so often with how long " +
                            "you've spent on it this window. Must be shorter than the time per open and the total time.",
                    )
                    Switch(checked = draft.softReminders, onCheckedChange = { draft.softReminders = it })
                }
                if (draft.softReminders) ReminderIntervalField(draft.reminderMin) { draft.reminderMin = it }
            }
        }

        if (draft.mode == BlockMode.REMINDER) {
            SectionCard("Reminders", subtitle = "Counts time on screen during the schedule; resets each window.") {
                ReminderIntervalField(draft.reminderMin) { draft.reminderMin = it }
                LimitScopeChooser(draft.limitScope, label = "Count time for") { draft.limitScope = it }
            }
        }

        if (draft.mode != BlockMode.REMINDER) SectionCard(
            "Block screen message",
            info = "Shown in big letters every time this block set stops you. Write something that'll actually work on you at 1 AM.",
        ) {
            OutlinedTextField(
                draft.message, { draft.message = it }, placeholder = { Text("e.g. Future you will thank you. Go to sleep.") },
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                // Single line on TVs: Fire TV's keyboard traps you in multi-line fields.
                singleLine = isTv(context),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = formImeAction()),
                keyboardActions = formKeyboardActions(),
            )
        }

        SectionCard("Safeguards", subtitle = "Make it harder to give in on impulse") {
            if (draft.mode != BlockMode.REMINDER) NumberSetting(
                "Emergency pause length", draft.suspensionMin, { draft.suspensionMin = it }, "min",
                hint = "How long an emergency pause lasts",
                info = "In a real emergency you can pause the whole block set with your PIN. It turns itself back on after this long. " +
                    "Pausing resets the streak.",
            )
            if (draft.mode != BlockMode.REMINDER) NumberSetting(
                "Emergency pauses allowed", draft.maxSuspensions, { draft.maxSuspensions = it }, "per window",
                hint = "Resets when the next window starts", blankMeans = "Unlimited pauses",
                info = "How many emergency pauses you get during one scheduled window.",
            )
            if (draft.mode != BlockMode.REMINDER) NumberSetting(
                "Wait between pauses", draft.cooldownSec, { draft.cooldownSec = it }, "sec",
                hint = "After a pause ends",
                info = "After an emergency pause ends, you have to wait this long before you can pause again, " +
                    "so pauses can't be chained back-to-back.",
            )
            NumberSetting(
                "Cooling-off period", draft.changeWaitMin, { draft.changeWaitMin = it }, "min",
                hint = "For disabling or weakening this set",
                info = "Disabling this block set, or editing it to block less (fewer apps, shorter times, looser limits), " +
                    "only takes effect after this wait — and you can cancel it in the meantime. " +
                    "Changes that block more apply immediately. Lowering this wait also has to wait.",
            )
        }

        if (original == null) {
            SectionCard(
                "PIN",
                subtitle = if (draft.usePin) "Needed to edit, pause, or turn off this block set" else "Without a PIN, those just ask \"Are you sure?\"",
                info = "A PIN adds a moment of friction before you can weaken a block set. Cooling-off waits apply either way.\n\n" +
                    "4–12 digits. There's no recovery — if you forget it, the only way out is clearing the app's data, " +
                    "which deletes all block sets. You choose this when creating the set.",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Protect with a PIN", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = draft.usePin, onCheckedChange = { draft.usePin = it })
                }
                if (draft.usePin) {
                    PinField(draft.pin, { draft.pin = it }, "PIN")
                    PinField(draft.pinConfirm, { draft.pinConfirm = it }, "Confirm PIN")
                }
            }
        } else {
            Hint(
                "Changes that block less wait ${original.protection.changeWait.toMinutes()} min before applying. " +
                    "Changes that block more apply right away.",
            )
        }

        if (errors.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(14.dp)) {
                    errors.forEach { Text("• $it", color = MaterialTheme.colorScheme.onErrorContainer) }
                }
            }
        }
        Button(
            onClick = {
                val (set, problems) = draft.build(original)
                errors = problems
                if (set != null) onSave(set)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text(if (original == null) "Create block set" else "Save changes") }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LimitScopeChooser(scope: LimitScope, label: String = "Limits apply to", onChange: (LimitScope) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        InfoButton(
            label,
            "Each app separately: every app and site in this set gets its own opens and time — " +
                "10 opens means 10 for Instagram and 10 for TikTok.\n\n" +
                "All together: one shared pool for the whole set — e.g. 10 opens and 20 minutes across all of them, " +
                "or one \"30 minutes so far\" for all your social apps combined. " +
                "Switching between apps in the set keeps counting as the same use.",
        )
    }
    // IntrinsicSize.Min + fillMaxHeight: both cards take the taller one's height (like flex stretch).
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ChoiceCard(
            "Each app separately", "Separate count per app or site.",
            scope == LimitScope.EACH_TARGET, { onChange(LimitScope.EACH_TARGET) }, Modifier.weight(1f).fillMaxHeight(),
        )
        ChoiceCard(
            "All together", "One shared count for the whole set.",
            scope == LimitScope.WHOLE_SET, { onChange(LimitScope.WHOLE_SET) }, Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun ReminderIntervalField(value: String, onChange: (String) -> Unit) {
    NumberSetting(
        "Remind me every", value, onChange, "min",
        hint = "Of time on screen",
        info = "Each time you've used an app (or the whole set, if counted together) for another this-many minutes " +
            "during the window, a banner shows how long you've been on it so far. Default 15 minutes.",
    )
}

@Composable
private fun AdultSitesToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BadgeIcon("18+", 36.dp)
        Column(Modifier.weight(1f)) {
            Text("Block adult websites", style = MaterialTheme.typography.bodyLarge)
            Hint("${AdultSiteList.approxSizeLabel()} known sites, in supported browsers")
        }
        InfoButton(
            "Block adult websites",
            "Blocks every site on a bundled list of ${AdultSiteList.approxSizeLabel()} adult domains (and their subdomains), " +
                "from The Block List Project, last updated ${AdultSiteList.LIST_DATE}. They count as one item for soft-block limits.\n\n" +
                "Brand-new sites may not be on the list yet.\n\n" + Browsers.supportText,
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun WebsiteAdder(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val domain = domainFromUserInput(text)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            text, { text = it }, label = { Text("Add a website") }, placeholder = { Text("reddit.com") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = formImeAction()),
            keyboardActions = formKeyboardActions(),
            shape = MaterialTheme.shapes.medium,
            supportingText = { Text(domain?.let { "Blocks $it and its subdomains" } ?: "Domain only, no https:// needed") },
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(enabled = domain != null, onClick = { domain?.let(onAdd); text = "" }) { Text("Add") }
    }
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader("Choose apps", onBack = { onDone(chosen) })
        OutlinedTextField(
            query, { query = it }, placeholder = { Text("Search apps") }, singleLine = true,
            shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(),
        )
        val list = apps
        if (list == null) {
            Hint("Loading apps…", Modifier.padding(16.dp))
        } else {
            val filtered = list.filter {
                appliedQuery.isBlank() || it.label.contains(appliedQuery, ignoreCase = true) || it.packageName.contains(appliedQuery, ignoreCase = true)
            }
            LazyColumn(Modifier.weight(1f).padding(top = 8.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            chosen = if (app.packageName in chosen) chosen - app.packageName else chosen + app.packageName
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        AppIcon(app.packageName, 40.dp)
                        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Checkbox(checked = app.packageName in chosen, onCheckedChange = null)
                    }
                }
            }
        }
        Button(onClick = { onDone(chosen) }, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).height(52.dp)) {
            Text("Done · ${chosen.size} selected")
        }
    }
}
