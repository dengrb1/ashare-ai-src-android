package com.ashareai.app.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandNotificationSpecTest {
    @Test
    fun `normalizes fields for monitor alert and research notifications`() {
        val monitor = spec(title = "持仓盈亏", content = "+12.50", subContent = "+1.20% · 3只持仓").normalized()
        val alert = spec(title = "止损预警", content = "价格跌破止损线", enableFloat = true).normalized()
        val research = spec(title = "每日研究", content = "72%", subContent = "生成报告中").normalized()

        assertEquals("持仓盈亏", monitor.title)
        assertTrue(alert.enableFloat)
        assertEquals("生成报告中", research.subContent)
    }

    @Test
    fun `truncates text and clamps timeout bounds`() {
        val normalized = spec(
            title = "t".repeat(41),
            content = "c".repeat(81),
            subContent = "s".repeat(81),
            timeoutMinutes = 0,
            islandTimeoutSeconds = 9_999,
        ).normalized()

        assertEquals(40, normalized.title.length)
        assertEquals(80, normalized.content.length)
        assertEquals(80, normalized.subContent?.length)
        assertEquals(1, normalized.timeoutMinutes)
        assertEquals(3_600, normalized.islandTimeoutSeconds)
    }

    @Test
    fun `capability requires protocol v3`() {
        assertFalse(FocusCapabilities(2, false).superIslandReady)
        assertTrue(FocusCapabilities(3, true).superIslandReady)
        assertTrue(FocusCapabilities(0, false).v3PayloadAttached)
    }

    private fun spec(
        title: String = "标题",
        content: String = "内容",
        subContent: String? = null,
        enableFloat: Boolean = false,
        timeoutMinutes: Int = 120,
        islandTimeoutSeconds: Int = 600,
    ) = IslandNotificationSpec(
        title = title,
        content = content,
        subContent = subContent,
        colorContent = null,
        ticker = title,
        enableFloat = enableFloat,
        timeoutMinutes = timeoutMinutes,
        islandTimeoutSeconds = islandTimeoutSeconds,
    )
}
