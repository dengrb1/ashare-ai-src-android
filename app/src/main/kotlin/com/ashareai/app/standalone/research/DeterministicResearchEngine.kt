package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.MonitoringTrigger
import com.ashareai.app.standalone.domain.RiskLevel
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.ResearchScore
import com.ashareai.app.standalone.domain.ResearchSignalSnapshot
import com.ashareai.app.standalone.domain.SignalFreshness
import com.ashareai.app.standalone.domain.TrendAnalysis
import com.ashareai.app.standalone.domain.TrendPhase
import com.ashareai.app.standalone.domain.TrendSignal
import com.ashareai.app.standalone.domain.VolumePriceAnalysis
import com.ashareai.app.standalone.domain.VolumePriceSignal
import com.ashareai.app.scoring.FactorEvidence
import com.ashareai.app.scoring.FactorScoring
import com.ashareai.app.scoring.Risk
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
        val previousClose = closes.dropLast(1).lastOrNull()
        val previousHigh = candles.dropLast(1).takeLast(20).maxOfOrNull { it.high }

        val trendPhase = when {
            lastClose == null || sma20 == null || sma60 == null -> TrendPhase.UNKNOWN
            sma20 > sma60 && lastClose > sma20 -> TrendPhase.RISING
            sma20 < sma60 && lastClose < sma20 -> TrendPhase.FALLING
            kotlin.math.abs(sma20 - sma60) / sma60.coerceAtLeast(0.01) < 0.02 -> TrendPhase.RANGE_BOUND
            else -> TrendPhase.TRANSITION
        }
        val trendSignal = when {
            lastClose != null && previousHigh != null && lastClose > previousHigh -> TrendSignal.BREAKOUT
            macd != null && macd.histogram > 0 && macd.macd > macd.signal -> TrendSignal.BULLISH
            macd != null && macd.histogram < 0 && macd.macd < macd.signal -> TrendSignal.BEARISH
            trendPhase == TrendPhase.TRANSITION -> TrendSignal.REVERSAL
            else -> TrendSignal.NEUTRAL
        }
        val trendConfidence = when {
            trendPhase == TrendPhase.UNKNOWN -> 0.0
            sma20 != null && sma60 != null && macd != null -> 0.8
            else -> 0.55
        }
        val priceDirection = when {
            lastClose == null || previousClose == null -> 0
            lastClose > previousClose -> 1
            lastClose < previousClose -> -1
            else -> 0
        }
        val volumeDirection = when {
            volumeRatio == null -> 0
            volumeRatio > 1.1 -> 1
            volumeRatio < 0.9 -> -1
            else -> 0
        }
        val volumeSignal = when {
            volumeRatio == null || priceDirection == 0 -> VolumePriceSignal.UNKNOWN
            priceDirection > 0 && volumeDirection > 0 -> VolumePriceSignal.VOLUME_UP_PRICE_UP
            priceDirection > 0 && volumeDirection < 0 -> VolumePriceSignal.VOLUME_DOWN_PRICE_UP
            priceDirection < 0 && volumeDirection > 0 -> VolumePriceSignal.VOLUME_UP_PRICE_DOWN
            priceDirection < 0 && volumeDirection < 0 -> VolumePriceSignal.VOLUME_DOWN_PRICE_DOWN
            else -> VolumePriceSignal.NEUTRAL
        }
        val activityProxy = volumeRatio?.let { ((it - 1.0) * 50.0 + 50.0).coerceIn(0.0, 100.0) }
        val trendText = when (trendPhase) {
            TrendPhase.RISING -> "价格位于短中期均线上方，处于上升阶段"
            TrendPhase.FALLING -> "价格位于短中期均线下方，处于下降阶段"
            TrendPhase.RANGE_BOUND -> "短中期均线接近，处于震荡阶段"
            TrendPhase.TRANSITION -> "短中期趋势出现切换迹象"
            TrendPhase.UNKNOWN -> "K 线不足，暂时无法判断趋势"
        }
        val volumeText = when (volumeSignal) {
            VolumePriceSignal.VOLUME_UP_PRICE_UP -> "上涨伴随放量，资金活跃度代理偏强"
            VolumePriceSignal.VOLUME_DOWN_PRICE_UP -> "上涨但成交量偏弱，需观察持续性"
            VolumePriceSignal.VOLUME_UP_PRICE_DOWN -> "下跌伴随放量，抛压较明显"
            VolumePriceSignal.VOLUME_DOWN_PRICE_DOWN -> "下跌但成交量收缩，暂未确认加速"
            VolumePriceSignal.DIVERGENCE -> "量价出现背离，需观察持续性"
            VolumePriceSignal.NEUTRAL -> "量价配合中性"
            VolumePriceSignal.UNKNOWN -> "成交量数据不足"
        }
        val triggers = buildList {
            if (trendSignal == TrendSignal.BREAKOUT) add(MonitoringTrigger.PRICE_BREAKOUT)
            if (trendSignal == TrendSignal.REVERSAL) add(MonitoringTrigger.TREND_REVERSAL)
            if (volumeSignal == VolumePriceSignal.VOLUME_DOWN_PRICE_UP ||
                volumeSignal == VolumePriceSignal.VOLUME_UP_PRICE_DOWN
            ) add(MonitoringTrigger.VOLUME_PRICE_DIVERGENCE)
            if (volatility != null && volatility > 0.55) add(MonitoringTrigger.VOLATILITY_SPIKE)
        }

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

        // Match the source FactorDecisionProvider: missing point-in-time evidence is neutral,
        // while the quality component records how much evidence was actually present.
        val factorTechnicalSignals = buildList {
            if (sma20 != null && lastClose != null && sma20 != 0.0) {
                add((50.0 + (lastClose - sma20) / sma20 * 500.0).coerceIn(0.0, 100.0))
            }
            if (macd != null && lastClose != null && lastClose != 0.0) {
                add((50.0 + macd.histogram / lastClose * 2_000.0).coerceIn(0.0, 100.0))
            }
            if (rsi != null) add((50.0 + (rsi - 50.0)).coerceIn(0.0, 100.0))
        }
        val factorTechnical = factorTechnicalSignals.takeIf { it.isNotEmpty() }?.average() ?: 50.0
        val qualityPresent = listOf(
            sma20,
            sma60,
            macd?.histogram,
            rsi,
            volumeRatio,
        ).count { it != null }
        // Missing fundamental/sentiment/event feeds are neutral evidence, not a score penalty.
        // Keep completeness visible through `unavailable`, while the quality factor stays in
        // the neutral-to-complete range so a price-only local run is not forced below 50.
        val factorQuality = (50.0 + 50.0 * qualityPresent / 5.0).coerceIn(50.0, 100.0)
        val factorEvidence = FactorEvidence(
            fundamental = 50.0,
            technical = factorTechnical,
            sentiment = 50.0,
            quality = factorQuality,
            marketScoreAdjustment = marketContext.scoreAdjustment,
            marketRiskMultiplier = marketContext.riskMultiplier,
        )
        val factorDecision = FactorScoring.decide(factorEvidence)

        // Fundamental, sentiment and event inputs are intentionally not inferred from prices.
        // The deterministic provider still emits a decision using the source's neutral values.
        unavailable += "基本面（本地行情源无财务证据，按中性 50 处理）"
        unavailable += "情绪（本地行情源无情绪证据，按中性 50 处理）"
        unavailable += "事件数据（本地行情源无事件证据，按中性风险乘数处理）"

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
        val technicalTotal = (normalizedTotal * 10).roundToInt() / 10.0
        if (marketContext.regime == MarketRegime.UNKNOWN) {
            unavailable += "大盘指数（K 线不足 20 日，按中性处理）"
        }
        val baseTotal = factorDecision.baseScore
        val total = factorDecision.score
        val risk = when (factorDecision.risk) {
            Risk.LOW -> "低"
            Risk.MEDIUM -> "中"
            Risk.HIGH -> "高"
        }
        val riskLevel = when (factorDecision.risk) {
            Risk.LOW -> RiskLevel.LOW
            Risk.MEDIUM -> RiskLevel.MEDIUM
            Risk.HIGH -> RiskLevel.HIGH
        }
        val signalFreshness = when (quote?.freshness) {
            MarketFreshness.FRESH -> SignalFreshness.FRESH
            MarketFreshness.STALE -> SignalFreshness.STALE
            else -> SignalFreshness.UNKNOWN
        }
        val summary = buildString {
            append(name)
            append(" 确定性因子评分 ")
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
            append("；趋势：")
            append(trendText)
            append("；")
            append(volumeText)
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
                factorFormulaVersion = factorDecision.formulaVersion,
                factorParameterSha256 = com.ashareai.app.scoring.FactorGenome().parameterSha256,
                factorDecision = factorDecision,
                engineVersion = "research-v2",
                trendAnalysis = TrendAnalysis(trendPhase, trendSignal, trendConfidence, trendText),
                volumePrice = VolumePriceAnalysis(volumeSignal, activityProxy, volumeText),
                riskLevel = riskLevel,
                signalFreshness = signalFreshness,
                monitoringTriggers = triggers,
            ),
            risk = risk,
            summary = summary,
            quote = quote,
            signals = ResearchSignalSnapshot(
                engineVersion = "research-v2",
                trend = TrendAnalysis(trendPhase, trendSignal, trendConfidence, trendText),
                volumePrice = VolumePriceAnalysis(volumeSignal, activityProxy, volumeText),
                risk = riskLevel,
                freshness = signalFreshness,
                triggers = triggers,
            ),
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
