package com.ashareai.app.standalone.island

import org.junit.Assert.assertEquals
import org.junit.Test

class IslandNotificationSpecTest {
    @Test
    fun normalizesV3PayloadLengthsAndTimeouts() {
        val value = IslandNotificationSpec(
            title = "t".repeat(80),
            content = "c".repeat(120),
            subContent = "s".repeat(120),
            colorContent = "#f00",
            ticker = "x".repeat(80),
            enableFloat = true,
            timeoutMinutes = 0,
            islandTimeoutSeconds = 9_999,
        ).normalized()

        assertEquals(40, value.title.length)
        assertEquals(80, value.content.length)
        assertEquals(80, value.subContent?.length)
        assertEquals(30, value.ticker.length)
        assertEquals(1, value.timeoutMinutes)
        assertEquals(3_600, value.islandTimeoutSeconds)
    }
}
