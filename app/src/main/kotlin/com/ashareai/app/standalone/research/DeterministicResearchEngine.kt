package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.ResearchScore
import kotlin.math.roundToInt

interface ResearchEngine {
    fun analyse(
        symbol: String,
        name: String,
        quote: MarketQuote?,
        candles: List<DailyCandle>,
        marketContext: MarketIndexContext = MarketIndexContext.unknown(),
    ): ResearchResult
}

class DeterministicResearchEngine : ResearchEngine {
    fun analyse(
        symbol: String,
        name: String,
        quote: MarketQuote?,
        candles: List<DailyCandle>,
    ): ResearchResult = analyse(symbol, name, quote, candles, MarketIndexContext.unknown())

    override fun analyse(
        symbol: String,
        name: String,
        quote: MarketQuote?,
        candles: List<DailyCandle>,
        marketContext: MarketIndexContext,
    ): ResearchResult {
        val closes = candles.map(DailyCandle::close)
        val unavailable = mutableListOf<String>()
        val sma20 = TechnicalIndicators.sma(closes, 20)
        val sma60 = TechnicalIndicators.sma(closes, 60)
        val macd = TechnicalIndicators.macd(closes)
        val rsi = TechnicalIndicators.rsi(closes)
        val atr = TechnicalIndicators.atr(candles)
        val volatility = TechnicalIndicators.volatility(closes)
        val volumeRatio = TechnicalIndicators.volumeRatio(candles)
        val lastClose = closes.lastOrNull()

        val trendScore = when {
            lastClose == null || sma60 == null -> {
                unavailable += "趋势（K 线不足 60 日）"
                null
            }
            lastClose >= sma60 -> 20.0
            else -> 5.0
        }
        val movingAverageScore = when {
            sma20 == null || sma60 == null -> {
                unavailable += "均线（K 线不足 60 日）"
                null
            }
            sma20 >= sma60 -> 15.0
            else -> 4.0
        }
        val macdScore = when {
            macd == null -> {
                unavailable += "MACD（K 线不足）"
                null
            }
            macd.macd > macd.signal && macd.histogram > 0 -> 15.0
            macd.macd > macd.signal -> 10.0
            else -> 3.0
        }
        val rsiScore = when {
            rsi == null -> {
                unavailable += "RSI（K 线不足）"
                null
            }
            rsi in 45.0..70.0 -> 10.0
            rsi in 35.0..80.0 -> 6.0
            else -> 2.0
        }
        val atrScore = when {
            atr == null || lastClose == null || lastClose == 0.0 -> {
                unavailable += "ATR（K 线不足）"
                null
            }
            atr / lastClose <= 0.03 -> 10.0
            atr / lastClose <= 0.06 -> 6.0
            else -> 2.0
        }
        val volatilityScore = when {
            volatility == null -> {
                unavailable += "波动率（K 线不足）"
                null
            }
            volatility <= 0.30 -> 10.0
            volatility <= 0.55 -> 6.0
            else -> 2.0
        }
        val volumeScore = when {
            volumeRatio == null -> {
                unavailable += "成交量（数据不足）"
                null
            }
            volumeRatio in 1.0..2.5 -> 10.0
            volumeRatio >= 0.7 -> 6.0
            else -> 3.0
        }
        val freshnessScore = when (quote?.freshness) {
            MarketFreshness.FRESH -> 5.0
            MarketFreshness.STALE -> 2.0
            else -> {
                unavailable += "行情新鲜度（无可用报价）"
                0.0
            }
        }

        // Basic-event and fundamental data are intentionally not inferred from a price feed.
        unavailable += "基本面（本地行情源不可用）"
        unavailable += "事件数据（本地行情源不可用）"

        val components = listOf(
            trendScore,
            movingAverageScore,
            macdScore,
            rsiScore,
            atrScore,
            volatilityScore,
            volumeScore,
            freshnessScore,
        )
        val availableTotal = components.filterNotNull().sum()
        val availableMaximum = listOf(20.0, 15.0, 15.0, 10.0, 10.0, 10.0, 10.0, 5.0)
            .zip(components)
            .filter { it.second != null }
            .sumOf { it.first }
        val normalizedTotal = if (availableMaximum == 0.0) 0.0 else availableTotal / availableMaximum * 100
        val baseTotal = (normalizedTotal * 10).roundToInt() / 10.0
        if (marketContext.regime == MarketRegime.UNKNOWN) {
            unavailable += "大盘指数（K 线不足 20 日，按中性处理）"
        }
        val total = (((baseTotal + marketContext.scoreAdjustment).coerceIn(0.0, 100.0) * marketContext.riskMultiplier) * 10)
            .roundToInt() / 10.0
        val risk = when {
            quote?.freshness == MarketFreshness.UNAVAILABLE -> "数据不足"
            total >= 70 && (volatility ?: 1.0) < 0.55 -> "中"
            total >= 45 -> "中高"
            else -> "高"
        }
        val summary = buildString {
            append(name)
            append(" 本地技术评分 ")
            append(total)
            append(" / 100；风险：")
            append(risk)
            append("；大盘：")
            append(marketRegimeLabel(marketContext.regime))
            append("（调整 ")
            append(formatSigned(marketContext.scoreAdjustment))
            append("，风险乘数 ")
            append(marketContext.riskMultiplier)
            append("）")
            if (quote?.freshness == MarketFreshness.STALE) append("；报价为陈旧缓存")
            if (unavailable.isNotEmpty()) {
                append("；不可用：")
                append(unavailable.joinToString("、"))
            }
        }
        return ResearchResult(
            symbol = symbol,
            name = name,
            score = ResearchScore(
                trend = trendScore,
                movingAverage = movingAverageScore,
                macd = macdScore,
                rsi = rsiScore,
                atr = atr,
                volatility = volatility,
                volume = volumeRatio,
                freshness = freshnessScore,
                baseTotal = baseTotal,
                marketContext = marketContext,
                total = total,
                unavailable = unavailable.distinct(),
            ),
            risk = risk,
            summary = summary,
            quote = quote,
        )
    }

    private fun marketRegimeLabel(regime: MarketRegime) = when (regime) {
        MarketRegime.RISK_ON -> "风险偏好改善"
        MarketRegime.RISK_OFF -> "风险偏好收缩"
        MarketRegime.NEUTRAL -> "大盘中性"
        MarketRegime.UNKNOWN -> "大盘数据不足"
    }

    private fun formatSigned(value: Double): String = if (value >= 0) "+$value" else value.toString()
}
