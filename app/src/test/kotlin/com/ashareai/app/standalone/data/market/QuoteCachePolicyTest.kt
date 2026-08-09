package com.ashareai.app.standalone.data.market

import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteCachePolicyTest {
    @Test
    fun fallsBackToStaleCacheAndMarksUnavailableSymbols() {
        val cached = quote("600519", 1500.0, 10L)

        val values = QuoteCachePolicy.merge(
            requested = listOf("600519", "000001"),
            fresh = emptyList(),
            cached = mapOf(cached.symbol to cached),
            provider = "test",
            now = 100L,
        )

        assertEquals(MarketFreshness.STALE, values[0].freshness)
        assertEquals(1500.0, values[0].lastPrice ?: 0.0, 0.001)
        assertEquals(MarketFreshness.UNAVAILABLE, values[1].freshness)
        assertTrue(values[1].lastPrice == null)
    }

    @Test
    fun determinesStalenessFromConfiguredAge() {
        val quote = quote("600519", 1500.0, 100L)

        assertFalse(QuoteCachePolicy.isStale(quote, now = 190L, maximumAgeMillis = 100L))
        assertTrue(QuoteCachePolicy.isStale(quote, now = 201L, maximumAgeMillis = 100L))
    }

    private fun quote(symbol: String, price: Double, fetchedAt: Long) = MarketQuote(
        symbol = symbol,
        name = symbol,
        lastPrice = price,
        previousClose = price,
        changePercent = 0.0,
        volume = null,
        provider = "test",
        fetchedAt = fetchedAt,
        freshness = MarketFreshness.FRESH,
    )
}
