package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.ResearchScore
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchAdviceTest {
    @Test
    fun eligibleStockProducesBoardLotPlanWithinBudget() {
        val advice = ResearchAdviceEngine.buildBuyAdvice(
            result = result(score = 82.0, price = 10.0),
            candles = candles(),
            availableBudget = 50_000.0,
            perSymbolBudget = 12_000.0,
            maxStockPrice = 20.0,
        )

        assertEquals(BuyAdviceAction.BUY, advice.action)
        assertTrue(advice.quantity >= 100)
        assertEquals(0, advice.quantity % 100)
        assertTrue(advice.plannedAmount <= 12_000.0)
        assertTrue(requireNotNull(advice.stopLoss) < requireNotNull(advice.entryHigh))
        assertTrue(requireNotNull(advice.takeProfit) > requireNotNull(advice.entryHigh))
    }

    @Test
    fun staleQuoteCannotProduceBuyAdvice() {
        val stale = result(score = 82.0, price = 10.0).copy(
            quote = result(82.0, 10.0).quote?.copy(freshness = MarketFreshness.STALE),
        )

        val advice = ResearchAdviceEngine.buildBuyAdvice(stale, candles(), 50_000.0, 12_000.0, null)

        assertEquals(BuyAdviceAction.WATCH, advice.action)
        assertEquals(0, advice.quantity)
        assertTrue(advice.reasons.any { it.contains("新鲜报价") })
    }

    @Test
    fun plansStopAllocatingAfterTotalBudgetIsConsumed() {
        val ranked = listOf(
            result(90.0, 10.0) to candles(),
            result(88.0, 10.0, "000002") to candles("000002"),
        )

        val advices = ResearchAdviceEngine.buildBuyAdvices(ranked, 10_500.0, 10_500.0, null)

        assertEquals(BuyAdviceAction.BUY, advices.first().action)
        assertTrue(advices.last().action != BuyAdviceAction.BUY)
    }

    private fun result(score: Double, price: Double, symbol: String = "000001") = ResearchResult(
        symbol = symbol,
        name = symbol,
        score = ResearchScore(
            trend = 20.0,
            movingAverage = 15.0,
            macd = 15.0,
            rsi = 10.0,
            atr = 0.5,
            volatility = 0.2,
            volume = 1.2,
            freshness = 5.0,
            total = score,
            unavailable = emptyList(),
        ),
        risk = "中",
        summary = "test",
        quote = MarketQuote(symbol, symbol, price, price - 0.1, 1.0, 1_000.0, "test", 1L, MarketFreshness.FRESH),
    )

    private fun candles(symbol: String = "000001") = (0 until 80).map { index ->
        val price = 8.0 + index * 0.02
        DailyCandle(
            symbol,
            LocalDate.of(2026, 1, 1).plusDays(index.toLong()),
            price,
            price + 0.1,
            price + 0.3,
            price - 0.2,
            1_000.0,
            "test",
            1L,
        )
    }
}
