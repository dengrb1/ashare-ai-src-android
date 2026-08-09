package com.ashareai.app.standalone.alerts

import com.ashareai.app.standalone.domain.AlertKind
import com.ashareai.app.standalone.domain.AlertRule
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertEvaluatorTest {
    @Test
    fun stopLossUsesAtrAndClampsAtFiveToTenPercent() {
        val evaluator = AlertEvaluator()

        assertEquals(95.0, evaluator.stopLossPrice(100.0, 1.0), 0.001)
        assertEquals(90.0, evaluator.stopLossPrice(100.0, 8.0), 0.001)
        assertEquals(92.0, evaluator.stopLossPrice(100.0, null), 0.001)
    }

    @Test
    fun cooldownPreventsDuplicateAlert() {
        val now = 1_000_000L
        val evaluator = AlertEvaluator { now }
        val rule = AlertRule(
            id = "stop",
            symbol = "600519",
            name = "贵州茅台",
            kind = AlertKind.STOP_LOSS,
            lowerBound = 100.0,
            upperBound = null,
            enabled = true,
            expiresAt = null,
            cooldownMinutes = 30,
            lastTriggeredAt = now - 5 * 60_000L,
            configJson = "",
        )

        val events = evaluator.evaluate(listOf(rule), listOf(quote("600519", 99.0)))

        assertTrue(events.isEmpty())
    }

    @Test
    fun buyZoneHonorsBoundsAndExpiry() {
        val now = 10_000L
        val evaluator = AlertEvaluator { now }
        val active = AlertRule(
            id = "buy",
            symbol = "000001",
            name = "平安银行",
            kind = AlertKind.BUY_ZONE,
            lowerBound = 10.0,
            upperBound = 11.0,
            enabled = true,
            expiresAt = now + 1,
            cooldownMinutes = 1,
            lastTriggeredAt = null,
            configJson = "",
        )

        assertEquals(1, evaluator.evaluate(listOf(active), listOf(quote("000001", 10.5))).size)
        assertTrue(evaluator.evaluate(listOf(active.copy(expiresAt = now)), listOf(quote("000001", 10.5))).isEmpty())
    }

    private fun quote(symbol: String, price: Double) = MarketQuote(
        symbol = symbol,
        name = symbol,
        lastPrice = price,
        previousClose = price,
        changePercent = 0.0,
        volume = null,
        provider = "test",
        fetchedAt = 0,
        freshness = MarketFreshness.FRESH,
    )
}
