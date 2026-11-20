package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.example.androidwebsiteblocker.blocking.BlockOverlay
import com.example.androidwebsiteblocker.core.model.Foreground
import com.example.androidwebsiteblocker.core.model.hostFromAddressBar
import com.example.androidwebsiteblocker.core.model.hostFromAddressLabel

/**
 * Works out what the user is actually looking at.
 *
 * Uses the window list rather than event package names: only TYPE_APPLICATION windows count, so
 * the notification shade (System UI), keyboard, and our own block overlay never change the
 * foreground. Screen off or locked → null. For supported [Browsers], also reads the site.
 */
class ForegroundDetector(private val service: AccessibilityService) {
    private val power = service.getSystemService(PowerManager::class.java)
    private val keyguard = service.getSystemService(KeyguardManager::class.java)

    /**
     * Last site seen per browser. Kept while a browser hides its toolbar (fullscreen video,
     * scrolling) or while the address bar is being edited, so those don't look like leaving.
     */
    private val lastHost = mutableMapOf<String, String?>()

    /** Screen on and unlocked. */
    fun screenUsable(): Boolean = power.isInteractive && !keyguard.isKeyguardLocked

    fun detect(): Foreground? {
        if (!screenUsable()) return null
        val root = activeAppRoot() ?: return null
        val pkg = root.packageName?.toString() ?: return null
        val host = Browsers.forPackage(pkg)?.let { browserHost(root, it) }
        return Foreground(pkg, host)
    }

    /** Root node of the app window the user is using (not System UI, keyboard, or our overlays). */
    fun activeAppRoot(): AccessibilityNodeInfo? {
        val appWindows = service.windows.filter {
            it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.title?.toString() != BlockOverlay.OVERLAY_WINDOW_TITLE
        }
        val window = appWindows.firstOrNull { it.isFocused }
            ?: appWindows.firstOrNull { it.isActive }
            ?: appWindows.maxByOrNull { it.layer }
            ?: return null
        return window.root
    }

    private fun browserHost(root: AccessibilityNodeInfo, browser: Browser): String? {
        val urlBar = browser.urlBarIds.firstNotNullOfOrNull { id -> root.findAccessibilityNodeInfosByViewId(id).firstOrNull() }
            ?: findAddressField(root, browser.packageName)
        if (urlBar != null && !urlBar.isFocused) {
            // Non-URL text (e.g. "Search or type URL" on a new tab page) means no site.
            lastHost[browser.packageName] = addressOf(urlBar)
        }
        return lastHost[browser.packageName]
    }

    /**
     * Fallback when no known address bar ID matches: a site address in the browser's own toolbar
     * (or any address-like text field), never in the web page or the suggestions list.
     *
     * Handles toolbars built with Jetpack Compose (e.g. newer Firefox), whose element names have
     * no package prefix ("ADDRESSBAR_SEARCH_BOX", "composable_toolbar").
     */
    private fun findAddressField(root: AccessibilityNodeInfo, pkg: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Boolean>>().apply { add(root to false) }
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_SCAN_NODES) {
            val (node, inToolbar) = queue.removeFirst()
            visited++
            val cls = node.className?.toString().orEmpty()
            if (cls.contains("WebView") || cls.contains("GeckoView")) continue // page content
            val id = node.viewIdResourceName?.lowercase().orEmpty()
            if (SKIP_HINTS.any(id::contains)) continue // suggestions list shows other sites' URLs
            val isToolbar = inToolbar || TOOLBAR_HINTS.any(id::contains)
            val ownUi = id.isEmpty() || id.startsWith("${pkg.lowercase()}:id/") || !id.contains(":id/")
            if (ownUi && (isToolbar || cls.endsWith("EditText")) && addressOf(node) != null) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it to isToolbar) }
        }
        return null
    }

    /**
     * The site shown by an address bar element: its text, or — for toolbars like Firefox's that
     * leave the text empty — its description (" hltv.org. Search or enter address").
     */
    private fun addressOf(node: AccessibilityNodeInfo): String? =
        node.text?.toString()?.let(::hostFromAddressBar)
            ?: node.contentDescription?.toString()?.let(::hostFromAddressLabel)

    private companion object {
        const val MAX_SCAN_NODES = 400
        val TOOLBAR_HINTS = listOf("toolbar", "addressbar", "address_bar", "url", "omnibox", "location_bar")
        val SKIP_HINTS = listOf("awesomebar", "suggestion")
    }
}
