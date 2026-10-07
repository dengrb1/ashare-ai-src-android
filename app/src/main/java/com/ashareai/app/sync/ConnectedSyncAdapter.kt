package com.ashareai.app.sync

import com.ashareai.app.data.AppContainer
import com.ashareai.app.data.model.AssetStateRequest
import com.ashareai.app.data.model.Backtest
import com.ashareai.app.data.model.BuyEntryMonitor
import com.ashareai.app.data.model.PaperPosition
import com.ashareai.app.data.model.Run
import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.standalone.data.archive.ArchiveBacktest
import com.ashareai.app.standalone.data.archive.ArchiveAlert
import com.ashareai.app.standalone.data.archive.ArchiveCandidate
import com.ashareai.app.standalone.data.archive.ArchiveHolding
import com.ashareai.app.standalone.data.archive.ArchiveNotification
import com.ashareai.app.standalone.data.archive.ArchiveReport
import com.ashareai.app.standalone.data.archive.ArchiveResearchRun
import com.ashareai.app.standalone.data.archive.ArchiveSimulationPortfolio
import com.ashareai.app.standalone.data.archive.ArchiveWatchlistItem
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** A connected snapshot plus the server capabilities that affect a merge. */
data class ConnectedSnapshotResult(
    val snapshot: LocalArchiveSnapshot,
    val warnings: List<String> = emptyList(),
    val unsupportedWrites: Set<String> = emptySet(),
    /** False when any selected server collection could not be read completely. */
    val complete: Boolean = true,
    val incompleteScopes: Set<String> = emptySet(),
)

data class ConnectedApplyResult(
    val applied: Set<String>,
    val unsupported: Set<String>,
    val warnings: List<String> = emptyList(),
)

private data class ConnectedAlertApplyResult(
    val attempted: Boolean,
    val unsupported: Boolean,
    val warnings: List<String> = emptyList(),
)

/** The connected API addresses monitors by symbol; archive IDs are only local keys. */
internal fun planConnectedAlertWrites(
    target: LocalArchiveSnapshot,
    current: LocalArchiveSnapshot,
): List<ArchiveAlert> {
    val targetById = target.alerts.associateBy { it.id }
    val removed = current.alerts
        .filter { it.id.startsWith("buy:") || it.id.startsWith("trade:") }
        .filter { it.id !in targetById }
        .map { it.copy(enabled = false) }
    return (target.alerts + removed).distinctBy { it.id }
}

internal fun connectedSecuritySymbol(value: String): String? {
    val normalized = value.trim().uppercase()
    return normalized.takeIf { Regex("\\d{6}\\.(SH|SZ|BJ)").matches(it) }
}

/**
 * Converts the existing connected APIs to the phone's archive contract. The adapter intentionally
 * excludes credentials, AI keys, push registrations, quotes, candles and logs. Server APIs that
 * only expose read access are still useful for local import, but are reported as read-only when a
 * local snapshot is pushed back.
 */
