package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import kotlin.math.pow
import kotlin.math.sqrt

data class MacdValue(
    val macd: Double,
    val signal: Double,
    val histogram: Double,
)

object TechnicalIndicators {
    fun sma(values: List<Double>, period: Int): Double? {
        if (period <= 0 || values.size < period) return null
        return values.takeLast(period).average()
    }

    fun ema(values: List<Double>, period: Int): List<Double> {
        if (period <= 0 || values.isEmpty()) return emptyList()
        val alpha = 2.0 / (period + 1)
        val result = ArrayList<Double>(values.size)
        var current = values.first()
        values.forEachIndexed { index, value ->
            if (index > 0) current = alpha * value + (1 - alpha) * current
            result += current
        }
        return result
    }

    fun macd(closes: List<Double>): MacdValue? {
        if (closes.size < 35) return null
        val fast = ema(closes, 12)
        val slow = ema(closes, 26)
        val line = fast.zip(slow) { a, b -> a - b }
        val signal = ema(line, 9)
        if (signal.isEmpty()) return null
        return MacdValue(
            macd = line.last(),
            signal = signal.last(),
            histogram = line.last() - signal.last(),
        )
    }

    fun rsi(closes: List<Double>, period: Int = 14): Double? {
        if (closes.size <= period) return null
        val changes = closes.zipWithNext { before, after -> after - before }.takeLast(period)
        val gains = changes.map { it.coerceAtLeast(0.0) }.average()
        val losses = changes.map { (-it).coerceAtLeast(0.0) }.average()
        return when {
            losses == 0.0 && gains == 0.0 -> 50.0
            losses == 0.0 -> 100.0
            else -> 100 - 100 / (1 + gains / losses)
        }
    }

    fun atr(candles: List<DailyCandle>, period: Int = 20): Double? {
        if (candles.size <= period) return null
        val trueRanges = candles.zipWithNext { previous, current ->
            maxOf(
                current.high - current.low,
                kotlin.math.abs(current.high - previous.close),
                kotlin.math.abs(current.low - previous.close),
            )
        }
        return if (trueRanges.size < period) null else trueRanges.takeLast(period).average()
    }

    fun volatility(closes: List<Double>, period: Int = 20): Double? {
        if (closes.size <= period) return null
        val returns = closes.takeLast(period + 1).zipWithNext { before, after ->
            if (before == 0.0) 0.0 else after / before - 1
        }
        if (returns.isEmpty()) return null
        val mean = returns.average()
        return sqrt(returns.map { (it - mean).pow(2) }.average()) * sqrt(252.0)
    }

    fun volumeRatio(candles: List<DailyCandle>, period: Int = 20): Double? {
        val volumes = candles.mapNotNull(DailyCandle::volume)
        if (volumes.size < period + 1) return null
        val baseline = volumes.dropLast(1).takeLast(period).average()
        return if (baseline == 0.0) null else volumes.last() / baseline
    }
}
