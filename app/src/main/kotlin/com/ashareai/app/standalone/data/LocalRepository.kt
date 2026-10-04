package com.ashareai.app.standalone.data

import com.ashareai.app.standalone.data.local.AiProviderEntity
import com.ashareai.app.standalone.data.local.AlertRuleEntity
import com.ashareai.app.standalone.data.local.CandleEntity
import com.ashareai.app.standalone.data.local.ChatMessageEntity
import com.ashareai.app.standalone.data.local.ChatSessionEntity
import com.ashareai.app.standalone.data.local.HoldingEntity
import com.ashareai.app.standalone.data.local.LocalDao
import com.ashareai.app.standalone.data.local.LocalNotificationEntity
import com.ashareai.app.standalone.data.local.QuoteEntity
import com.ashareai.app.standalone.data.local.ResearchCandidateEntity
import com.ashareai.app.standalone.data.local.ResearchReportEntity
import com.ashareai.app.standalone.data.local.ResearchRunEntity
import com.ashareai.app.standalone.data.local.SimulationPortfolioEntity
import com.ashareai.app.standalone.data.local.SyncOperationEntity
import com.ashareai.app.standalone.data.local.SyncBaselineEntity
import com.ashareai.app.standalone.data.local.SyncTombstoneEntity
import com.ashareai.app.standalone.data.local.WatchlistEntity
import com.ashareai.app.standalone.data.archive.ArchiveAlert
import com.ashareai.app.standalone.data.archive.ArchiveBacktest
import com.ashareai.app.standalone.data.archive.ArchiveBacktestTrade
import com.ashareai.app.standalone.data.archive.ArchiveCandidate
import com.ashareai.app.standalone.data.archive.ArchiveChatMessage
import com.ashareai.app.standalone.data.archive.ArchiveChatSession
import com.ashareai.app.standalone.data.archive.ArchiveHolding
import com.ashareai.app.standalone.data.archive.ArchiveImportSummary
import com.ashareai.app.standalone.data.archive.ArchiveNotification
import com.ashareai.app.standalone.data.archive.ArchiveReport
import com.ashareai.app.standalone.data.archive.ArchiveResearchRun
import com.ashareai.app.standalone.data.archive.ArchiveSimulationPortfolio
import com.ashareai.app.standalone.data.archive.ArchiveMergePlanner
import com.ashareai.app.standalone.data.archive.ArchiveMergePreview
import com.ashareai.app.standalone.data.archive.ArchiveMergeResolution
import com.ashareai.app.standalone.data.archive.ArchiveTombstone
import com.ashareai.app.standalone.data.archive.ArchiveWatchlistItem
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import com.ashareai.app.standalone.domain.AiProvider
import com.ashareai.app.standalone.domain.AlertKind
import com.ashareai.app.standalone.domain.AlertRule
import com.ashareai.app.standalone.domain.ChatMessage
import com.ashareai.app.standalone.domain.ChatSession
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.LocalNotification
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.NotificationPriority
import com.ashareai.app.standalone.domain.ResearchCandidate
import com.ashareai.app.standalone.domain.ResearchReport
import com.ashareai.app.standalone.domain.ResearchRun
import com.ashareai.app.standalone.domain.ResearchRunState
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.domain.SimulationPortfolio
import com.ashareai.app.standalone.domain.WatchlistItem
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalRepository(
    private val dao: LocalDao,
    private val json: Json = Json,
) {
    private val archiveImportMutex = Mutex()
    val holdings: Flow<List<Holding>> = dao.observeHoldings().map { entities -> entities.map { it.toDomain() } }
    val watchlist: Flow<List<WatchlistItem>> = dao.observeWatchlist().map { entities -> entities.map { it.toDomain() } }
    val quotes: Flow<List<MarketQuote>> = dao.observeQuotes().map {
        it.map { quote -> quote.toDomain(MarketFreshness.FRESH) }
    }
    val alerts: Flow<List<AlertRule>> = dao.observeAlerts().map { entities -> entities.map { it.toDomain() } }
    val notifications: Flow<List<LocalNotification>> = dao.observeNotifications().map {
        entities -> entities.map { it.toDomain() }
    }
    val unreadNotificationCount: Flow<Int> = dao.observeUnreadNotificationCount()
    val researchRuns: Flow<List<ResearchRun>> = dao.observeResearchRuns().map { entities -> entities.map { it.toDomain() } }
    val reports: Flow<List<ResearchReport>> = dao.observeReports().map { entities -> entities.map { it.toDomain() } }
    val candidates: Flow<List<ResearchCandidate>> = dao.observeCandidates().map {
        entities -> entities.map { it.toDomain() }
    }
    val simulationPortfolios: Flow<List<SimulationPortfolio>> = dao.observeSimulationPortfolios().map {
        entities -> entities.map { it.toDomain() }
    }
    val aiProviders: Flow<List<AiProvider>> = dao.observeAiProviders().map { entities -> entities.map { it.toDomain() } }
    val chatSessions: Flow<List<ChatSession>> = dao.observeChatSessions().map { entities -> entities.map { it.toDomain() } }

    suspend fun holdingsNow(): List<Holding> = dao.holdings().map { it.toDomain() }

    suspend fun watchlistNow(): List<WatchlistItem> = dao.watchlist().map { it.toDomain() }

    suspend fun saveHolding(holding: Holding) {
        dao.upsertHolding(
            HoldingEntity(
                symbol = holding.symbol,
                name = holding.name,
                quantity = holding.quantity.coerceAtLeast(0.0),
                averageCost = holding.averageCost.coerceAtLeast(0.0),
                updatedAt = holding.updatedAt,
            ),
        )
        dao.clearSyncTombstone("holdings", holding.symbol)
    }

    suspend fun removeHolding(symbol: String) {
        val existing = dao.holding(symbol)
        dao.deleteHolding(symbol)
        existing?.let { dao.saveSyncTombstone(SyncTombstoneEntity("holdings", symbol, System.currentTimeMillis(), it.updatedAt)) }
    }

    suspend fun saveWatchlist(item: WatchlistItem) {
        dao.upsertWatchlist(WatchlistEntity(item.symbol, item.name, item.addedAt))
        dao.clearSyncTombstone("watchlist", item.symbol)
    }

    suspend fun removeWatchlist(symbol: String) {
        val existing = dao.watchlistItem(symbol)
        dao.deleteWatchlist(symbol)
        existing?.let { dao.saveSyncTombstone(SyncTombstoneEntity("watchlist", symbol, System.currentTimeMillis(), it.addedAt)) }
    }

    suspend fun cachedQuotes(symbols: Collection<String>): List<MarketQuote> {
        if (symbols.isEmpty()) return emptyList()
        return dao.quotesFor(symbols.distinct()).map { it.toDomain(MarketFreshness.FRESH) }
    }

    suspend fun cacheQuotes(quotes: List<MarketQuote>) {
        dao.upsertQuotes(
            quotes.map {
                QuoteEntity(
                    symbol = it.symbol,
                    name = it.name,
                    lastPrice = it.lastPrice,
                    previousClose = it.previousClose,
                    changePercent = it.changePercent,
                    volume = it.volume,
                    provider = it.provider,
                    fetchedAt = it.fetchedAt,
                )
            },
        )
    }

    suspend fun cachedCandles(symbol: String): List<DailyCandle> = dao.candlesFor(symbol).map { it.toDomain() }

    suspend fun getCandleByDate(symbol: String, date: String): DailyCandle? {
        return dao.getCandleByDate(symbol, date)?.toDomain()
    }

    suspend fun replaceCandles(symbol: String, candles: List<DailyCandle>) {
        dao.clearCandles(symbol)
        dao.upsertCandles(
            candles.map {
                CandleEntity(
                    symbol = it.symbol,
                    tradingDate = it.tradingDate.toString(),
                    open = it.open,
                    close = it.close,
                    high = it.high,
                    low = it.low,
                    volume = it.volume,
                    provider = it.provider,
                    fetchedAt = it.fetchedAt,
                )
            },
        )
    }

    suspend fun clearMarketCache() {
        dao.clearQuotes()
        dao.clearCandles()
    }

    suspend fun activeAlerts(): List<AlertRule> = dao.activeAlerts().map { it.toDomain() }

    suspend fun saveAlert(rule: AlertRule) {
        dao.upsertAlert(
            AlertRuleEntity(
                id = rule.id,
                symbol = rule.symbol,
                name = rule.name,
                kind = rule.kind.name,
                lowerBound = rule.lowerBound,
                upperBound = rule.upperBound,
                enabled = rule.enabled,
                expiresAt = rule.expiresAt,
                cooldownMinutes = rule.cooldownMinutes.coerceAtLeast(1),
                lastTriggeredAt = rule.lastTriggeredAt,
                configJson = rule.configJson,
            ),
        )
        dao.clearSyncTombstone("alerts", rule.id)
    }

    suspend fun removeAlert(id: String) {
        val existing = dao.alert(id)
        dao.deleteAlert(id)
        existing?.let {
            val revision = it.lastTriggeredAt ?: it.expiresAt ?: 0L
            dao.saveSyncTombstone(SyncTombstoneEntity("alerts", id, System.currentTimeMillis(), revision))
        }
    }

    suspend fun markAlertTriggered(id: String, at: Long) = dao.markAlertTriggered(id, at)

    suspend fun saveNotification(notification: LocalNotification, payloadJson: String = "{}") {
        dao.upsertNotification(
            LocalNotificationEntity(
                id = notification.id,
                title = notification.title,
                body = notification.body,
                priority = notification.priority.name,
                deepLink = notification.deepLink,
                createdAt = notification.createdAt,
                isRead = notification.isRead,
                payloadJson = payloadJson,
            ),
        )
    }

    suspend fun markNotificationRead(id: String) = dao.markNotificationRead(id)

    suspend fun markAllNotificationsRead() = dao.markAllNotificationsRead()

    suspend fun createResearchRun(run: ResearchRun) = dao.upsertResearchRun(run.toEntity())

    suspend fun researchRun(id: String): ResearchRun? = dao.researchRun(id)?.toDomain()

    suspend fun allResearchRuns(): List<ResearchRun> = dao.allResearchRuns().map { it.toDomain() }

    suspend fun recoverableResearchRuns(): List<ResearchRun> = dao.recoverableResearchRuns().map { it.toDomain() }

    suspend fun updateResearchRun(
        id: String,
        state: ResearchRunState,
        completedCount: Int,
        errorMessage: String? = null,
        now: Long,
    ) = dao.updateResearchRun(id, state.name, completedCount, now, errorMessage)

    suspend fun requestResearchCancellation(id: String, now: Long) = dao.requestResearchCancellation(id, now)

    suspend fun saveReport(report: ResearchReport) = dao.upsertReport(
        ResearchReportEntity(
            id = report.id,
            runId = report.runId,
            title = report.title,
            deterministicBody = report.deterministicBody,
            aiExplanation = report.aiExplanation,
            createdAt = report.createdAt,
        ),
    )

    suspend fun allReports(): List<ResearchReport> = dao.reports().map { it.toDomain() }

    suspend fun replaceCandidates(runId: String, candidates: List<ResearchCandidate>) {
        dao.deleteCandidatesForRun(runId)
        dao.upsertCandidates(
            candidates.map {
                ResearchCandidateEntity(
                    id = it.id,
                    runId = it.runId,
                    symbol = it.symbol,
                    name = it.name,
                    score = it.score,
                    risk = it.risk,
                    reason = it.reason,
                    createdAt = it.createdAt,
                )
            },
        )
    }

    suspend fun candidatesForRun(runId: String): List<ResearchCandidate> =
        dao.getCandidatesByReportId(runId).map { it.toDomain() }

    suspend fun saveSimulationPortfolio(portfolio: SimulationPortfolio) = dao.upsertSimulationPortfolio(
        SimulationPortfolioEntity(
            id = portfolio.id,
            runId = portfolio.runId,
            name = portfolio.name,
            holdingsJson = portfolio.holdingsJson,
            score = portfolio.score,
            createdAt = portfolio.createdAt,
        ),
    )

    suspend fun aiProvider(id: String): AiProviderEntity? = dao.aiProvider(id)

    suspend fun saveAiProvider(entity: AiProviderEntity) = dao.upsertAiProvider(entity)

    suspend fun removeAiProvider(id: String) = dao.deleteAiProvider(id)

    fun chatMessages(sessionId: String): Flow<List<ChatMessage>> =
        dao.observeChatMessages(sessionId).map { entities -> entities.map { it.toDomain() } }

    suspend fun saveChatSession(session: ChatSession) = dao.upsertChatSession(
        ChatSessionEntity(session.id, session.title, session.updatedAt),
    )

    suspend fun saveChatMessage(message: ChatMessage) = dao.upsertChatMessage(
        ChatMessageEntity(
            id = message.id,
            sessionId = message.sessionId,
            role = message.role,
            body = message.body,
            createdAt = message.createdAt,
            selectedSymbol = message.selectedSymbol,
            includedPortfolio = message.includedPortfolio,
        ),
    )

    /**
     * Exports user-owned records only. It deliberately excludes AI providers, encrypted API keys,
     * quote/K-line caches, and all temporary work.
     */
    suspend fun exportArchive(now: Long = System.currentTimeMillis()): LocalArchiveSnapshot = LocalArchiveSnapshot(
        createdAt = now,
        holdings = dao.holdings().map {
            ArchiveHolding(it.symbol, it.name, it.quantity, it.averageCost, it.updatedAt)
        },
        watchlist = dao.watchlist().map {
            ArchiveWatchlistItem(it.symbol, it.name, it.addedAt)
        },
        alerts = dao.alerts().map {
            ArchiveAlert(
                id = it.id,
                symbol = it.symbol,
                name = it.name,
                kind = it.kind,
                lowerBound = it.lowerBound,
                upperBound = it.upperBound,
                enabled = it.enabled,
                expiresAt = it.expiresAt,
                cooldownMinutes = it.cooldownMinutes,
                lastTriggeredAt = it.lastTriggeredAt,
                configJson = it.configJson,
            )
        },
        notifications = dao.notifications().map {
            ArchiveNotification(
                id = it.id,
                title = it.title,
                body = it.body,
                priority = it.priority,
                deepLink = it.deepLink,
                createdAt = it.createdAt,
                isRead = it.isRead,
            )
        },
        researchRuns = dao.allResearchRuns().map {
            ArchiveResearchRun(
                id = it.id,
                scope = it.scope,
                symbolsJson = it.symbolsJson,
                state = it.state,
                totalCount = it.totalCount,
                completedCount = it.completedCount,
                startedAt = it.startedAt,
                updatedAt = it.updatedAt,
                cancellationRequested = it.cancellationRequested,
                errorMessage = it.errorMessage,
                includePortfolioDataForAi = it.includePortfolioDataForAi,
                aiProviderId = null,
                triggerSource = it.triggerSource,
                automaticReportSlot = it.automaticReportSlot,
                totalBudget = it.totalBudget,
                perSymbolBudget = it.perSymbolBudget,
                maxStockPrice = it.maxStockPrice,
                configVersion = it.configVersion,
            )
        },
        reports = dao.reports().map {
            ArchiveReport(it.id, it.runId, it.title, it.deterministicBody, it.aiExplanation, it.createdAt)
        },
        candidates = dao.candidates().map {
            ArchiveCandidate(it.id, it.runId, it.symbol, it.name, it.score, it.risk, it.reason, it.createdAt)
        },
        simulationPortfolios = dao.simulationPortfolios().map {
            ArchiveSimulationPortfolio(it.id, it.runId, it.name, it.holdingsJson, it.score, it.createdAt)
        },
        backtests = dao.allBacktests().map {
            ArchiveBacktest(
                id = it.id,
                startDate = it.startDate,
                endDate = it.endDate,
                initialCash = it.initialCash,
                benchmark = it.benchmark,
                reportId = it.reportId,
                feeRate = it.feeRate,
                status = it.status,
                metricsJson = it.metricsJson,
                errorMessage = it.errorMessage,
                createdAt = it.createdAt,
                completedAt = it.completedAt,
            )
        },
        backtestTrades = dao.allBacktestTrades().map {
            ArchiveBacktestTrade(
                id = it.id,
                backtestId = it.backtestId,
                symbol = it.symbol,
                name = it.name,
                action = it.action,
                date = it.date,
                price = it.price,
                quantity = it.quantity,
                amount = it.amount,
                fee = it.fee,
                reason = it.reason,
            )
        },
        chatSessions = dao.chatSessions().map {
            ArchiveChatSession(it.id, it.title, it.updatedAt)
        },
        chatMessages = dao.chatMessages().map {
            ArchiveChatMessage(
                id = it.id,
                sessionId = it.sessionId,
                role = it.role,
                body = it.body,
                createdAt = it.createdAt,
                selectedSymbol = it.selectedSymbol,
                includedPortfolio = it.includedPortfolio,
            )
        },
        tombstones = dao.allSyncTombstones().map {
            ArchiveTombstone(it.collection, it.recordKey, it.deletedAt, it.sourceRevision)
        },
    )

    suspend fun previewImportArchive(
        archive: LocalArchiveSnapshot,
        scopes: Set<String> = ArchiveScope.all,
    ): ArchiveMergePreview = ArchiveMergePlanner.preview(exportArchive(), archive, scopes)

    /** Applies an already reviewed preview. Replaying the same key is a no-op. */
    suspend fun applyImportArchive(
        archive: LocalArchiveSnapshot,
        preview: ArchiveMergePreview,
        resolutions: Map<String, ArchiveMergeResolution> = emptyMap(),
        idempotencyKey: String,
        scopes: Set<String> = ArchiveScope.all,
    ): ArchiveImportSummary = archiveImportMutex.withLock {
        val previous = dao.syncOperation(idempotencyKey)
        if (previous?.state == "COMPLETED") {
            return ArchiveImportSummary(
                holdings = 0,
                watchlist = 0,
                reports = 0,
                messages = 0,
                alreadyApplied = true,
            )
        }
        require(idempotencyKey.isNotBlank()) { "导入幂等键不能为空" }
        val currentPreview = previewImportArchive(archive, scopes)
        check(currentPreview == preview) { "导入预览已过期，请重新预览" }
        val now = System.currentTimeMillis()
        dao.saveSyncOperation(SyncOperationEntity(idempotencyKey, "LOCAL_ARCHIVE_IMPORT", "ARCHIVE", "APPLYING", createdAt = now, updatedAt = now))
        val conflictKeys = currentPreview.conflicts
            .filter { ArchiveScope.includes(scopes, it.collection) }
            .map { "${it.collection}:${it.key}" }.toSet()
        try {
            fun accepts(collection: String, key: String): Boolean {
                val compound = "$collection:$key"
                return compound !in conflictKeys || resolutions[compound] == ArchiveMergeResolution.KEEP_IMPORTED
            }
            val filtered = archive.copy(
                holdings = archive.holdings.filter { ArchiveScope.includes(scopes, "holdings") && accepts("holdings", it.symbol) },
                watchlist = archive.watchlist.filter { ArchiveScope.includes(scopes, "watchlist") && accepts("watchlist", it.symbol) },
                alerts = archive.alerts.filter { ArchiveScope.includes(scopes, "alerts") && accepts("alerts", it.id) },
                notifications = archive.notifications.filter { ArchiveScope.includes(scopes, "notifications") && accepts("notifications", it.id) },
                researchRuns = archive.researchRuns.filter { ArchiveScope.includes(scopes, "researchRuns") && accepts("researchRuns", it.id) },
                reports = archive.reports.filter { ArchiveScope.includes(scopes, "reports") && accepts("reports", it.id) },
                candidates = archive.candidates.filter { ArchiveScope.includes(scopes, "candidates") && accepts("candidates", it.id) },
                simulationPortfolios = archive.simulationPortfolios.filter { ArchiveScope.includes(scopes, "simulationPortfolios") && accepts("simulationPortfolios", it.id) },
                backtests = archive.backtests.filter { ArchiveScope.includes(scopes, "backtests") && accepts("backtests", it.id) },
                backtestTrades = archive.backtestTrades.filter { ArchiveScope.includes(scopes, "backtestTrades") && accepts("backtestTrades", it.id) },
                chatSessions = archive.chatSessions.filter { ArchiveScope.includes(scopes, "chatSessions") && accepts("chatSessions", it.id) },
                chatMessages = archive.chatMessages.filter { ArchiveScope.includes(scopes, "chatMessages") && accepts("chatMessages", it.id) },
                tombstones = emptyList(),
            )
            val summary = importArchive(filtered)
            var deleted = 0
            archive.tombstones.forEach { tombstone ->
                val compound = "${tombstone.collection}:${tombstone.recordKey}"
                if (ArchiveScope.includes(scopes, tombstone.collection) && resolutions[compound] != ArchiveMergeResolution.KEEP_LOCAL) {
                    deleteImportedRecord(tombstone.collection, tombstone.recordKey)
                    dao.saveSyncTombstone(SyncTombstoneEntity(tombstone.collection, tombstone.recordKey, tombstone.deletedAt, tombstone.sourceRevision))
                    deleted++
                }
            }
            dao.saveSyncOperation(SyncOperationEntity(idempotencyKey, "LOCAL_ARCHIVE_IMPORT", "ARCHIVE", "COMPLETED", createdAt = previous?.createdAt ?: now, updatedAt = System.currentTimeMillis()))
            return summary.copy(
                deleted = deleted,
                skippedConflicts = conflictKeys.count { resolutions[it] != ArchiveMergeResolution.KEEP_IMPORTED },
            )
        } catch (error: Throwable) {
            dao.saveSyncOperation(
                SyncOperationEntity(
                    idempotencyKey,
                    "LOCAL_ARCHIVE_IMPORT",
                    "ARCHIVE",
                    "FAILED",
                    errorMessage = error.message,
                    createdAt = previous?.createdAt ?: now,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
            throw error
        }
    }

    suspend fun importArchive(archive: LocalArchiveSnapshot): ArchiveImportSummary {
        archive.holdings.forEach {
            dao.upsertHolding(HoldingEntity(it.symbol, it.name, it.quantity, it.averageCost, it.updatedAt))
            dao.clearSyncTombstone("holdings", it.symbol)
        }
        archive.watchlist.forEach {
            dao.upsertWatchlist(WatchlistEntity(it.symbol, it.name, it.addedAt))
            dao.clearSyncTombstone("watchlist", it.symbol)
        }
        archive.alerts.forEach {
            dao.upsertAlert(
                AlertRuleEntity(
                    id = it.id,
                    symbol = it.symbol,
                    name = it.name,
                    kind = it.kind,
                    lowerBound = it.lowerBound,
                    upperBound = it.upperBound,
                    enabled = it.enabled,
                    expiresAt = it.expiresAt,
                    cooldownMinutes = it.cooldownMinutes,
                    lastTriggeredAt = it.lastTriggeredAt,
                    configJson = it.configJson,
                ),
            )
            dao.clearSyncTombstone("alerts", it.id)
        }
        archive.notifications.forEach {
            dao.upsertNotification(
                LocalNotificationEntity(
                    id = it.id,
                    title = it.title,
                    body = it.body,
                    priority = it.priority,
                    deepLink = it.deepLink,
                    createdAt = it.createdAt,
                    isRead = it.isRead,
                    payloadJson = "{}",
                ),
            )
            dao.clearSyncTombstone("notifications", it.id)
        }
        archive.researchRuns.forEach {
            dao.upsertResearchRun(
                ResearchRunEntity(
                    id = it.id,
                    scope = it.scope,
                    symbolsJson = it.symbolsJson,
                    state = it.state,
                    totalCount = it.totalCount,
                    completedCount = it.completedCount,
                    startedAt = it.startedAt,
                    updatedAt = it.updatedAt,
                    cancellationRequested = it.cancellationRequested,
                    errorMessage = it.errorMessage,
                    includePortfolioDataForAi = it.includePortfolioDataForAi,
                    aiProviderId = null,
                    triggerSource = it.triggerSource,
                    automaticReportSlot = it.automaticReportSlot,
                    totalBudget = it.totalBudget,
                    perSymbolBudget = it.perSymbolBudget,
                    maxStockPrice = it.maxStockPrice,
                    configVersion = it.configVersion,
                ),
            )
            dao.clearSyncTombstone("researchRuns", it.id)
        }
        archive.reports.forEach {
            dao.upsertReport(ResearchReportEntity(it.id, it.runId, it.title, it.deterministicBody, it.aiExplanation, it.createdAt))
            dao.clearSyncTombstone("reports", it.id)
        }
        archive.candidates.forEach {
            dao.upsertCandidates(
                listOf(ResearchCandidateEntity(it.id, it.runId, it.symbol, it.name, it.score, it.risk, it.reason, it.createdAt)),
            )
            dao.clearSyncTombstone("candidates", it.id)
        }
        archive.simulationPortfolios.forEach {
            dao.upsertSimulationPortfolio(
                SimulationPortfolioEntity(it.id, it.runId, it.name, it.holdingsJson, it.score, it.createdAt),
            )
            dao.clearSyncTombstone("simulationPortfolios", it.id)
        }
        archive.backtests.forEach {
            dao.insertBacktest(
                com.ashareai.app.standalone.data.local.LocalBacktestEntity(
                    id = it.id,
                    startDate = it.startDate,
                    endDate = it.endDate,
                    initialCash = it.initialCash,
                    benchmark = it.benchmark,
                    reportId = it.reportId,
                    feeRate = it.feeRate,
                    status = it.status,
                    metricsJson = it.metricsJson,
                    errorMessage = it.errorMessage,
                    createdAt = it.createdAt,
                    completedAt = it.completedAt,
                ),
            )
            dao.clearSyncTombstone("backtests", it.id)
        }
        archive.backtestTrades.forEach {
            dao.insertBacktestTrade(
                com.ashareai.app.standalone.data.local.LocalBacktestTradeEntity(
                    id = it.id,
                    backtestId = it.backtestId,
                    symbol = it.symbol,
                    name = it.name,
                    action = it.action,
                    date = it.date,
                    price = it.price,
                    quantity = it.quantity,
                    amount = it.amount,
                    fee = it.fee,
                    reason = it.reason,
                ),
            )
            dao.clearSyncTombstone("backtestTrades", it.id)
        }
        archive.chatSessions.forEach {
            dao.upsertChatSession(ChatSessionEntity(it.id, it.title, it.updatedAt))
            dao.clearSyncTombstone("chatSessions", it.id)
        }
        archive.chatMessages.forEach {
            dao.upsertChatMessage(
                ChatMessageEntity(
                    id = it.id,
                    sessionId = it.sessionId,
                    role = it.role,
                    body = it.body,
                    createdAt = it.createdAt,
                    selectedSymbol = it.selectedSymbol,
                    includedPortfolio = it.includedPortfolio,
                ),
            )
            dao.clearSyncTombstone("chatMessages", it.id)
        }
        return ArchiveImportSummary(
            holdings = archive.holdings.size,
            watchlist = archive.watchlist.size,
            reports = archive.reports.size,
            messages = archive.chatMessages.size,
            backtests = archive.backtests.size,
            backtestTrades = archive.backtestTrades.size,
        )
    }

    private suspend fun deleteImportedRecord(collection: String, key: String) {
        when (collection) {
            "holdings" -> dao.deleteHolding(key)
            "watchlist" -> dao.deleteWatchlist(key)
            "alerts" -> dao.deleteAlert(key)
            "notifications" -> dao.deleteNotification(key)
            "researchRuns" -> dao.deleteResearchRun(key)
            "reports" -> dao.deleteReport(key)
            "candidates" -> dao.deleteCandidate(key)
            "simulationPortfolios" -> dao.deleteSimulationPortfolio(key)
            "backtests" -> {
                dao.deleteBacktest(key)
                dao.deleteBacktestTrades(key)
            }
            "backtestTrades" -> dao.deleteBacktestTrade(key)
            "chatSessions" -> dao.deleteChatSession(key)
            "chatMessages" -> dao.deleteChatMessage(key)
        }
    }

    private fun ResearchRun.toEntity() = ResearchRunEntity(
        id = id,
        scope = scope.name,
        symbolsJson = json.encodeToString(symbols),
        state = state.name,
        totalCount = totalCount,
        completedCount = completedCount,
        startedAt = startedAt,
        updatedAt = updatedAt,
        cancellationRequested = cancellationRequested,
        errorMessage = errorMessage,
        includePortfolioDataForAi = includePortfolioDataForAi,
        aiProviderId = aiProviderId,
        triggerSource = triggerSource.name,
        automaticReportSlot = automaticReportSlot,
        totalBudget = totalBudget,
        perSymbolBudget = perSymbolBudget,
        maxStockPrice = maxStockPrice,
        configVersion = configVersion,
    )

    private fun HoldingEntity.toDomain() = Holding(symbol, name, quantity, averageCost, updatedAt)

    private fun WatchlistEntity.toDomain() = WatchlistItem(symbol, name, addedAt)

    private fun QuoteEntity.toDomain(freshness: MarketFreshness) = MarketQuote(
        symbol = symbol,
        name = name,
        lastPrice = lastPrice,
        previousClose = previousClose,
        changePercent = changePercent,
        volume = volume,
        provider = provider,
        fetchedAt = fetchedAt,
        freshness = freshness,
    )

    private fun CandleEntity.toDomain() = DailyCandle(
        symbol = symbol,
        tradingDate = LocalDate.parse(tradingDate),
        open = open,
        close = close,
        high = high,
        low = low,
        volume = volume,
        provider = provider,
        fetchedAt = fetchedAt,
    )

    private fun AlertRuleEntity.toDomain() = AlertRule(
        id = id,
        symbol = symbol,
        name = name,
        kind = runCatching { AlertKind.valueOf(kind) }.getOrDefault(AlertKind.MANUAL_PRICE),
        lowerBound = lowerBound,
        upperBound = upperBound,
        enabled = enabled,
        expiresAt = expiresAt,
        cooldownMinutes = cooldownMinutes,
        lastTriggeredAt = lastTriggeredAt,
        configJson = configJson,
    )

    private fun LocalNotificationEntity.toDomain() = LocalNotification(
        id = id,
        title = title,
        body = body,
        priority = runCatching { NotificationPriority.valueOf(priority) }.getOrDefault(NotificationPriority.NORMAL),
        deepLink = deepLink,
        createdAt = createdAt,
        isRead = isRead,
    )

    private fun ResearchRunEntity.toDomain() = ResearchRun(
        id = id,
        scope = runCatching { ResearchScope.valueOf(scope) }.getOrDefault(ResearchScope.CUSTOM),
        symbols = runCatching { json.decodeFromString<List<String>>(symbolsJson) }.getOrDefault(emptyList()),
        state = runCatching { ResearchRunState.valueOf(state) }.getOrDefault(ResearchRunState.FAILED),
        totalCount = totalCount,
        completedCount = completedCount,
        startedAt = startedAt,
        updatedAt = updatedAt,
        cancellationRequested = cancellationRequested,
        errorMessage = errorMessage,
        includePortfolioDataForAi = includePortfolioDataForAi,
        aiProviderId = aiProviderId,
        triggerSource = runCatching {
            com.ashareai.app.standalone.domain.ResearchTriggerSource.valueOf(triggerSource)
        }.getOrDefault(com.ashareai.app.standalone.domain.ResearchTriggerSource.MANUAL),
        automaticReportSlot = automaticReportSlot,
        totalBudget = totalBudget,
        perSymbolBudget = perSymbolBudget,
        maxStockPrice = maxStockPrice,
        configVersion = configVersion,
    )

    private fun ResearchReportEntity.toDomain() = ResearchReport(
        id = id,
        runId = runId,
        title = title,
        deterministicBody = deterministicBody,
        aiExplanation = aiExplanation,
        createdAt = createdAt,
    )

    private fun ResearchCandidateEntity.toDomain() = ResearchCandidate(
        id = id,
        runId = runId,
        symbol = symbol,
        name = name,
        score = score,
        risk = risk,
        reason = reason,
        createdAt = createdAt,
    )

    private fun SimulationPortfolioEntity.toDomain() = SimulationPortfolio(
        id = id,
        runId = runId,
        name = name,
        holdingsJson = holdingsJson,
        score = score,
        createdAt = createdAt,
    )

    private fun AiProviderEntity.toDomain() = AiProvider(
        id = id,
        name = name,
        baseUrl = baseUrl,
        model = model,
        organization = organization,
        project = project,
        enabled = enabled,
        createdAt = createdAt,
    )

    private fun ChatSessionEntity.toDomain() = ChatSession(id, title, updatedAt)

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        sessionId = sessionId,
        role = role,
        body = body,
        createdAt = createdAt,
        selectedSymbol = selectedSymbol,
        includedPortfolio = includedPortfolio,
    )

    // 回测相关方法
    suspend fun saveBacktest(
        id: String,
        startDate: String,
        endDate: String,
        initialCash: Double,
        benchmark: String,
        reportId: String?,
        feeRate: Double,
        status: com.ashareai.app.standalone.backtest.BacktestStatus,
        metricsJson: String?,
        errorMessage: String?,
    ) = dao.insertBacktest(
        com.ashareai.app.standalone.data.local.LocalBacktestEntity(
            id = id,
            startDate = startDate,
            endDate = endDate,
            initialCash = initialCash,
            benchmark = benchmark,
            reportId = reportId,
            feeRate = feeRate,
            status = status.name,
            metricsJson = metricsJson,
            errorMessage = errorMessage,
            createdAt = System.currentTimeMillis(),
            completedAt = null,
        )
    )

    suspend fun updateBacktestResult(
        id: String,
        status: com.ashareai.app.standalone.backtest.BacktestStatus,
        metricsJson: String?,
        errorMessage: String?,
    ) = dao.updateBacktestResult(id, status.name, metricsJson, errorMessage, System.currentTimeMillis())

    suspend fun getBacktestStatus(id: String): com.ashareai.app.standalone.backtest.BacktestStatus? =
        dao.getBacktestStatus(id)?.let { com.ashareai.app.standalone.backtest.BacktestStatus.valueOf(it) }

    suspend fun getBacktestMetricsJson(id: String): String? = dao.getBacktestMetricsJson(id)

    suspend fun getBacktestErrorMessage(id: String): String? = dao.getBacktestErrorMessage(id)

    suspend fun saveBacktestTrade(trade: com.ashareai.app.standalone.backtest.BacktestTrade) =
        dao.insertBacktestTrade(
            com.ashareai.app.standalone.data.local.LocalBacktestTradeEntity(
                id = trade.id,
                backtestId = trade.backtestId,
                symbol = trade.symbol,
                name = trade.name,
                action = trade.action.name,
                date = trade.date,
                price = trade.price,
                quantity = trade.quantity,
                amount = trade.amount,
                fee = trade.fee,
                reason = trade.reason,
            )
        )

    suspend fun getBacktestTrades(backtestId: String): List<com.ashareai.app.standalone.backtest.BacktestTrade> =
        dao.getBacktestTrades(backtestId).map {
            com.ashareai.app.standalone.backtest.BacktestTrade(
                id = it.id,
                backtestId = it.backtestId,
                symbol = it.symbol,
                name = it.name,
                action = com.ashareai.app.standalone.backtest.TradeAction.valueOf(it.action),
                date = it.date,
                price = it.price,
                quantity = it.quantity,
                amount = it.amount,
                fee = it.fee,
                reason = it.reason,
            )
        }

    fun listBacktests(limit: Int): Flow<List<com.ashareai.app.standalone.backtest.BacktestSummary>> =
        dao.listBacktests(limit).map { entities ->
            entities.map { entity ->
                val totalReturn = entity.metricsJson?.let {
                    it.substringAfter("\"totalReturn\": ").substringBefore(",").toDoubleOrNull()
                }
                com.ashareai.app.standalone.backtest.BacktestSummary(
                    id = entity.id,
                    startDate = entity.startDate,
                    endDate = entity.endDate,
                    status = com.ashareai.app.standalone.backtest.BacktestStatus.valueOf(entity.status),
                    totalReturn = totalReturn,
                    createdAt = entity.createdAt,
                )
            }
        }

    suspend fun deleteBacktest(id: String) {
        val existing = dao.backtest(id)
        dao.deleteBacktest(id)
        dao.deleteBacktestTrades(id)
        existing?.let {
            dao.saveSyncTombstone(
                SyncTombstoneEntity(
                    collection = "backtests",
                    recordKey = id,
                    deletedAt = System.currentTimeMillis(),
                    sourceRevision = it.completedAt ?: it.createdAt,
                ),
            )
        }
    }

    suspend fun getCandidatesByReportId(reportId: String): List<com.ashareai.app.standalone.domain.ResearchCandidate> =
        dao.getCandidatesByReportId(reportId).map { it.toDomain() }

    suspend fun saveSyncOperation(
        idempotencyKey: String,
        direction: String,
        scope: String,
        state: String,
        previewJson: String? = null,
        errorMessage: String? = null,
        now: Long = System.currentTimeMillis(),
    ) = dao.saveSyncOperation(SyncOperationEntity(idempotencyKey, direction, scope, state, previewJson, errorMessage, now, now))

    suspend fun syncOperation(idempotencyKey: String): SyncOperationEntity? = dao.syncOperation(idempotencyKey)

    suspend fun recordSyncDeletion(collection: String, key: String, sourceRevision: Long? = null) =
        dao.saveSyncTombstone(SyncTombstoneEntity(collection, key, System.currentTimeMillis(), sourceRevision))

    suspend fun clearSyncDeletion(collection: String, key: String) = dao.clearSyncTombstone(collection, key)

    suspend fun syncBaseline(accountKey: String): LocalArchiveSnapshot? =
        dao.syncBaseline(accountKey)?.let { entity ->
            runCatching { json.decodeFromString<LocalArchiveSnapshot>(entity.snapshotJson) }.getOrNull()
        }

    suspend fun saveSyncBaseline(accountKey: String, snapshot: LocalArchiveSnapshot, savedAt: Long = System.currentTimeMillis()) {
        require(accountKey.isNotBlank()) { "同步基线账号标识不能为空" }
        dao.saveSyncBaseline(
            SyncBaselineEntity(
                accountKey = accountKey,
                snapshotJson = json.encodeToString(snapshot),
                savedAt = savedAt,
            ),
        )
    }
}
