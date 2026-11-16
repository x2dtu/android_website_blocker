package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat

const val TAG = "NightBlocker"

/**
 * Long-lived component the system binds once the user enables it in Accessibility settings.
 * Receives UI events from all apps and turns them into foreground changes.
 */
class NightBlockerService : AccessibilityService() {

    private lateinit var detector: ForegroundDetector

    /** Screen off/on and unlock aren't accessibility events; listen for them directly. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
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
        ForegroundMonitor.setServiceConnected(true)
        refresh()
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
        ForegroundMonitor.report(detector.detect(), System.currentTimeMillis())
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }

    override fun onDestroy() {
        if (::detector.isInitialized) unregisterReceiver(screenReceiver)
        ForegroundMonitor.setServiceConnected(false)
        super.onDestroy()
    }
}
