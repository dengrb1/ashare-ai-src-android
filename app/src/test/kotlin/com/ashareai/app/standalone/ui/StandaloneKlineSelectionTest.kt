package com.ashareai.app.standalone.ui

import com.ashareai.app.standalone.domain.DailyCandle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StandaloneKlineSelectionTest {
    @Test
    fun selectsLatestFiveTradingSessions() {
        val candles = (0 until 12).map(::candle)

        val selected = selectKlineRange(candles, StandaloneKlineRange.DAY_5)

        assertEquals((7 until 12).map { BASE.plusDays(it.toLong()) }, selected.map(DailyCandle::tradingDate))
    }

    @Test
    fun monthRangeUsesLatestAvailableTradingDate() {
        val candles = listOf(0, 20, 40, 60, 80, 100).map(::candle)

        val selected = selectKlineRange(candles, StandaloneKlineRange.MONTH_3)

        assertEquals(listOf(20, 40, 60, 80, 100).map { BASE.plusDays(it.toLong()) }, selected.map(DailyCandle::tradingDate))
    }

    @Test
    fun removesMalformedCandlesBeforeDisplayingThem() {
        val valid = candle(1)
        val malformed = valid.copy(tradingDate = valid.tradingDate.plusDays(1), open = 0.0)

        assertEquals(listOf(valid), selectKlineRange(listOf(malformed, valid), StandaloneKlineRange.YEAR_1))
    }

    private fun candle(day: Int): DailyCandle {
        val price = 10.0 + day
        return DailyCandle(
            symbol = "600000",
            tradingDate = BASE.plusDays(day.toLong()),
            open = price,
            close = price + 0.2,
            high = price + 0.5,
            low = price - 0.5,
            volume = 1_000.0,
            provider = "test",
            fetchedAt = 1L,
        )
    }

    private companion object {
        val BASE: LocalDate = LocalDate.of(2026, 1, 1)
    }
}
