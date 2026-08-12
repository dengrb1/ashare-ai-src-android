package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketIndexContextTest {
    @Test
    fun riskOffMarketReducesScoreAndRiskMultiplier() {
        val context = MarketIndexContextAnalyzer.analyze(indexCandles { day -> 120.0 - day * 1.0 })

        assertEquals(MarketRegime.RISK_OFF, context.regime)
        assertTrue(context.scoreAdjustment < 0.0)
        assertTrue(context.riskMultiplier < 1.0)
    }

    @Test
    fun riskOnMarketRaisesScoreWithoutExtraRiskPenalty() {
        val context = MarketIndexContextAnalyzer.analyze(indexCandles { day -> 100.0 + day * 1.0 })

        assertEquals(MarketRegime.RISK_ON, context.regime)
        assertTrue(context.scoreAdjustment > 0.0)
        assertEquals(1.0, context.riskMultiplier, 0.0)
    }

    @Test
    fun insufficientHistoryFallsBackToNeutralAdjustment() {
        val context = MarketIndexContextAnalyzer.analyze(indexCandles(count = 20) { day -> 100.0 + day })

        assertEquals(MarketRegime.UNKNOWN, context.regime)
        assertEquals(0.0, context.scoreAdjustment, 0.0)
        assertEquals(1.0, context.riskMultiplier, 0.0)
    }

    private fun indexCandles(
        count: Int = 30,
        close: (Int) -> Double,
    ): Map<String, List<DailyCandle>> = MarketIndexContextAnalyzer.specs.associate { spec ->
        spec.symbol to (0 until count).map { day ->
            val value = close(day)
            DailyCandle(
                symbol = spec.symbol,
                tradingDate = LocalDate.of(2026, 1, 1).plusDays(day.toLong()),
                open = value,
                close = value,
                high = value,
                low = value,
                volume = null,
                provider = "test",
                fetchedAt = 1L,
            )
        }
    }
}
