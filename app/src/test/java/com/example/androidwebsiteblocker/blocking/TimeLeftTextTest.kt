package com.example.androidwebsiteblocker.blocking

import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeLeftTextTest {
    private val total = Duration.ofMinutes(20)

    @Test fun `drops seconds`() {
        assertEquals("8 of 20 min left", timeLeftText(Duration.ofSeconds(8 * 60 + 17), total))
        assertEquals("1 of 20 min left", timeLeftText(Duration.ofSeconds(60), total))
    }

    @Test fun `last minute`() {
        assertEquals("<1 min of 20 min left", timeLeftText(Duration.ofSeconds(59), total))
        assertEquals("<1 min of 20 min left", timeLeftText(Duration.ZERO, total))
    }
}
