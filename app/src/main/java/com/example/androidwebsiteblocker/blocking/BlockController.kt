package com.example.androidwebsiteblocker.blocking

import android.accessibilityservice.AccessibilityService
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Browser
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.androidwebsiteblocker.CHROME_PACKAGE
import com.example.androidwebsiteblocker.TAG
import com.example.androidwebsiteblocker.core.model.BlockSet
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.SoftLimits
import com.example.androidwebsiteblocker.core.model.Target
import com.example.androidwebsiteblocker.core.policy.BlockEngine
import com.example.androidwebsiteblocker.core.policy.Decision
import com.example.androidwebsiteblocker.core.storage.StateActions
import com.example.androidwebsiteblocker.core.storage.setById
import com.example.androidwebsiteblocker.core.storage.streakOf
import com.example.androidwebsiteblocker.data.StateStore
import com.example.androidwebsiteblocker.core.policy.formatDuration
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Runs inside the accessibility service: feeds foreground changes into the [BlockEngine], shows
 * or hides the block screen, persists usage, and schedules re-checks (limits running out, window
 * boundaries, suspensions ending, pending changes applying).
 */
class BlockController(private val service: AccessibilityService, private val store: StateStore) {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tickRunnable = Runnable { tick() }
    private val overlay = BlockOverlay(service, onGoHome = ::leave, onContinue = ::unlock)

    private var zone: ZoneId = ZoneId.systemDefault()
    private var engine = BlockEngine(zone, store.state.value.sets, store.state.value.usage)

    fun start() {
        // Block set edits from the UI take effect immediately.
        scope.launch {
            store.state.map { it.sets }.distinctUntilChanged().collect {
                if (it != engine.sets) handle(engine.updateSets(it, now()))
            }
        }
        tick()
    }

    fun stop() {
        scope.cancel()
        handler.removeCallbacks(tickRunnable)
        overlay.hide()
    }

    fun onForegroundChanged(foreground: Foreground?) {
        handle(engine.onForegroundChanged(foreground, now()))
    }

    /** Time zone change: windows are local time, so rebuild with the new zone. */
    fun onTimeChanged() {
        val foreground = engine.foreground
        zone = ZoneId.systemDefault()
        engine = BlockEngine(zone, store.state.value.sets, engine.usage)
        engine.onForegroundChanged(foreground, now())
        tick()
    }

    private fun tick() {
        val now = now()
        store.update { StateActions.maintain(it, now, zone) }
        val sets = store.state.value.sets
        if (sets != engine.sets) engine.updateSets(sets, now)
        handle(engine.tick(now))
    }

    private fun unlock() = handle(engine.unlock(now()))

    /**
     * The block screen's exit (button or Back). For a blocked website in Chrome, opens a fresh
     * tab instead of going home — otherwise reopening Chrome lands straight back on the blocked
     * tab. The blocked tab and its history are left alone.
     */
    private fun leave() {
        val fg = engine.foreground
        if (fg != null && isWebsiteBlock(fg) && openSafeTab()) return
        goHome()
    }

