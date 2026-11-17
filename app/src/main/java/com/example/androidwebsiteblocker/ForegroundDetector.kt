package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.androidwebsiteblocker.blocking.BlockOverlay
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.hostFromAddressBar

const val CHROME_PACKAGE = "com.android.chrome"
private const val CHROME_URL_BAR_ID = "com.android.chrome:id/url_bar"

/**
 * Works out what the user is actually looking at.
 *
 * Uses the window list rather than event package names: only TYPE_APPLICATION windows count, so
 * the notification shade (System UI), keyboard, and our own block overlay never change the
 * foreground. Screen off or locked → null.
 */
class ForegroundDetector(private val service: AccessibilityService) {
    private val power = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)

    /**
     * Last site seen in Chrome. Kept while Chrome hides its toolbar (fullscreen video, scrolling)
     * or while the address bar is being edited, so those don't look like leaving the site.
     */
    private var lastChromeHost: String? = null

    fun detect(): Foreground? {
        if (!power.isInteractive || keyguard.isKeyguardLocked) return null
        val appWindows = service.windows.filter {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.title?.toString() != BlockOverlay.OVERLAY_WINDOW_TITLE
        }
        val window = appWindows.firstOrNull { it.isFocused }
            ?: appWindows.firstOrNull { it.isActive }
            ?: appWindows.maxByOrNull { it.layer }
            ?: return null
        val root = window.root ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val host = if (pkg == CHROME_PACKAGE) chromeHost(root) else null
        return Foreground(pkg, host)
    }

    private fun chromeHost(root: AccessibilityNodeInfo): String? {
        val urlBar = root.findAccessibilityNodeInfosByViewId(CHROME_URL_BAR_ID).firstOrNull()
        if (urlBar != null && !urlBar.isFocused) {
            val text = urlBar.text?.toString().orEmpty()
            // Non-URL text (e.g. "Search or type URL" on the new tab page) means no site.
            lastChromeHost = hostFromAddressBar(text)
        }
        return lastChromeHost
    }
}
