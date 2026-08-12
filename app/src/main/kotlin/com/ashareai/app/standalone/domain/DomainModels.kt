package com.ashareai.app.standalone.domain

import java.time.LocalDate

data class Security(
    val symbol: String,
    val name: String,
    val exchange: String,
)

enum class MarketFreshness {
    FRESH,
    STALE,
    UNAVAILABLE,
}

data class MarketQuote(
    val symbol: String,
    val name: String,
    val lastPrice: Double?,
    val previousClose: Double?,
    val changePercent: Double?,
    val volume: Double?,
    val provider: String,
    val fetchedAt: Long,
    val freshness: MarketFreshness,
) {
    val isUsable: Boolean
        get() = lastPrice != null && freshness != MarketFreshness.UNAVAILABLE
}

data class DailyCandle(
    val symbol: String,
    val tradingDate: LocalDate,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double?,
    val provider: String,
    val fetchedAt: Long,
)

data class Holding(
    val symbol: String,
    val name: String,
    val quantity: Double,
    val averageCost: Double,
    val updatedAt: Long,
)

data class WatchlistItem(
    val symbol: String,
    val name: String,
    val addedAt: Long,
)

enum class AlertKind {
    STOP_LOSS,
    PROFIT_EXIT,
    BUY_ZONE,
    MANUAL_PRICE,
}

data class AlertRule(
    val id: String,
    val symbol: String,
    val name: String,
    val kind: AlertKind,
    val lowerBound: Double?,
    val upperBound: Double?,
    val enabled: Boolean,
    val expiresAt: Long?,
    val cooldownMinutes: Int,
    val lastTriggeredAt: Long?,
    val configJson: String,
)

data class AlertEvent(
    val ruleId: String,
    val symbol: String,
    val title: String,
    val body: String,
    val priority: NotificationPriority,
)

enum class NotificationPriority {
    NORMAL,
    WARNING,
    PROGRESS,
}

data class LocalNotification(
    val id: String,
    val title: String,
    val body: String,
    val priority: NotificationPriority,
    val deepLink: String,
    val createdAt: Long,
    val isRead: Boolean,
)

enum class ResearchScope {
    HOLDINGS,
    WATCHLIST,
    CUSTOM,
    MARKET,
}

enum class ResearchRunState {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    SUCCEEDED,
    FAILED,
}

data class ResearchRequest(
    val scope: ResearchScope,
    val symbols: List<String>,
    val marketLimit: Int,
    val includePortfolioDataForAi: Boolean,
    val aiProviderId: String?,
)

data class ResearchRun(
    val id: String,
    val scope: ResearchScope,
    val symbols: List<String>,
    val state: ResearchRunState,
    val totalCount: Int,
    val completedCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val cancellationRequested: Boolean,
    val errorMessage: String?,
    val includePortfolioDataForAi: Boolean = false,
    val aiProviderId: String? = null,
)

data class ResearchScore(
    val trend: Double?,
    val movingAverage: Double?,
    val macd: Double?,
    val rsi: Double?,
    val atr: Double?,
    val volatility: Double?,
    val volume: Double?,
    val freshness: Double,
    val total: Double,
    val unavailable: List<String>,
    val baseTotal: Double = total,
    val marketContext: com.ashareai.app.standalone.research.MarketIndexContext =
        com.ashareai.app.standalone.research.MarketIndexContext.unknown(),
)

data class ResearchResult(
    val symbol: String,
    val name: String,
    val score: ResearchScore,
    val risk: String,
    val summary: String,
    val quote: MarketQuote?,
)

data class ResearchReport(
    val id: String,
    val runId: String,
    val title: String,
    val deterministicBody: String,
    val aiExplanation: String?,
    val createdAt: Long,
)

data class ResearchCandidate(
    val id: String,
    val runId: String,
    val symbol: String,
    val name: String,
    val score: Double,
    val risk: String,
    val reason: String,
    val createdAt: Long,
)

data class SimulationPortfolio(
    val id: String,
    val runId: String,
    val name: String,
    val holdingsJson: String,
    val score: Double,
    val createdAt: Long,
)

data class AiProvider(
    val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val organization: String?,
    val project: String?,
    val enabled: Boolean,
    val createdAt: Long,
)

data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
)

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val role: String,
    val body: String,
    val createdAt: Long,
    val selectedSymbol: String?,
    val includedPortfolio: Boolean,
)
