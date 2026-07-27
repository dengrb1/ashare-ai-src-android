package com.ashareai.app.ui.components

import com.ashareai.app.data.KlinePeriod
import org.junit.Assert.assertEquals
import org.junit.Test

class ChartViewportTest {
    @Test
    fun rangeAndPanAreAnchoredFromLatest() {
        val viewport = ChartViewport(visibleCount = 80).pan(total = 300, bars = 25)
        assertEquals(195 until 275, viewport.range(300))
        assertEquals(25, viewport.offsetFromLatest)
    }

    @Test
    fun zoomClampsToSupportedWindow() {
        assertEquals(40, ChartViewport(80).zoom(300, 2f).visibleCount)
        assertEquals(20, ChartViewport(20).zoom(300, 3f).visibleCount)
        assertEquals(30, ChartViewport(80).normalized(30).visibleCount)
    }

    @Test
    fun intradayTimeUsesShanghaiTimezoneAndKeepsMinutes() {
        assertEquals(
            "2026-07-27 09:30",
            formatKlineTime("2026-07-27T01:30:00Z", KlinePeriod.MINUTE_5, detailed = true),
        )
        assertEquals(
            "07-27 09:30",
            formatKlineTime("2026-07-27T01:30:00Z", KlinePeriod.MINUTE_5, detailed = false),
        )
    }
}