    private fun goHome() {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /** True when what's blocked is the site, not the browser app itself. */
    private fun isWebsiteBlock(fg: Foreground): Boolean {
        if (fg.packageName != CHROME_PACKAGE || fg.host == null) return false
        val target = when (val d = engine.evaluate(fg, now())) {
            is Decision.Blocked -> d.primary.target
            is Decision.SoftGate -> d.primary.target
            else -> return false
        }
        // If Chrome itself is blocked too, a new tab wouldn't help.
        return target is Target.Website && engine.evaluate(Foreground(CHROME_PACKAGE), now()) == Decision.Allow
    }

    private fun openSafeTab(): Boolean {
        val host = Uri.parse(SAFE_TAB_URL).host
        if (engine.evaluate(Foreground(CHROME_PACKAGE, host), now()) != Decision.Allow) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(SAFE_TAB_URL)).apply {
            setPackage(CHROME_PACKAGE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Chrome reuses one tab per application id, so repeated exits don't pile up tabs.
            putExtra(Browser.EXTRA_APPLICATION_ID, service.packageName)
        }
        return try {
            service.startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Couldn't open a new Chrome tab", e)
            false
        }
    }

    private fun handle(decision: Decision) {
        val now = now()
        store.update { if (it.usage == engine.usage) it else it.copy(usage = engine.usage) }
        when (decision) {
            Decision.Allow, is Decision.InSession -> overlay.hide()
            is Decision.Blocked, is Decision.SoftGate -> {
                val model = toModel(decision)
                if (model == null) overlay.hide() else overlay.show(model)
            }
        }
        scheduleNext(now)
    }

    private fun scheduleNext(now: Instant) {
        handler.removeCallbacks(tickRunnable)
        val next = listOfNotNull(engine.nextEvaluationAt(now), StateActions.nextPendingAt(store.state.value)).minOrNull()
        // Cap so a missed boundary (e.g. clock change) is caught within a minute.
        val delayMs = (next?.let { Duration.between(now, it).toMillis() + 50 } ?: MAX_TICK_MS).coerceIn(0, MAX_TICK_MS)
        handler.postDelayed(tickRunnable, delayMs)
    }

    private fun toModel(decision: Decision): BlockScreenModel? {
        val state = store.state.value
        val fg = engine.foreground ?: return null
        return when (decision) {
            is Decision.Blocked -> {
                val set = state.setById(decision.primary.setId) ?: return null
                BlockScreenModel(
                    resetKey = "blocked:${fg}",
                    setName = set.name,
                    colorArgb = set.colorArgb,
                    targetLabel = label(decision.primary.target, fg),
                    message = set.message,
                    streakDays = state.streakOf(set.id).days,
                    reasons = decision.blockers.map { it.describe() }.distinct(),
                    soft = null,
                    leaveLabel = leaveLabel(fg),
                )
            }
            is Decision.SoftGate -> {
                val gate = decision.primary
                val set = state.setById(gate.setId) ?: return null
                BlockScreenModel(
                    resetKey = "gate:${fg}:${decision.opens?.used}",
                    setName = set.name,
                    colorArgb = set.colorArgb,
                    targetLabel = label(gate.target, fg),
                    message = set.message,
                    streakDays = state.streakOf(set.id).days,
                    reasons = decision.gates.map { it.setName }.distinct().let { names ->
                        if (names.size > 1) listOf("Soft blocks: ${names.joinToString(", ")}") else emptyList()
                    },
                    soft = SoftGateInfo(
                        unlockDelay = decision.unlockDelay,
                        opensUsed = decision.opens?.used ?: gate.opensUsed,
                        maxOpens = decision.opens?.max,
                        limitsSummary = limitsSummary(set),
                    ),
                    leaveLabel = leaveLabel(fg),
                )
            }
            else -> null
        }
    }

    private fun leaveLabel(fg: Foreground) = if (isWebsiteBlock(fg)) "Open a new tab" else "Go to home screen"

    private fun label(target: Target, fg: Foreground): String = when (target) {
        is Target.Website -> fg.host ?: target.domain
        is Target.App -> appLabel(target.packageName)
    }

    private fun appLabel(packageName: String): String = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        Log.w(TAG, "No label for $packageName", e)
        packageName
    }

    private fun now(): Instant = Instant.now()

    companion object {
        private const val MAX_TICK_MS = 60_000L
        private const val SAFE_TAB_URL = "https://www.google.com/"

        fun limitsSummary(set: BlockSet): String = limitsSummary(set.softLimits)

        fun limitsSummary(l: SoftLimits): String = listOfNotNull(
            l.perOpenLimit?.let { "${formatDuration(it)} per open" },
            l.totalLimit?.let { "${formatDuration(it)} total" },
        ).joinToString(" · ")
    }
}
