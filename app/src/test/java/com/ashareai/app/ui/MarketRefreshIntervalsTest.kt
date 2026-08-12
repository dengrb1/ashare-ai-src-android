package com.ashareai.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRefreshIntervalsTest {
    @Test
    fun matchesWebOptions() {
        assertEquals(listOf(5, 10, 15, 30, 60, 120), MarketRefreshIntervals.OPTIONS)
    }

    @Test
    fun normalizesUnknownValuesToDefault() {
        assertEquals(MarketRefreshIntervals.DEFAULT_SECONDS, MarketRefreshIntervals.normalize(20))
        assertTrue(MarketRefreshIntervals.OPTIONS.all { MarketRefreshIntervals.normalize(it) == it })
    }
}
