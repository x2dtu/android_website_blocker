package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.example.androidwebsiteblocker.blocking.BlockController
import com.example.androidwebsiteblocker.blocking.BlockScreenHost
import com.example.androidwebsiteblocker.data.AdultSiteList
import com.example.androidwebsiteblocker.data.StateStore

const val TAG = "NightBlocker"

/**
 * Long-lived component the system binds once the user enables it in Accessibility settings.
 * Receives UI events from all apps and turns them into foreground changes.
 */
class NightBlockerService : AccessibilityService() {

    private lateinit var detector: ForegroundDetector
    private var controller: BlockController? = null

    /** Screen off/on and unlock aren't accessibility events; listen for them directly. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    private val timeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            controller?.onTimeChanged()
        }
    }

    override fun onServiceConnected() {
        Log.i(TAG, "Service connected")
        detector = ForegroundDetector(this)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        val timeFilter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeReceiver, timeFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        AdultSiteList.ensureLoaded(this)
        controller = BlockController(this, StateStore.get(this)).also { it.start() }
        // If the block screen gets covered (user switched apps), re-check what's really on screen.
        BlockScreenHost.onHiddenWhileBlocking = { refresh() }
        ForegroundMonitor.setServiceConnected(true)
        // Always hand the new controller the current foreground, even if the monitor already
        // has it (service restarted while the same app stayed on screen).
        val foreground = detector.detect()
        ForegroundMonitor.report(foreground, System.currentTimeMillis())
        controller?.onForegroundChanged(foreground)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> refresh()
            // Content changes are frequent; only Chrome needs them (address bar updates).
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ->
                if (event.packageName == CHROME_PACKAGE) refresh()
        }
    }

    private fun refresh() {
        if (!::detector.isInitialized) return
        // While the block screen is up, the window list is unreliable: during the switch to it,
        // Android can briefly report no app or the launcher, which looked like the user leaving
        // and closed the block screen. Trust only screen off / lock here; actually leaving
        // (Home, Recents, another app, a call) stops the block screen, which calls refresh()
        // again via onHiddenWhileBlocking once it's no longer covering.
        if (BlockScreenHost.coversForeground() && detector.screenUsable()) return
        val foreground = detector.detect()
        if (ForegroundMonitor.report(foreground, System.currentTimeMillis())) {
            controller?.onForegroundChanged(foreground)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        controller?.onConfigurationChanged()
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        controller?.stop()
        controller = null
        if (::detector.isInitialized) {
            unregisterReceiver(screenReceiver)
            unregisterReceiver(timeReceiver)
        }
        ForegroundMonitor.setServiceConnected(false)
        super.onDestroy()
    }
}
