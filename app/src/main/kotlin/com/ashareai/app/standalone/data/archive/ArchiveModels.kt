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
    val chatSessions: List<ArchiveChatSession> = emptyList(),
    val chatMessages: List<ArchiveChatMessage> = emptyList(),
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
)
