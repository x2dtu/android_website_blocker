package com.example.androidwebsiteblocker.core.model

/**
 * Extracts the lowercase host from browser address bar text, e.g.
 * "https://www.Reddit.com/r/all?x=1" → "www.reddit.com", "reddit.com" → "reddit.com".
 * Returns null for text that isn't a URL (such as a search being typed).
 */
fun hostFromAddressBar(text: String): String? {
    val t = text.trim().lowercase()
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    val host = t.substringAfter("://", t)
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
        .substringBefore(':')
        .trimEnd('.')
    if (!host.contains('.') || host.startsWith('.')) return null
    if (!host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
    return host
}

/**
 * Like [hostFromAddressBar], but also finds the site inside a spoken-style label, e.g. Firefox's
 * address bar description " hltv.org. Search or enter address" → "hltv.org".
 */
fun hostFromAddressLabel(text: String): String? =
    hostFromAddressBar(text) ?: text.trim().split(Regex("\\s+")).firstNotNullOfOrNull(::hostFromAddressBar)

/** Normalizes user-entered website text to a domain for a [Target.Website], or null if invalid. Drops a leading "www.". */
fun domainFromUserInput(text: String): String? = hostFromAddressBar(text)?.removePrefix("www.")
