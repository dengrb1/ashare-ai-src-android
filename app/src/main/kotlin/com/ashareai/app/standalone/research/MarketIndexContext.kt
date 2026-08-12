package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round

data class MarketIndexSpec(
    val name: String,
    val symbol: String,
    val weight: Double,
)

data class MarketIndexPerformance(
    val name: String,
    val symbol: String,
    val return1d: Double?,
    val return5d: Double?,
    val return20d: Double?,
)

data class MarketIndexContext(
    val tradingDate: LocalDate?,
    val indices: List<MarketIndexPerformance>,
    val compositeReturn1d: Double?,
    val compositeReturn5d: Double?,
    val compositeReturn20d: Double?,
    val regime: MarketRegime,
    val scoreAdjustment: Double,
    val riskMultiplier: Double,
) {
    companion object {
        fun unknown() = MarketIndexContext(
            tradingDate = null,
            indices = emptyList(),
            compositeReturn1d = null,
            compositeReturn5d = null,
            compositeReturn20d = null,
            regime = MarketRegime.UNKNOWN,
            scoreAdjustment = 0.0,
            riskMultiplier = 1.0,
        )
    }
}

enum class MarketRegime {
    RISK_ON,
    NEUTRAL,
    RISK_OFF,
    UNKNOWN,
}

/**
 * Produces one immutable market snapshot per research run. The calculation deliberately uses
 * only K lines available when the run starts, so opening a historical report cannot change it.
 */
object MarketIndexContextAnalyzer {
    val specs = listOf(
        MarketIndexSpec("沪深300", "000300", 0.50),
        MarketIndexSpec("中证500", "000905", 0.30),
        MarketIndexSpec("中证1000", "000852", 0.20),
    )

    fun analyze(candlesBySymbol: Map<String, List<DailyCandle>>): MarketIndexContext {
        val tradingDate = specs.mapNotNull { candlesBySymbol[it.symbol]?.maxByOrNull(DailyCandle::tradingDate)?.tradingDate }
            .minOrNull()
            ?: return MarketIndexContext.unknown()
        val performances = specs.map { spec ->
            val candles = candlesBySymbol[spec.symbol].orEmpty()
                .filter { it.tradingDate <= tradingDate }
                .sortedBy(DailyCandle::tradingDate)
            MarketIndexPerformance(
                name = spec.name,
                symbol = spec.symbol,
                return1d = cumulativeReturn(candles, 1),
                return5d = cumulativeReturn(candles, 5),
                return20d = cumulativeReturn(candles, 20),
            )
        }
        val return1d = weighted(performances) { it.return1d }
        val return5d = weighted(performances) { it.return5d }
        val return20d = weighted(performances) { it.return20d }
        val (regime, adjustment, multiplier) = when {
            return5d == null || return20d == null -> Triple(MarketRegime.UNKNOWN, 0.0, 1.0)
            return5d >= 0.01 && return20d >= 0.0 -> Triple(
                MarketRegime.RISK_ON,
                boundedAdjustment(return5d),
                1.0,
            )
            return5d <= -0.01 && return20d < 0.0 -> Triple(
                MarketRegime.RISK_OFF,
                boundedAdjustment(return5d),
                1.0 - 0.15 * min(1.0, abs(return5d) / 0.05),
            )
            else -> Triple(MarketRegime.NEUTRAL, boundedAdjustment(return5d), 1.0)
        }
        return MarketIndexContext(
            tradingDate = tradingDate,
            indices = performances,
            compositeReturn1d = return1d,
            compositeReturn5d = return5d,
            compositeReturn20d = return20d,
            regime = regime,
            scoreAdjustment = rounded(adjustment),
            riskMultiplier = rounded(multiplier),
        )
    }

    private fun cumulativeReturn(candles: List<DailyCandle>, sessions: Int): Double? {
        if (candles.size <= sessions) return null
        val base = candles[candles.lastIndex - sessions].close
        val latest = candles.last().close
        return if (base == 0.0) null else latest / base - 1.0
    }

    private fun weighted(
        performances: List<MarketIndexPerformance>,
        value: (MarketIndexPerformance) -> Double?,
    ): Double? {
        if (performances.size != specs.size || performances.any { value(it) == null }) return null
        return performances.zip(specs).sumOf { (performance, spec) -> value(performance)!! * spec.weight }
    }

    private fun boundedAdjustment(return5d: Double): Double = max(-8.0, min(8.0, return5d * 100 * 1.5))

    private fun rounded(value: Double): Double = round(value * 1_000_000) / 1_000_000
}
