package com.ashareai.app.ui.components

import kotlin.math.roundToInt

data class ChartViewport(
    val visibleCount: Int = DEFAULT_VISIBLE,
    val offsetFromLatest: Int = 0,
) {
    fun normalized(total: Int): ChartViewport {
        if (total <= 0) return copy(visibleCount = DEFAULT_VISIBLE, offsetFromLatest = 0)
        val count = visibleCount.coerceIn(minOf(MIN_VISIBLE, total), total)
        return copy(
            visibleCount = count,
            offsetFromLatest = offsetFromLatest.coerceIn(0, (total - count).coerceAtLeast(0)),
        )
    }

    fun range(total: Int): IntRange {
        val safe = normalized(total)
        val endExclusive = total - safe.offsetFromLatest
        return (endExclusive - safe.visibleCount) until endExclusive
    }

    fun zoom(total: Int, scale: Float): ChartViewport {
        if (!scale.isFinite() || scale <= 0f) return normalized(total)
        val next = (visibleCount / scale).roundToInt().coerceIn(minOf(MIN_VISIBLE, total), total)
        return copy(visibleCount = next).normalized(total)
    }

    fun pan(total: Int, bars: Int): ChartViewport =
        copy(offsetFromLatest = offsetFromLatest + bars).normalized(total)

    fun latest(total: Int): ChartViewport = copy(offsetFromLatest = 0).normalized(total)

    companion object {
        const val MIN_VISIBLE = 20
        const val DEFAULT_VISIBLE = 80
    }
}
