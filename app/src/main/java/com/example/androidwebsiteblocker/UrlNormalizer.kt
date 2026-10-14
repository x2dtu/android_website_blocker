package com.example.androidwebsiteblocker

private val SUBDOMAIN_PREFIXES = listOf("www.", "m.", "old.")

/**
 * Normalizes a URL as shown in Chrome's address bar so it can be compared with exact rules.
 * "https://www.Reddit.com/r/All/?feed=home#top" -> "reddit.com/r/all"
 */
fun normalizeUrl(raw: String): String {
    var url = raw.trim().lowercase()
        .substringBefore('#')
        .substringBefore('?')
        .removePrefix("https://")
        .removePrefix("http://")
    SUBDOMAIN_PREFIXES.firstOrNull { url.startsWith(it) }?.let { url = url.removePrefix(it) }
    return url.trimEnd('/')
}
