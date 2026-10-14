package com.ashareai.app.ui.components

import com.ashareai.app.data.KlinePeriod

/** UI-facing display choices shared by the connected and local chart shells. */
enum class KlineInterval(val label: String, val period: KlinePeriod) {
    INTRADAY("分时", KlinePeriod.MINUTE_5),
    FIVE_DAYS("5日", KlinePeriod.MINUTE_60),
    DAY("日K", KlinePeriod.DAY),
    WEEK("周K", KlinePeriod.WEEK),
    MONTH("月K", KlinePeriod.MONTH),
}

enum class KlineAdjustment(val apiValue: String, val label: String) {
    FORWARD("qfq", "前复权"),
    BACKWARD("hfq", "后复权"),
    NONE("none", "不复权"),
}

enum class KlineIndicator(val label: String) {
    VOLUME("成交量"),
    MACD("MACD"),
    KDJ("KDJ"),
    CAPITAL_ACTIVITY("量价活跃度"),
}

data class KlineCrosshairState(
    val index: Int,
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val amount: Double?,
    val turnoverRate: Double?,
)
