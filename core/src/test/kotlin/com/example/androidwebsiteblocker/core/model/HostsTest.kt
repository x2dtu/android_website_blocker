package com.example.androidwebsiteblocker.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostsTest {
    @Test fun `extracts host from address bar text`() {
        assertEquals("reddit.com", hostFromAddressBar("reddit.com"))
        assertEquals("www.reddit.com", hostFromAddressBar("https://www.Reddit.com/r/all?x=1#top"))
        assertEquals("old.reddit.com", hostFromAddressBar("old.reddit.com/r/android"))
        assertEquals("localhost.test", hostFromAddressBar("http://user@localhost.test:8080/x"))
        assertEquals("example.com", hostFromAddressBar("  example.com.  "))
    }

    @Test fun `rejects non-urls`() {
        assertNull(hostFromAddressBar("how to sleep better"))
        assertNull(hostFromAddressBar("reddit"))
        assertNull(hostFromAddressBar(""))
        assertNull(hostFromAddressBar("chrome://newtab"))
    }

    @Test fun `finds the site inside an address bar description`() {
        assertEquals("hltv.org", hostFromAddressLabel(" hltv.org. Search or enter address"))
        assertEquals("old.reddit.com", hostFromAddressLabel("old.reddit.com/r/android"))
        assertNull(hostFromAddressLabel("Search or enter address"))
        assertNull(hostFromAddressLabel("Non-private Tabs Open: 2. Tap to switch tabs."))
    }

    @Test fun `user input drops www`() {
        assertEquals("reddit.com", domainFromUserInput("https://www.reddit.com/"))
        assertEquals("m.youtube.com", domainFromUserInput("m.youtube.com"))
    }
}
