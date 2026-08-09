package com.ashareai.app.standalone.alerts

import com.ashareai.app.standalone.domain.AlertEvent
import com.ashareai.app.standalone.domain.AlertKind
import com.ashareai.app.standalone.domain.AlertRule
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.NotificationPriority
import kotlin.math.max
import kotlin.math.min

class AlertEvaluator(
    private val now: () -> Long = System::currentTimeMillis,
) {
    fun stopLossPrice(averageCost: Double, atr20: Double?): Double {
        require(averageCost > 0) { "平均成本必须大于 0" }
        val distance = atr20
            ?.let { max(0.05, min(0.10, (it * 2) / averageCost)) }
            ?: 0.08
        return averageCost * (1 - distance)
    }

    fun evaluate(rules: List<AlertRule>, quotes: Collection<MarketQuote>): List<AlertEvent> {
        val quoteBySymbol = quotes.associateBy(MarketQuote::symbol)
        val timestamp = now()
        return rules.mapNotNull { rule ->
            val quote = quoteBySymbol[rule.symbol] ?: return@mapNotNull null
            val price = quote.lastPrice ?: return@mapNotNull null
            if (!rule.enabled || isExpired(rule, timestamp) || isCoolingDown(rule, timestamp)) {
                return@mapNotNull null
            }
            val triggered = when (rule.kind) {
                AlertKind.STOP_LOSS -> rule.lowerBound?.let { price <= it } ?: false
                AlertKind.PROFIT_EXIT -> rule.upperBound?.let { price >= it } ?: false
                AlertKind.BUY_ZONE -> {
                    val lower = rule.lowerBound ?: return@mapNotNull null
                    val upper = rule.upperBound ?: return@mapNotNull null
                    price in lower..upper
                }
                AlertKind.MANUAL_PRICE -> {
                    (rule.lowerBound?.let { price <= it } ?: false) ||
                        (rule.upperBound?.let { price >= it } ?: false)
                }
            }
            if (!triggered) return@mapNotNull null
            AlertEvent(
                ruleId = rule.id,
                symbol = rule.symbol,
                title = rule.name + " 价格提醒",
                body = message(rule.kind, price, rule),
                priority = if (rule.kind == AlertKind.BUY_ZONE) {
                    NotificationPriority.NORMAL
                } else {
                    NotificationPriority.WARNING
                },
            )
        }
    }

    private fun isExpired(rule: AlertRule, now: Long): Boolean = rule.expiresAt?.let { it <= now } ?: false

    private fun isCoolingDown(rule: AlertRule, now: Long): Boolean =
        rule.lastTriggeredAt?.let { now - it < rule.cooldownMinutes * 60_000L } ?: false

    private fun message(kind: AlertKind, price: Double, rule: AlertRule): String = when (kind) {
        AlertKind.STOP_LOSS -> "现价 " + price + " 已触及止损线 " + rule.lowerBound
        AlertKind.PROFIT_EXIT -> "现价 " + price + " 已触及浮盈退出价 " + rule.upperBound
        AlertKind.BUY_ZONE -> "现价 " + price + " 进入买入区间 " + rule.lowerBound + "–" + rule.upperBound
        AlertKind.MANUAL_PRICE -> "现价 " + price + " 已触及手动价位"
    }
}
