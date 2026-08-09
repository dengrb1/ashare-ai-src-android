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
import com.ashareai.app.standalone.data.local.WatchlistEntity
import com.ashareai.app.standalone.data.archive.ArchiveAlert
import com.ashareai.app.standalone.data.archive.ArchiveCandidate
import com.ashareai.app.standalone.data.archive.ArchiveChatMessage
import com.ashareai.app.standalone.data.archive.ArchiveChatSession
import com.ashareai.app.standalone.data.archive.ArchiveHolding
import com.ashareai.app.standalone.data.archive.ArchiveImportSummary
import com.ashareai.app.standalone.data.archive.ArchiveNotification
import com.ashareai.app.standalone.data.archive.ArchiveReport
import com.ashareai.app.standalone.data.archive.ArchiveResearchRun
import com.ashareai.app.standalone.data.archive.ArchiveSimulationPortfolio
import com.ashareai.app.standalone.data.archive.ArchiveWatchlistItem
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalRepository(
    private val dao: LocalDao,
    private val json: Json = Json,
) {
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

    suspend fun saveHolding(holding: Holding) = dao.upsertHolding(
        HoldingEntity(
            symbol = holding.symbol,
            name = holding.name,
            quantity = holding.quantity.coerceAtLeast(0.0),
            averageCost = holding.averageCost.coerceAtLeast(0.0),
            updatedAt = holding.updatedAt,
        ),
    )

    suspend fun removeHolding(symbol: String) = dao.deleteHolding(symbol)

    suspend fun saveWatchlist(item: WatchlistItem) = dao.upsertWatchlist(
        WatchlistEntity(item.symbol, item.name, item.addedAt),
    )

    suspend fun removeWatchlist(symbol: String) = dao.deleteWatchlist(symbol)

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

    suspend fun saveAlert(rule: AlertRule) = dao.upsertAlert(
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

    suspend fun removeAlert(id: String) = dao.deleteAlert(id)

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
    )

    suspend fun importArchive(archive: LocalArchiveSnapshot): ArchiveImportSummary {
        archive.holdings.forEach {
            dao.upsertHolding(HoldingEntity(it.symbol, it.name, it.quantity, it.averageCost, it.updatedAt))
        }
        archive.watchlist.forEach {
            dao.upsertWatchlist(WatchlistEntity(it.symbol, it.name, it.addedAt))
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
                ),
            )
        }
        archive.reports.forEach {
            dao.upsertReport(ResearchReportEntity(it.id, it.runId, it.title, it.deterministicBody, it.aiExplanation, it.createdAt))
        }
        archive.candidates.forEach {
            dao.upsertCandidates(
                listOf(ResearchCandidateEntity(it.id, it.runId, it.symbol, it.name, it.score, it.risk, it.reason, it.createdAt)),
            )
        }
        archive.simulationPortfolios.forEach {
            dao.upsertSimulationPortfolio(
                SimulationPortfolioEntity(it.id, it.runId, it.name, it.holdingsJson, it.score, it.createdAt),
            )
        }
        archive.chatSessions.forEach {
            dao.upsertChatSession(ChatSessionEntity(it.id, it.title, it.updatedAt))
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
        }
        return ArchiveImportSummary(
            holdings = archive.holdings.size,
            watchlist = archive.watchlist.size,
            reports = archive.reports.size,
            messages = archive.chatMessages.size,
        )
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
}
