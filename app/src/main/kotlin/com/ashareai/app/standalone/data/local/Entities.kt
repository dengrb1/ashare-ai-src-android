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
