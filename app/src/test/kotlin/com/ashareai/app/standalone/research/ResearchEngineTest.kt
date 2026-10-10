package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchEngineTest {
    @Test
    fun reportsUnavailableFundamentalAndEventDataInsteadOfFabricatingScores() {
        val candles = (0 until 80).map { day ->
            val close = 10.0 + day * 0.1
            DailyCandle(
                symbol = "000001",
                tradingDate = LocalDate.of(2026, 1, 1).plusDays(day.toLong()),
                open = close - 0.1,
                close = close,
                high = close + 0.2,
                low = close - 0.2,
                volume = 1_000.0 + day,
                provider = "test",
                fetchedAt = 1L,
            )
        }

        val result = DeterministicResearchEngine().analyse(
            symbol = "000001",
            name = "平安银行",
            quote = MarketQuote(
                symbol = "000001",
                name = "平安银行",
                lastPrice = 18.0,
                previousClose = 17.9,
                changePercent = 0.5,
                volume = 1_000.0,
                provider = "test",
                fetchedAt = 1L,
                freshness = MarketFreshness.FRESH,
            ),
            candles = candles,
        )

        assertTrue(result.score.total in 0.0..100.0)
        assertTrue("neutral missing evidence must not force a sub-50 score", result.score.total >= 50.0)
        assertTrue(result.score.unavailable.any { it.startsWith("基本面") })
        assertTrue(result.score.unavailable.any { it.startsWith("事件数据") })
        assertTrue(result.score.factorDecision != null)
        assertTrue(result.score.factorFormulaVersion == "factor-v1.0.0")
        assertTrue(result.score.factorParameterSha256?.length == 64)
    }
}
