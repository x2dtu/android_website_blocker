package com.example.androidwebsiteblocker

/**
 * Browsers whose address bar the app can read, so website rules (site blocks, adult filter,
 * soft limits, reminders, "Open a new tab") work in them.
 *
 * [urlBarIds] are the address bar's view IDs. Most Chromium-based browsers reuse Chrome's
 * toolbar, so they share Chrome's `url_bar` ID under their own package. If none is found (e.g. a
 * redesigned toolbar), [ForegroundDetector] falls back to scanning the browser's own UI for an
 * address-like text field. Use Detection log → "Inspect browser" to check a browser on a device.
 */
data class Browser(val packageName: String, val name: String, val urlBarIds: List<String>)

object Browsers {
    private fun chromium(pkg: String, name: String) = Browser(pkg, name, listOf("$pkg:id/url_bar"))

    val all: List<Browser> = listOf(
        chromium("com.android.chrome", "Chrome"),
        chromium("com.chrome.beta", "Chrome Beta"),
        chromium("com.chrome.dev", "Chrome Dev"),
        chromium("com.chrome.canary", "Chrome Canary"),
        chromium("com.brave.browser", "Brave"),
        chromium("com.brave.browser_beta", "Brave Beta"),
        chromium("com.brave.browser_nightly", "Brave Nightly"),
        chromium("com.microsoft.emmx", "Microsoft Edge"),
        chromium("com.vivaldi.browser", "Vivaldi"),
        chromium("com.kiwibrowser.browser", "Kiwi"),
        Browser("com.opera.browser", "Opera", listOf("com.opera.browser:id/url_field", "com.opera.browser:id/url_bar")),
        Browser(
            "com.sec.android.app.sbrowser", "Samsung Internet",
            listOf("com.sec.android.app.sbrowser:id/location_bar_edit_text", "com.sec.android.app.sbrowser:id/url_bar"),
        ),
        Browser(
            "org.mozilla.firefox", "Firefox",
            listOf("org.mozilla.firefox:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox:id/url_bar_title"),
        ),
        Browser(
            "org.mozilla.firefox_beta", "Firefox Beta",
            listOf("org.mozilla.firefox_beta:id/mozac_browser_toolbar_url_view", "org.mozilla.firefox_beta:id/url_bar_title"),
        ),
    )

    private val byPackage = all.associateBy { it.packageName }

    fun forPackage(packageName: String?): Browser? = packageName?.let(byPackage::get)

    fun isBrowser(packageName: CharSequence?): Boolean = packageName?.toString()?.let(byPackage::containsKey) == true

    private val mainNames = listOf("Chrome", "Brave", "Firefox", "Microsoft Edge", "Samsung Internet", "Opera", "Vivaldi", "Kiwi")

    /** For info text: "Chrome, Brave, Firefox, …" (main editions only). */
    val displayNames: String = mainNames.joinToString(", ")

    /** For info dialogs: one browser per line, plus what to do about the rest. */
    val supportText: String =
        "Websites are checked in these browsers (including their Beta/Dev editions):\n" +
            mainNames.joinToString("\n") { "• $it" } +
            "\n\nOther browsers can't be checked by site. To close the gap, add them as apps in a hard block set."
}
