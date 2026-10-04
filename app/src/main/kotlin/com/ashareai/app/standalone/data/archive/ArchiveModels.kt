package com.ashareai.app.standalone.data.archive

import kotlinx.serialization.Serializable

@Serializable
data class LocalArchiveSnapshot(
    val formatVersion: Int = 1,
    val createdAt: Long,
    val holdings: List<ArchiveHolding> = emptyList(),
    val watchlist: List<ArchiveWatchlistItem> = emptyList(),
    val alerts: List<ArchiveAlert> = emptyList(),
    val notifications: List<ArchiveNotification> = emptyList(),
    val researchRuns: List<ArchiveResearchRun> = emptyList(),
    val reports: List<ArchiveReport> = emptyList(),
    val candidates: List<ArchiveCandidate> = emptyList(),
    val simulationPortfolios: List<ArchiveSimulationPortfolio> = emptyList(),
    val backtests: List<ArchiveBacktest> = emptyList(),
    val backtestTrades: List<ArchiveBacktestTrade> = emptyList(),
    val chatSessions: List<ArchiveChatSession> = emptyList(),
    val chatMessages: List<ArchiveChatMessage> = emptyList(),
    /** Deletions are explicit so an absent record is never treated as a delete. */
    val tombstones: List<ArchiveTombstone> = emptyList(),
)

/** Stable archive collection names used by the user-selected sync scope. */
object ArchiveScope {
    const val HOLDINGS = "holdings"
    const val TRADING_RESEARCH = "trading_research"
    const val WATCHLIST = "watchlist"
    const val ALERTS = "alerts"
    const val REPORTS = "reports"
    const val SIMULATION_PORTFOLIOS = "simulation_portfolios"
    const val BACKTESTS = "backtests"
    const val ALL = "*"

    val all: Set<String> = setOf(
        HOLDINGS,
        TRADING_RESEARCH,
        WATCHLIST,
        ALERTS,
        REPORTS,
        SIMULATION_PORTFOLIOS,
        BACKTESTS,
    )

    fun includes(scopes: Set<String>, collection: String): Boolean {
        if (scopes.isEmpty()) return false
        if (ALL in scopes) return true
        return when (collection) {
            HOLDINGS, "holdings" -> HOLDINGS in scopes
            WATCHLIST, "watchlist" -> WATCHLIST in scopes
            ALERTS, "alerts" -> ALERTS in scopes
            REPORTS, "reports" -> REPORTS in scopes
            SIMULATION_PORTFOLIOS, "simulationPortfolios" -> SIMULATION_PORTFOLIOS in scopes
            BACKTESTS, "backtests", "backtestTrades" -> BACKTESTS in scopes
            TRADING_RESEARCH, "researchRuns", "candidates", "chatSessions", "chatMessages", "notifications" -> TRADING_RESEARCH in scopes
            else -> false
        }
    }
}

/**
 * Stable revisions used by both preview implementations.  A record's content
 * fingerprint is still checked by [ArchiveMergePlanner]; these timestamps only
 * decide whether a non-conflicting update is newer.
 */
object ArchiveRevision {
    fun holding(value: ArchiveHolding): Long = value.updatedAt
    fun watchlist(value: ArchiveWatchlistItem): Long = value.addedAt
    fun alert(value: ArchiveAlert): Long = value.lastTriggeredAt ?: value.expiresAt ?: 0L
    fun notification(value: ArchiveNotification): Long = value.createdAt
    fun researchRun(value: ArchiveResearchRun): Long = value.updatedAt
    fun report(value: ArchiveReport): Long = value.createdAt
    fun candidate(value: ArchiveCandidate): Long = value.createdAt
    fun simulationPortfolio(value: ArchiveSimulationPortfolio): Long = value.createdAt
    fun backtest(value: ArchiveBacktest): Long = value.createdAt
    fun backtestTrade(value: ArchiveBacktestTrade): Long =
        value.date.replace("-", "").toLongOrNull() ?: 0L
    fun chatSession(value: ArchiveChatSession): Long = value.updatedAt
    fun chatMessage(value: ArchiveChatMessage): Long = value.createdAt
}

@Serializable
data class ArchiveTombstone(
    val collection: String,
    val recordKey: String,
    val deletedAt: Long,
    val sourceRevision: Long? = null,
)

@Serializable
data class ArchiveHolding(
    val symbol: String,
    val name: String,
    val quantity: Double,
    val averageCost: Double,
    val updatedAt: Long,
)

@Serializable
data class ArchiveWatchlistItem(
    val symbol: String,
    val name: String,
    val addedAt: Long,
)

@Serializable
data class ArchiveAlert(
    val id: String,
    val symbol: String,
    val name: String,
    val kind: String,
    val lowerBound: Double? = null,
    val upperBound: Double? = null,
    val enabled: Boolean,
    val expiresAt: Long? = null,
    val cooldownMinutes: Int,
    val lastTriggeredAt: Long? = null,
    val configJson: String,
)

@Serializable
data class ArchiveNotification(
    val id: String,
    val title: String,
    val body: String,
    val priority: String,
    val deepLink: String,
    val createdAt: Long,
    val isRead: Boolean,
)

@Serializable
data class ArchiveResearchRun(
    val id: String,
    val scope: String,
    val symbolsJson: String,
    val state: String,
    val totalCount: Int,
    val completedCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val cancellationRequested: Boolean,
    val errorMessage: String? = null,
    val includePortfolioDataForAi: Boolean,
    val aiProviderId: String? = null,
    val triggerSource: String = "MANUAL",
    val automaticReportSlot: String? = null,
    val totalBudget: Double = 1_000_000.0,
    val perSymbolBudget: Double = 80_000.0,
    val maxStockPrice: Double? = null,
    val configVersion: Int = 1,
)

@Serializable
data class ArchiveReport(
    val id: String,
    val runId: String,
    val title: String,
    val deterministicBody: String,
    val aiExplanation: String? = null,
    val createdAt: Long,
)

@Serializable
data class ArchiveCandidate(
    val id: String,
    val runId: String,
    val symbol: String,
    val name: String,
    val score: Double,
    val risk: String,
    val reason: String,
    val createdAt: Long,
)

@Serializable
data class ArchiveSimulationPortfolio(
    val id: String,
    val runId: String,
    val name: String,
    val holdingsJson: String,
    val score: Double,
    val createdAt: Long,
)

@Serializable
data class ArchiveBacktest(
    val id: String,
    val startDate: String,
    val endDate: String,
    val initialCash: Double,
    val benchmark: String,
    val reportId: String? = null,
    val feeRate: Double,
    val status: String,
    val metricsJson: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)

@Serializable
data class ArchiveBacktestTrade(
    val id: String,
    val backtestId: String,
    val symbol: String,
    val name: String,
    val action: String,
    val date: String,
    val price: Double,
    val quantity: Int,
    val amount: Double,
    val fee: Double,
    val reason: String,
)

@Serializable
data class ArchiveChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

@Serializable
data class ArchiveChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val body: String,
    val createdAt: Long,
    val selectedSymbol: String? = null,
    val includedPortfolio: Boolean,
)

data class ArchiveImportSummary(
    val holdings: Int,
    val watchlist: Int,
    val reports: Int,
    val messages: Int,
    val deleted: Int = 0,
    val skippedConflicts: Int = 0,
    val alreadyApplied: Boolean = false,
    val backtests: Int = 0,
    val backtestTrades: Int = 0,
)
