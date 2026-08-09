package com.ashareai.app.standalone.monitor

import com.ashareai.app.standalone.alerts.AlertEvaluator
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.data.settings.SettingsStore
import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.notifications.NotificationRepository
import com.ashareai.app.standalone.work.ShanghaiTradingCalendar
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

data class MonitoringSnapshot(
    val active: Boolean,
    val intervalSeconds: Int,
    val title: String,
    val body: String,
    val updatedAt: Long,
)

class MarketMonitoringCoordinator(
    private val local: LocalRepository,
    private val market: MarketRepository,
    private val settings: SettingsStore,
    private val alerts: AlertEvaluator,
    private val notifications: NotificationRepository,
    private val calendar: ShanghaiTradingCalendar,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun monitorLoop(onUpdate: (MonitoringSnapshot) -> Unit) {
        while (true) {
            val snapshot = checkOnce()
            onUpdate(snapshot)
            if (!snapshot.active) return
            delay(snapshot.intervalSeconds * 1_000L)
        }
    }

    suspend fun checkOnce(): MonitoringSnapshot {
        val preferences = settings.settings.first()
        val now = clock()
        if (!preferences.monitoringEnabled) {
            return inactive(preferences.monitoringIntervalSeconds, "持仓监控已关闭", now)
        }
        val holdings = local.holdingsNow()
        if (holdings.isEmpty()) {
            return inactive(preferences.monitoringIntervalSeconds, "无持仓，已停止行情轮询", now)
        }
        if (!calendar.isTradingSession()) {
            return inactive(preferences.monitoringIntervalSeconds, "非交易时段，保留最后行情时间", now)
        }
        val quotes = market.refreshQuotes(holdings.map(Holding::symbol))
        if (preferences.alertsEnabled) {
            alerts.evaluate(local.activeAlerts(), quotes).forEach { event ->
                notifications.publish(
                    title = event.title,
                    body = event.body,
                    priority = event.priority,
                    deepLink = "alerts",
                    notificationId = "alert-" + event.ruleId + "-" + now,
                )
                local.markAlertTriggered(event.ruleId, now)
            }
        }
        val value = holdings.sumOf { holding ->
            val price = quotes.firstOrNull { it.symbol == holding.symbol }?.lastPrice ?: return@sumOf 0.0
            price * holding.quantity
        }
        val profit = holdings.sumOf { holding ->
            val price = quotes.firstOrNull { it.symbol == holding.symbol }?.lastPrice ?: return@sumOf 0.0
            (price - holding.averageCost) * holding.quantity
        }
        return MonitoringSnapshot(
            active = true,
            intervalSeconds = preferences.monitoringIntervalSeconds,
            title = "持仓监控 · " + holdings.size + " 只",
            body = "市值 " + format(value) + " · 盈亏 " + format(profit),
            updatedAt = now,
        )
    }

    private fun inactive(intervalSeconds: Int, message: String, now: Long) = MonitoringSnapshot(
        active = false,
        intervalSeconds = intervalSeconds,
        title = "持仓监控已停止",
        body = message,
        updatedAt = now,
    )

    private fun format(value: Double): String = String.format(Locale.US, "%.2f", value)
}
