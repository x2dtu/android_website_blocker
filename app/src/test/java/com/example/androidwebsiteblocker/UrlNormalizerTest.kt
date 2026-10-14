package com.example.androidwebsiteblocker

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlNormalizerTest {
    @Test
    fun chromeDisplayFormIsUnchanged() {
        assertEquals("reddit.com", normalizeUrl("reddit.com"))
        assertEquals("reddit.com/r/foo", normalizeUrl("reddit.com/r/foo"))
    }

    @Test
    fun stripsSchemeAndSubdomains() {
        assertEquals("reddit.com", normalizeUrl("https://www.reddit.com"))
        assertEquals("reddit.com/r/all", normalizeUrl("http://old.reddit.com/r/all"))
        assertEquals("reddit.com/r/popular", normalizeUrl("https://m.reddit.com/r/popular"))
    }

    @Test
    fun stripsTrailingSlashQueryAndFragment() {
        assertEquals("reddit.com", normalizeUrl("reddit.com/"))
        assertEquals("reddit.com/r/all", normalizeUrl("reddit.com/r/all/?sort=top#x"))
        assertEquals("reddit.com", normalizeUrl("https://www.reddit.com/?feed=home"))
    }

    @Test
    fun lowercasesAndTrims() {
        assertEquals("reddit.com/r/androiddev", normalizeUrl("  Reddit.com/R/AndroidDev  "))
    }

    @Test
    fun onlyStripsOneLeadingSubdomain() {
        assertEquals("example.com", normalizeUrl("www.example.com"))
        assertEquals("mail.example.com", normalizeUrl("mail.example.com"))
    }
}
