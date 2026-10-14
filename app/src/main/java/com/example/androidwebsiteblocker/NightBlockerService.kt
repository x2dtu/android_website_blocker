package com.example.androidwebsiteblocker

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

const val TAG = "NightBlocker"

private const val CHROME_PACKAGE = "com.android.chrome"
private const val URL_BAR_ID = "com.android.chrome:id/url_bar"

// Set to false once the url_bar lookup is confirmed to work on your device.
private const val DUMP_TREE_WHEN_URL_BAR_MISSING = true
private const val DUMP_INTERVAL_MS = 10_000L

/**
 * Accessibility services are long-lived components the system binds to once the user enables
 * them. Android calls [onAccessibilityEvent] for every UI event in the packages listed in
 * res/xml/accessibility_service_config.xml (just Chrome here).
 */
class NightBlockerService : AccessibilityService() {

    private var lastUrl: String? = null
    private var lastDumpAt = 0L

    override fun onServiceConnected() {
        Log.i(TAG, "Service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != CHROME_PACKAGE) return
        // rootInActiveWindow can briefly point at another app's window while switching apps.
        val root = rootInActiveWindow ?: return
        if (root.packageName != CHROME_PACKAGE) return

        val urlBar = root.findAccessibilityNodeInfosByViewId(URL_BAR_ID).firstOrNull()
        if (urlBar == null) {
            maybeDumpTree(root)
            return
        }
        // While the address bar is focused, its text is whatever the user is typing.
        if (urlBar.isFocused) return
        val raw = urlBar.text?.toString().orEmpty()
        if (raw.isBlank()) return

        val url = normalizeUrl(raw)
        if (url == lastUrl) return
        lastUrl = url
        Log.i(TAG, "URL: $url (raw: $raw)")
    }

    override fun onInterrupt() {
        Log.i(TAG, "Service interrupted")
    }

    private fun maybeDumpTree(root: AccessibilityNodeInfo) {
        if (!DUMP_TREE_WHEN_URL_BAR_MISSING) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastDumpAt < DUMP_INTERVAL_MS) return
        lastDumpAt = now
        Log.d(TAG, "url_bar not found; dumping node tree")
        dumpNode(root, depth = 0)
    }

    private fun dumpNode(node: AccessibilityNodeInfo, depth: Int) {
        Log.d(
            TAG,
            "  ".repeat(depth) +
                "${node.className} id=${node.viewIdResourceName} text=${node.text} " +
                "desc=${node.contentDescription} focused=${node.isFocused}"
        )
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, depth + 1) }
        }
    }
}
