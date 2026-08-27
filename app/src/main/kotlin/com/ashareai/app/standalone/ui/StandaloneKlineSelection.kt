package com.ashareai.app.standalone.ui

import com.ashareai.app.standalone.domain.DailyCandle

enum class StandaloneKlineRange(val label: String) {
    DAY_5("近5日"),
    MONTH_1("近1月"),
    MONTH_3("近3月"),
    MONTH_6("近6月"),
    YEAR_1("近1年"),
}

fun selectKlineRange(
    candles: List<DailyCandle>,
    range: StandaloneKlineRange,
): List<DailyCandle> {
    val ordered = candles
        .filter { candle ->
            candle.open.isFinite() && candle.open > 0.0 &&
                candle.close.isFinite() && candle.close > 0.0 &&
                candle.high.isFinite() && candle.low.isFinite() &&
                candle.high >= maxOf(candle.open, candle.close) &&
                candle.low <= minOf(candle.open, candle.close)
        }
        .distinctBy(DailyCandle::tradingDate)
        .sortedBy(DailyCandle::tradingDate)
    val latest = ordered.lastOrNull()?.tradingDate ?: return emptyList()
    return when (range) {
        StandaloneKlineRange.DAY_5 -> ordered.takeLast(5)
        StandaloneKlineRange.MONTH_1 -> ordered.filter { !it.tradingDate.isBefore(latest.minusMonths(1)) }
        StandaloneKlineRange.MONTH_3 -> ordered.filter { !it.tradingDate.isBefore(latest.minusMonths(3)) }
        StandaloneKlineRange.MONTH_6 -> ordered.filter { !it.tradingDate.isBefore(latest.minusMonths(6)) }
        StandaloneKlineRange.YEAR_1 -> ordered.filter { !it.tradingDate.isBefore(latest.minusYears(1)) }
    }
}
