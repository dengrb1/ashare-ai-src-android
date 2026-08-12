package com.ashareai.app.ui

/** 前台行情轮询允许的间隔，与 Web 端保持一致。 */
object MarketRefreshIntervals {
    const val DEFAULT_SECONDS = 5
    val OPTIONS = listOf(5, 10, 15, 30, 60, 120)

    fun normalize(seconds: Int): Int = OPTIONS.firstOrNull { it == seconds } ?: DEFAULT_SECONDS
}
