package com.ashareai.app.standalone.monitor

import com.ashareai.app.standalone.alerts.AlertEvaluator
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.data.settings.SettingsStore
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.MonitoringEvent
import com.ashareai.app.standalone.domain.MonitoringEventType
import com.ashareai.app.standalone.domain.MonitoringSource
import com.ashareai.app.standalone.domain.NotificationPriority
import com.ashareai.app.standalone.domain.ResearchCandidate
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.TrendPhase
import com.ashareai.app.standalone.domain.TrendSignal
import com.ashareai.app.standalone.domain.VolumePriceSignal
import com.ashareai.app.standalone.domain.MonitoringTarget
import com.ashareai.app.standalone.research.DeterministicResearchEngine
import com.ashareai.app.standalone.research.ResearchEngine
import com.ashareai.app.standalone.notifications.NotificationRepository
import com.ashareai.app.standalone.work.ShanghaiTradingCalendar
import com.ashareai.app.performance.ResourceBudget
import com.ashareai.app.performance.DeviceResourceLevel
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
    private val engine: ResearchEngine = DeterministicResearchEngine(),
    private val settings: SettingsStore,
    private val alerts: AlertEvaluator,
    private val notifications: NotificationRepository,
    private val calendar: ShanghaiTradingCalendar,
    private val clock: () -> Long = System::currentTimeMillis,
    private val resourceBudget: () -> ResourceBudget = {
        ResourceBudget(DeviceResourceLevel.NORMAL, 1, 128, 128, "默认监控预算")
    },
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
        val budget = resourceBudget()
        val intervalSeconds = (preferences.monitoringIntervalSeconds * budget.refreshIntervalMultiplier)
            .toInt()
            .coerceIn(15, 900)
        val now = clock()
        if (!preferences.monitoringEnabled) {
            return inactive(intervalSeconds, "持仓监控已关闭", now)
        }
        if (!calendar.isTradingSession()) {
            return inactive(intervalSeconds, "非交易时段，已暂停行情请求", now)
        }
        val targets = monitoringTargets()
        if (targets.isEmpty()) {
            return inactive(intervalSeconds, "暂无自选、报告候选或模拟组合目标", now)
        }
        val quotes = market.refreshQuotes(targets.map(MonitoringTarget::symbol)).associateBy(MarketQuote::symbol)
        val latestReport = local.allReports().firstOrNull()
        val baselines = latestReport?.let { local.candidatesForRun(it.runId) }
            .orEmpty()
            .associateBy(ResearchCandidate::symbol)
        var eventCount = 0
        targets.forEach { target ->
            val quote = quotes[target.symbol] ?: return@forEach
            val candles = market.dailyCandles(target.symbol, limit = 100)
            val result = engine.analyse(
                symbol = target.symbol,
                name = target.name.ifBlank { quote.name },
                quote = quote,
                candles = candles,
            )
            val baseline = baselines[target.symbol]
            val triggers = detectTriggers(target, quote, result, baseline)
            triggers.forEach { type ->
                val event = monitoringEvent(target, quote, result, type, latestReport?.id, now)
                if (local.monitoringEvent(event.id) == null) {
                    local.saveMonitoringEvent(event)
                    eventCount += 1
                    if (event.severity == NotificationPriority.WARNING) {
                        notifications.publish(
                            title = "${target.name} · ${eventTitle(type)}",
                            body = event.payload,
                            priority = event.severity,
                            deepLink = "reports",
                            notificationId = "monitor-notification-${event.id}",
                        )
                    }
                }
            }
        }
        if (preferences.alertsEnabled) {
            alerts.evaluate(local.activeAlerts(), quotes.values).forEach { event ->
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
        val holdings = local.holdingsNow()
        val value = holdings.sumOf { holding ->
            val price = quotes[holding.symbol]?.lastPrice ?: return@sumOf 0.0
            price * holding.quantity
        }
        val profit = holdings.sumOf { holding ->
            val price = quotes[holding.symbol]?.lastPrice ?: return@sumOf 0.0
            (price - holding.averageCost) * holding.quantity
        }
        return MonitoringSnapshot(
            active = true,
            intervalSeconds = intervalSeconds,
            title = "实时研究监控 · ${targets.size} 只",
            body = "市值 ${format(value)} · 盈亏 ${format(profit)} · 新信号 $eventCount 条",
            updatedAt = now,
        )
    }

    private suspend fun monitoringTargets(): List<MonitoringTarget> {
        val targets = linkedMapOf<String, MonitoringTarget>()
        local.holdingsNow().forEach { holding ->
            targets[holding.symbol] = MonitoringTarget(
                symbol = holding.symbol,
                name = holding.name,
                source = MonitoringSource.SIMULATION_PORTFOLIO,
            )
        }
        local.watchlistNow().forEach { item ->
            targets.putIfAbsent(
                item.symbol,
                MonitoringTarget(item.symbol, item.name, MonitoringSource.WATCHLIST),
            )
        }
        val report = local.allReports().firstOrNull()
        report?.let { latest ->
            local.candidatesForRun(latest.runId).take(30).forEach { candidate ->
                targets.putIfAbsent(
                    candidate.symbol,
                    MonitoringTarget(candidate.symbol, candidate.name, MonitoringSource.REPORT_CANDIDATE),
                )
            }
        }
        // Simulation portfolios are stored as a bounded JSON list. They add targets that
        // may no longer be in the current holdings or watchlist without creating a new table.
        local.simulationPortfolios.first().take(10).forEach { portfolio ->
            runCatching {
                Json.parseToJsonElement(portfolio.holdingsJson).jsonArray.forEach { element ->
                    val symbol = element.jsonObject["symbol"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (symbol.isNotBlank()) {
                        targets.putIfAbsent(
                            symbol,
                            MonitoringTarget(symbol, symbol, MonitoringSource.SIMULATION_PORTFOLIO),
                        )
                    }
                }
            }
        }
        return targets.values.filter(MonitoringTarget::enabled).take(100)
    }

    private fun detectTriggers(
        target: MonitoringTarget,
        quote: MarketQuote,
        result: ResearchResult,
        baseline: ResearchCandidate?,
    ): List<MonitoringEventType> = buildList {
        val breakoutThreshold = target.priceBreakoutPercent ?: 3.0
        if (result.signals.trend.signal == TrendSignal.BREAKOUT ||
            (quote.changePercent ?: 0.0) >= breakoutThreshold
        ) add(MonitoringEventType.PRICE_BREAKOUT)
        if (result.signals.trend.signal == TrendSignal.REVERSAL ||
            (baseline != null && baseline.trendPhase != TrendPhase.UNKNOWN &&
                result.signals.trend.phase != baseline.trendPhase)
        ) add(MonitoringEventType.TREND_REVERSAL)
        if (result.signals.volumePrice.signal == VolumePriceSignal.VOLUME_DOWN_PRICE_UP ||
            result.signals.volumePrice.signal == VolumePriceSignal.VOLUME_UP_PRICE_DOWN ||
            result.signals.volumePrice.signal == VolumePriceSignal.DIVERGENCE
        ) add(MonitoringEventType.VOLUME_PRICE_DIVERGENCE)
        if (result.score.monitoringTriggers.contains(com.ashareai.app.standalone.domain.MonitoringTrigger.VOLATILITY_SPIKE)) {
            add(MonitoringEventType.VOLATILITY_SPIKE)
        }
        if (baseline != null && kotlin.math.abs(result.score.total - baseline.score) >= target.scoreChangeThreshold) {
            add(MonitoringEventType.SCORE_CHANGED)
        }
    }.distinct()

    private fun monitoringEvent(
        target: MonitoringTarget,
        quote: MarketQuote,
        result: ResearchResult,
        type: MonitoringEventType,
        reportId: String?,
        now: Long,
    ): MonitoringEvent {
        val bucket = (quote.fetchedAt.takeIf { it > 0 } ?: now) / 60_000L
        val eventId = "monitor:${target.symbol}:${type.name}:$bucket"
        val payload = buildJsonObject {
            put("source", target.source.name)
            put("price", quote.lastPrice ?: 0.0)
            quote.changePercent?.let { put("change_percent", it) }
            put("score", result.score.total)
            put("trend_phase", result.signals.trend.phase.name)
            put("trend_signal", result.signals.trend.signal.name)
            put("volume_price_signal", result.signals.volumePrice.signal.name)
            result.signals.volumePrice.capitalActivityProxy?.let { put("capital_activity_proxy", it) }
            put("explanation", result.signals.trend.explanation + "；" + result.signals.volumePrice.explanation)
        }.toString()
        return MonitoringEvent(
            id = eventId,
            symbol = target.symbol,
            name = target.name.ifBlank { quote.name },
            type = type,
            occurredAt = now,
            severity = if (type == MonitoringEventType.VOLUME_PRICE_DIVERGENCE ||
                type == MonitoringEventType.VOLATILITY_SPIKE
            ) NotificationPriority.WARNING else NotificationPriority.NORMAL,
            reportId = reportId,
            payload = payload,
        )
    }

    private fun eventTitle(type: MonitoringEventType): String = when (type) {
        MonitoringEventType.PRICE_BREAKOUT -> "价格突破"
        MonitoringEventType.TREND_REVERSAL -> "趋势拐点"
        MonitoringEventType.VOLUME_PRICE_DIVERGENCE -> "量价背离"
        MonitoringEventType.VOLATILITY_SPIKE -> "波动异常"
        MonitoringEventType.SCORE_CHANGED -> "评分变化"
        MonitoringEventType.REPORT_UPDATED -> "报告更新"
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