class ConnectedSyncAdapter(
    private val marketRepository: com.ashareai.app.data.MarketRepository,
    private val notificationRepository: com.ashareai.app.data.NotificationRepository,
    private val researchRepository: com.ashareai.app.data.ResearchRepository,
    private val simulationRepository: com.ashareai.app.data.SimulationRepository,
) {
    constructor(container: AppContainer) : this(
        marketRepository = container.marketRepository,
        notificationRepository = container.notificationRepository,
        researchRepository = container.researchRepository,
        simulationRepository = container.simulationRepository,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun readSnapshot(now: Long = System.currentTimeMillis()): ConnectedSnapshotResult {
        val warnings = mutableListOf<String>()
        val incompleteScopes = mutableSetOf<String>()
        val assets = runCatching { marketRepository.assets() }
            .getOrElse { error ->
                warnings += "无法读取连接版持仓与自选：${error.message ?: "网络错误"}"
                incompleteScopes += setOf(ArchiveScope.HOLDINGS, ArchiveScope.WATCHLIST)
                null
            }
        val notificationPage = runCatching { notificationRepository.notifications(limit = 100) }
            .getOrElse { error ->
                warnings += "连接版通知暂不可用：${error.message ?: "网络错误"}"
                incompleteScopes += ArchiveScope.TRADING_RESEARCH
                null
            }
        val notifications = notificationPage?.items.orEmpty()
        if (notificationPage?.next_cursor != null) {
            warnings += "连接版通知超过单页同步上限，预览不完整"
            incompleteScopes += ArchiveScope.TRADING_RESEARCH
        }
        val runs = runCatching { researchRepository.runs(limit = 50, mine = true) }
            .getOrElse { error ->
                warnings += "连接版研究记录暂不可用：${error.message ?: "网络错误"}"
                incompleteScopes += ArchiveScope.TRADING_RESEARCH
                emptyList()
            }
        if (runs.size >= 50) {
            warnings += "连接版研究记录达到同步上限，预览可能不完整"
            incompleteScopes += ArchiveScope.TRADING_RESEARCH
        }
        val backtests = runCatching { simulationRepository.backtests(limit = 100) }
            .getOrElse { error ->
                warnings += "连接版回测记录暂不可用：${error.message ?: "网络错误"}"
                incompleteScopes += ArchiveScope.BACKTESTS
                emptyList()
            }
        if (backtests.size >= 100) {
            warnings += "连接版回测记录达到同步上限，预览可能不完整"
            incompleteScopes += ArchiveScope.BACKTESTS
        }
        val buyEntryMonitors = runCatching { simulationRepository.buyEntryMonitors(limit = 100) }
            .getOrElse { error ->
                warnings += "连接版买入监控暂不可用：${error.message ?: "网络错误"}"
                incompleteScopes += ArchiveScope.ALERTS
                emptyList()
            }
        if (buyEntryMonitors.size >= 100) {
            warnings += "连接版买入监控达到同步上限，预览可能不完整"
            incompleteScopes += ArchiveScope.ALERTS
        }
        val tradeAdviceMonitors = runCatching { simulationRepository.tradeAdviceMonitors() }
            .getOrElse { error ->
                warnings += "连接版交易建议监控暂不可用：${error.message ?: "网络错误"}"
                incompleteScopes += ArchiveScope.ALERTS
                emptyList()
            }

        val researchRuns = runs.map { it.toArchive(now) }
        val reports = mutableListOf<ArchiveReport>()
        val candidates = mutableListOf<ArchiveCandidate>()
        val portfolios = mutableListOf<ArchiveSimulationPortfolio>()
        runs.take(50).forEach { run ->
            val date = run.trading_date ?: run.requested_date ?: return@forEach
            run.report_id?.let { reportId ->
                val report = runCatching { researchRepository.report(date, run.run_id) }
                    .onFailure { error ->
                        incompleteScopes += ArchiveScope.REPORTS
                        warnings += "连接版报告元数据 $reportId 暂不可用：${error.message ?: "读取失败"}"
                    }
                    .getOrNull()
                reports += ArchiveReport(
                    id = reportId,
                    runId = run.run_id,
                    title = "${date} 研究报告",
                    deterministicBody = report?.result?.let { result ->
                        json.encodeToString(JsonObject.serializer(), result)
                    }.orEmpty(),
                    aiExplanation = null,
                    createdAt = epoch(report?.created_at ?: run.created_at, now),
                )
            }
            runCatching { researchRepository.candidates(date, run.run_id) }
                .onFailure { error ->
                    incompleteScopes += ArchiveScope.TRADING_RESEARCH
                    warnings += "连接版候选结果 ${run.run_id} 暂不可用：${error.message ?: "读取失败"}"
                }
                .getOrNull()
                ?.forEach { candidate ->
                    candidates += ArchiveCandidate(
                        id = "${run.run_id}:${candidate.symbol}",
                        runId = run.run_id,
                        symbol = candidate.symbol,
                        name = candidate.name.orEmpty(),
                        score = candidate.total_score ?: 0.0,
                        risk = "${candidate.event_risk_multiplier ?: 1.0}",
                        reason = "${candidate.industry_name.orEmpty()} · 排名 ${candidate.rank ?: 0}",
                        createdAt = epoch(run.created_at, now),
                    )
                }
            runCatching { simulationRepository.portfolio(date, run.run_id) }
                .onFailure { error ->
                    incompleteScopes += ArchiveScope.SIMULATION_PORTFOLIOS
                    warnings += "连接版模拟组合 ${run.run_id} 暂不可用：${error.message ?: "读取失败"}"
                }
                .getOrNull()
                ?.let { portfolio ->
                    portfolios += ArchiveSimulationPortfolio(
                        id = portfolio.portfolio_id ?: "${run.run_id}:portfolio",
                        runId = run.run_id,
                        name = "${date} 模拟组合",
                        holdingsJson = json.encodeToString(portfolio.positions),
                        score = portfolio.expected_turnover ?: 0.0,
                        createdAt = epoch(run.created_at, now),
                    )
                }
        }

        val snapshot = LocalArchiveSnapshot(
            createdAt = now,
            holdings = assets?.positions.orEmpty().map { position ->
                ArchiveHolding(
                    symbol = position.symbol,
                    name = position.name.orEmpty(),
                    quantity = position.quantity,
                    averageCost = position.cost,
                    updatedAt = epoch(assets?.updated_at, now),
                )
            },
            watchlist = assets?.watchlist.orEmpty().map { symbol ->
                ArchiveWatchlistItem(symbol, symbol, epoch(assets?.updated_at, now))
            },
            alerts = buyEntryMonitors.map { it.toArchiveAlert(now) } +
                tradeAdviceMonitors.map { it.toArchiveAlert(now) },
            notifications = notifications.map { notification ->
                ArchiveNotification(
                    id = notification.notification_id,
                    title = notification.title,
                    body = notification.body,
                    priority = notification.severity,
                    deepLink = notification.resource_url.orEmpty(),
                    createdAt = epoch(notification.created_at, now),
                    isRead = notification.read_at != null,
                )
            },
            researchRuns = researchRuns,
            reports = reports.distinctBy { it.id },
            candidates = candidates.distinctBy { it.id },
            simulationPortfolios = portfolios.distinctBy { it.id },
            backtests = backtests.map { it.toArchive(now) },
        )
        warnings += "连接版研究记录、报告、模拟组合和回测没有安全写回接口；这些范围可预览并导入本地，写回连接版会标记为只读。"
        return ConnectedSnapshotResult(
            snapshot = snapshot,
            warnings = warnings,
            unsupportedWrites = setOf(
                ArchiveScope.TRADING_RESEARCH,
                ArchiveScope.REPORTS,
                ArchiveScope.SIMULATION_PORTFOLIOS,
                ArchiveScope.BACKTESTS,
            ),
            complete = incompleteScopes.isEmpty(),
            incompleteScopes = incompleteScopes,
        )
    }

    /** Asset replacement is naturally idempotent; every other connected collection is read-only. */
    suspend fun applySnapshot(
        snapshot: LocalArchiveSnapshot,
        current: ConnectedSnapshotResult,
        scopes: Set<String>,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ConnectedApplyResult {
        require(idempotencyKey.isNotBlank()) { "同步幂等键不能为空" }
        val includeHoldings = ArchiveScope.includes(scopes, "holdings")
        val includeWatchlist = ArchiveScope.includes(scopes, "watchlist")
        val includeAlerts = ArchiveScope.includes(scopes, ArchiveScope.ALERTS)
        val unsupported = current.unsupportedWrites
            .filter { scope -> ArchiveScope.includes(scopes, scope) }
            .toMutableSet()
        val warnings = mutableListOf<String>()
        if (!includeHoldings && !includeWatchlist && !includeAlerts) {
            return ConnectedApplyResult(
                applied = emptySet(),
                unsupported = unsupported,
                warnings = if (idempotencyKey.isBlank()) listOf("同步幂等键为空") else emptyList(),
            )
        }
        if (includeHoldings || includeWatchlist) {
            // Alerts have their own endpoints. Do not make an unrelated asset read a
            // prerequisite for an alerts-only sync when the market API is unavailable.
            val assets = marketRepository.assets()
            val request = AssetStateRequest(
                watchlist = if (includeWatchlist) snapshot.watchlist.map { it.symbol } else assets.watchlist,
                positions = if (includeHoldings) snapshot.holdings.map { it.toPaperPosition() } else assets.positions,
                total_assets = assets.total_assets,
                exit_monitor_enabled = assets.exit_monitor_enabled,
                default_profit_trigger = assets.default_profit_trigger,
                stop_loss_monitor_enabled = assets.stop_loss_monitor_enabled,
                buy_monitor_enabled = assets.buy_monitor_enabled,
                market_refresh_interval_seconds = assets.market_refresh_interval_seconds,
            )
            // The endpoint replaces one user's asset document, so retrying the same snapshot does
            // not create duplicate records even though older servers do not accept an idempotency header.
            marketRepository.saveAssets(request, idempotencyKey)
        }
        if (includeAlerts) {
            val alertResult = applyConnectedAlerts(snapshot, current.snapshot, idempotencyKey)
            warnings += alertResult.warnings
            if (alertResult.unsupported) unsupported += ArchiveScope.ALERTS
        }
        return ConnectedApplyResult(
            applied = buildSet {
                if (includeHoldings) add(ArchiveScope.HOLDINGS)
                if (includeWatchlist) add(ArchiveScope.WATCHLIST)
                if (includeAlerts && ArchiveScope.ALERTS !in unsupported) add(ArchiveScope.ALERTS)
            },
            unsupported = unsupported,
            warnings = warnings + if (idempotencyKey.isBlank()) listOf("同步幂等键为空") else emptyList(),
        )
    }

    private suspend fun applyConnectedAlerts(
        target: LocalArchiveSnapshot,
        current: LocalArchiveSnapshot,
        idempotencyKey: String,
    ): ConnectedAlertApplyResult {
        val warnings = mutableListOf<String>()
        var unsupported = false
        val alertsToApply = planConnectedAlertWrites(target, current)
        alertsToApply.forEach { alert ->
            val symbol = connectedSecuritySymbol(alert.symbol)
            if (symbol == null) {
                unsupported = true
                warnings += "提醒 ${alert.id} 的证券代码缺少交易所后缀，无法写回连接版"
                return@forEach
            }
            runCatching {
                when {
                    alert.kind == "BUY_ENTRY_MONITOR" || alert.id.startsWith("buy:") ->
                        simulationRepository.saveBuyEntryMonitor(
                            com.ashareai.app.data.model.BuyEntryMonitorRequest(symbol, alert.enabled),
                            "$idempotencyKey:${alert.id}",
                        )
                    alert.kind == "TRADE_ADVICE_MONITOR" || alert.id.startsWith("trade:") ->
                        simulationRepository.saveTradeAdviceMonitor(
                            com.ashareai.app.data.model.TradeAdviceMonitorRequest(
                                symbol = symbol,
                                enabled = alert.enabled,
                                manual_buy_price = alert.lowerBound,
                                manual_sell_price = alert.upperBound,
                            ),
                            "$idempotencyKey:${alert.id}",
                        )
                    else -> {
                        unsupported = true
                        warnings += "提醒 ${alert.id} 的类型 ${alert.kind} 没有连接版写回接口"
                    }
                }
            }.onFailure { error ->
                unsupported = true
                warnings += "提醒 ${alert.id} 写回连接版失败：${error.message ?: "请求失败"}"
            }
        }
        return ConnectedAlertApplyResult(
            attempted = alertsToApply.isNotEmpty(),
            unsupported = unsupported,
            warnings = warnings,
        )
    }

    private fun ArchiveHolding.toPaperPosition() = PaperPosition(
        symbol = symbol,
        name = name,
        quantity = quantity,
        cost = averageCost,
    )

    private fun BuyEntryMonitor.toArchiveAlert(now: Long) = ArchiveAlert(
        id = "buy:$monitor_id",
        symbol = symbol,
        name = symbol,
        kind = "BUY_ENTRY_MONITOR",
        lowerBound = entry_low,
        upperBound = entry_high,
        enabled = status.equals("ACTIVE", ignoreCase = true),
        expiresAt = expires_at?.let { epoch(it, now) },
        cooldownMinutes = 30,
        lastTriggeredAt = triggered_at?.let { epoch(it, now) },
        configJson = json.encodeToString(this),
    )

    private fun TradeAdviceMonitor.toArchiveAlert(now: Long) = ArchiveAlert(
        id = "trade:$monitor_id",
        symbol = symbol,
        name = symbol,
        kind = "TRADE_ADVICE_MONITOR",
        lowerBound = manual_buy_price ?: ai_buy_price,
        upperBound = manual_sell_price ?: ai_sell_price,
        enabled = enabled,
        expiresAt = null,
        cooldownMinutes = 30,
        lastTriggeredAt = generated_at?.let { epoch(it, now) },
        configJson = json.encodeToString(this),
    )

    private fun Run.toArchive(now: Long) = ArchiveResearchRun(
        id = run_id,
        scope = research_scope ?: "UNKNOWN",
        symbolsJson = json.encodeToString(target_symbols),
        state = status,
        totalCount = target_symbols.size,
        completedCount = progress ?: 0,
        startedAt = epoch(started_at ?: created_at, now),
        updatedAt = epoch(completed_at ?: created_at ?: started_at, now),
        cancellationRequested = status.equals("CANCELLING", ignoreCase = true),
        errorMessage = error_message,
        includePortfolioDataForAi = false,
        triggerSource = trigger_source ?: "CONNECTED",
        automaticReportSlot = automatic_report_slot,
        totalBudget = total_budget ?: 1_000_000.0,
        perSymbolBudget = per_symbol_budget ?: 80_000.0,
        configVersion = 1,
    )

    private fun Backtest.toArchive(now: Long) = ArchiveBacktest(
        id = backtest_id,
        startDate = start_date.orEmpty(),
        endDate = end_date.orEmpty(),
        initialCash = 0.0,
        benchmark = "",
        reportId = null,
        feeRate = 0.0,
        status = status,
        metricsJson = metrics?.toString(),
        errorMessage = error_message,
        createdAt = epoch(created_at, now),
        completedAt = completed_at?.let { epoch(it, now) },
    )

    private fun epoch(value: String?, fallback: Long): Long {
        if (value.isNullOrBlank()) return fallback
        return runCatching { Instant.parse(value).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
            .recoverCatching { LocalDate.parse(value).atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() }
            .getOrDefault(fallback)
    }
}
