package com.ashareai.app.standalone.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holdings")
data class HoldingEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val quantity: Double,
    val averageCost: Double,
    val updatedAt: Long,
)

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val addedAt: Long,
)

@Entity(tableName = "quotes")
data class QuoteEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val lastPrice: Double?,
    val previousClose: Double?,
    val changePercent: Double?,
    val volume: Double?,
    val provider: String,
    val fetchedAt: Long,
)

@Entity(
    tableName = "candles",
    primaryKeys = ["symbol", "tradingDate"],
)
data class CandleEntity(
    val symbol: String,
    val tradingDate: String,
    val open: Double,
    val close: Double,
    val high: Double,
    val low: Double,
    val volume: Double?,
    val provider: String,
    val fetchedAt: Long,
)

@Entity(tableName = "alerts")
data class AlertRuleEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val kind: String,
    val lowerBound: Double?,
    val upperBound: Double?,
    val enabled: Boolean,
    val expiresAt: Long?,
    val cooldownMinutes: Int,
    val lastTriggeredAt: Long?,
    val configJson: String,
)

@Entity(tableName = "local_notifications")
data class LocalNotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val priority: String,
    val deepLink: String,
    val createdAt: Long,
    val isRead: Boolean,
    val payloadJson: String,
)

@Entity(tableName = "monitoring_events")
data class MonitoringEventEntity(
    @PrimaryKey val id: String,
    val symbol: String,
    val name: String,
    val type: String,
    val occurredAt: Long,
    val severity: String,
    val reportId: String?,
    val payloadJson: String,
    val isRead: Boolean,
)

@Entity(tableName = "research_runs")
data class ResearchRunEntity(
    @PrimaryKey val id: String,
    val scope: String,
    val symbolsJson: String,
    val state: String,
    val totalCount: Int,
    val completedCount: Int,
    val startedAt: Long,
    val updatedAt: Long,
    val cancellationRequested: Boolean,
    val errorMessage: String?,
    val includePortfolioDataForAi: Boolean,
    val aiProviderId: String?,
    val triggerSource: String,
    val automaticReportSlot: String?,
    val totalBudget: Double,
    val perSymbolBudget: Double,
    val maxStockPrice: Double?,
    val configVersion: Int,
)

@Entity(tableName = "research_reports")
data class ResearchReportEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val title: String,
    val deterministicBody: String,
    val aiExplanation: String?,
    val createdAt: Long,
    val engineVersion: String = "research-v2",
    val signalSummaryJson: String? = null,
    val monitoringEventCount: Int = 0,
)

@Entity(tableName = "research_candidates")
data class ResearchCandidateEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val symbol: String,
    val name: String,
    val score: Double,
    val risk: String,
    val reason: String,
    val createdAt: Long,
    val trendPhase: String = "UNKNOWN",
    val trendSignal: String = "UNKNOWN",
    val volumePriceSignal: String = "UNKNOWN",
    val capitalActivityProxy: Double? = null,
    val freshness: String = "UNKNOWN",
)

@Entity(tableName = "simulation_portfolios")
data class SimulationPortfolioEntity(
    @PrimaryKey val id: String,
    val runId: String,
    val name: String,
    val holdingsJson: String,
    val score: Double,
    val createdAt: Long,
)

@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val encryptedApiKey: String,
    val model: String,
    val organization: String?,
    val project: String?,
    val enabled: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val updatedAt: Long,
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val sessionId: String,
    val role: String,
    val body: String,
    val createdAt: Long,
    val selectedSymbol: String?,
    val includedPortfolio: Boolean,
)

@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    @PrimaryKey val idempotencyKey: String,
    val direction: String,
    val scope: String,
    val state: String,
    val previewJson: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "sync_tombstones", primaryKeys = ["collection", "recordKey"])
data class SyncTombstoneEntity(
    val collection: String,
    val recordKey: String,
    val deletedAt: Long,
    val sourceRevision: Long? = null,
)

/** Last confirmed connected snapshot, isolated by authenticated account and server URL. */
@Entity(tableName = "sync_baselines")
data class SyncBaselineEntity(
    @PrimaryKey val accountKey: String,
    val snapshotJson: String,
    val savedAt: Long,
)
