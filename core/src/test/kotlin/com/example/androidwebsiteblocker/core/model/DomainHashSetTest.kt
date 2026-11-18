package com.example.androidwebsiteblocker.core.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainHashSetTest {
    @After fun reset() { AdultSites.list = null }

    @Test fun `hash matches the python generator`() {
        // Values printed by tools/build_domain_list.py's fnv1a64.
        assertEquals(-3750763034362895579L, DomainHashSet.fnv1a64(""))
        assertEquals(-5808556873153909620L, DomainHashSet.fnv1a64("a"))
        assertEquals(6298361471204529350L, DomainHashSet.fnv1a64("example.com"))
        assertEquals(-1262417720040770913L, DomainHashSet.fnv1a64("old.reddit.com"))
    }

    private fun fileOf(vararg domains: String): ByteArray {
        val hashes = domains.map(DomainHashSet::fnv1a64).sorted()
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(0x4E424453); writeInt(1); writeInt(hashes.size)
            hashes.forEach(::writeLong)
        }
        return bytes.toByteArray()
    }

    @Test fun `matches listed domains and their subdomains only`() {
        val set = DomainHashSet.read(ByteArrayInputStream(fileOf("badsite.com", "naughty.co.uk")))
        assertEquals(2, set.size)
        assertTrue(set.containsHostOrParent("badsite.com"))
        assertTrue(set.containsHostOrParent("www.BadSite.com."))
        assertTrue(set.containsHostOrParent("a.b.naughty.co.uk"))
        assertFalse(set.containsHostOrParent("notbadsite.com"))
        assertFalse(set.containsHostOrParent("co.uk"))
        assertFalse(set.containsHostOrParent("com"))
        assertFalse(set.containsHostOrParent(""))
    }

    @Test fun `adult websites target uses the loaded list`() {
        val chrome = { host: String -> Foreground("com.android.chrome", host) }
        assertFalse(Target.AdultWebsites.matches(chrome("badsite.com"))) // not loaded yet
        AdultSites.list = DomainHashSet.read(ByteArrayInputStream(fileOf("badsite.com")))
        assertTrue(Target.AdultWebsites.matches(chrome("m.badsite.com")))
        assertFalse(Target.AdultWebsites.matches(chrome("google.com")))
        assertFalse(Target.AdultWebsites.matches(Foreground("com.android.chrome")))
    }

    @Test fun `bundled asset loads and matches known sites`() {
        val file = java.io.File("../app/src/main/assets/adult_domains.bin")
        if (!file.exists()) return
        val set = file.inputStream().use(DomainHashSet::read)
        assertTrue(set.size > 100_000)
        assertTrue(set.containsHostOrParent("www.pornhub.com"))
        for (ok in listOf("google.com", "reddit.com", "old.reddit.com", "tumblr.com", "x.com", "wikipedia.org", "youtube.com")) {
            assertFalse(ok, set.containsHostOrParent(ok))
        }
    }
}
