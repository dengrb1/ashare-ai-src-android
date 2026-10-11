package com.ashareai.app.standalone.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalDao {
    @Query("SELECT * FROM holdings ORDER BY symbol")
    fun observeHoldings(): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings ORDER BY symbol")
    suspend fun holdings(): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE symbol = :symbol LIMIT 1")
    suspend fun holding(symbol: String): HoldingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHolding(holding: HoldingEntity)

    @Query("DELETE FROM holdings WHERE symbol = :symbol")
    suspend fun deleteHolding(symbol: String)

    @Query("SELECT * FROM watchlist ORDER BY symbol")
    fun observeWatchlist(): Flow<List<WatchlistEntity>>

    @Query("SELECT * FROM watchlist ORDER BY symbol")
    suspend fun watchlist(): List<WatchlistEntity>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol LIMIT 1")
    suspend fun watchlistItem(symbol: String): WatchlistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchlist(item: WatchlistEntity)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun deleteWatchlist(symbol: String)

    @Query("SELECT * FROM quotes WHERE symbol IN (:symbols)")
    suspend fun quotesFor(symbols: List<String>): List<QuoteEntity>

    @Query("SELECT * FROM quotes ORDER BY fetchedAt DESC")
    fun observeQuotes(): Flow<List<QuoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertQuotes(quotes: List<QuoteEntity>)

    @Query("DELETE FROM quotes")
    suspend fun clearQuotes()

    @Query("SELECT * FROM candles WHERE symbol = :symbol ORDER BY tradingDate ASC")
    suspend fun candlesFor(symbol: String): List<CandleEntity>

    @Query("SELECT * FROM candles WHERE symbol = :symbol AND tradingDate = :date LIMIT 1")
    suspend fun getCandleByDate(symbol: String, date: String): CandleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandles(candles: List<CandleEntity>)

    @Query("DELETE FROM candles WHERE symbol = :symbol")
    suspend fun clearCandles(symbol: String)

    @Query("DELETE FROM candles")
    suspend fun clearCandles()

    @Query("SELECT * FROM alerts ORDER BY symbol, kind")
    fun observeAlerts(): Flow<List<AlertRuleEntity>>

    @Query("SELECT * FROM alerts WHERE enabled = 1")
    suspend fun activeAlerts(): List<AlertRuleEntity>

    @Query("SELECT * FROM alerts ORDER BY symbol, kind")
    suspend fun alerts(): List<AlertRuleEntity>

    @Query("SELECT * FROM alerts WHERE id = :id LIMIT 1")
    suspend fun alert(id: String): AlertRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlert(alert: AlertRuleEntity)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteAlert(id: String)

    @Query("DELETE FROM local_notifications WHERE id = :id")
    suspend fun deleteNotification(id: String)

    @Query("UPDATE alerts SET lastTriggeredAt = :at WHERE id = :id")
    suspend fun markAlertTriggered(id: String, at: Long)

    @Query("SELECT * FROM local_notifications ORDER BY createdAt DESC")
    fun observeNotifications(): Flow<List<LocalNotificationEntity>>

    @Query("SELECT COUNT(*) FROM local_notifications WHERE isRead = 0")
    fun observeUnreadNotificationCount(): Flow<Int>

    @Query("SELECT * FROM local_notifications ORDER BY createdAt DESC")
    suspend fun notifications(): List<LocalNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNotification(notification: LocalNotificationEntity)

    @Query("UPDATE local_notifications SET isRead = 1 WHERE id = :id")
    suspend fun markNotificationRead(id: String)

    @Query("UPDATE local_notifications SET isRead = 1")
    suspend fun markAllNotificationsRead()

    @Query("DELETE FROM local_notifications")
    suspend fun clearNotifications()

    @Query("SELECT * FROM monitoring_events ORDER BY occurredAt DESC LIMIT :limit")
    fun observeMonitoringEvents(limit: Int = 100): Flow<List<MonitoringEventEntity>>

    @Query("SELECT * FROM monitoring_events ORDER BY occurredAt DESC")
    suspend fun monitoringEvents(): List<MonitoringEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMonitoringEvent(event: MonitoringEventEntity)

    @Query("SELECT * FROM monitoring_events WHERE id = :id LIMIT 1")
    suspend fun monitoringEvent(id: String): MonitoringEventEntity?

    @Query("UPDATE monitoring_events SET isRead = 1 WHERE id = :id")
    suspend fun markMonitoringEventRead(id: String)

    @Query("DELETE FROM monitoring_events WHERE occurredAt < :before")
    suspend fun deleteMonitoringEventsBefore(before: Long)

    @Query("SELECT * FROM research_runs ORDER BY updatedAt DESC")
    fun observeResearchRuns(): Flow<List<ResearchRunEntity>>

    @Query("SELECT * FROM research_runs WHERE id = :id")
    suspend fun researchRun(id: String): ResearchRunEntity?

    @Query("SELECT * FROM research_runs ORDER BY updatedAt DESC")
    suspend fun allResearchRuns(): List<ResearchRunEntity>

    @Query("SELECT * FROM research_runs WHERE state IN ('QUEUED', 'RUNNING', 'CANCELLING') ORDER BY updatedAt ASC")
    suspend fun recoverableResearchRuns(): List<ResearchRunEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResearchRun(run: ResearchRunEntity)

    @Query("DELETE FROM research_runs WHERE id = :id")
    suspend fun deleteResearchRun(id: String)

    @Query("UPDATE research_runs SET state = :state, completedCount = :completedCount, updatedAt = :updatedAt, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateResearchRun(
        id: String,
        state: String,
        completedCount: Int,
        updatedAt: Long,
        errorMessage: String?,
    )

    @Query("UPDATE research_runs SET cancellationRequested = 1, state = 'CANCELLING', updatedAt = :updatedAt WHERE id = :id")
    suspend fun requestResearchCancellation(id: String, updatedAt: Long)

    @Query("SELECT * FROM research_reports ORDER BY createdAt DESC")
    fun observeReports(): Flow<List<ResearchReportEntity>>

    @Query("SELECT * FROM research_reports ORDER BY createdAt DESC")
    suspend fun reports(): List<ResearchReportEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReport(report: ResearchReportEntity)

    @Query("DELETE FROM research_reports WHERE id = :id")
    suspend fun deleteReport(id: String)

    @Query("SELECT * FROM research_candidates ORDER BY score DESC, symbol")
    fun observeCandidates(): Flow<List<ResearchCandidateEntity>>

    @Query("SELECT * FROM research_candidates ORDER BY score DESC, symbol")
    suspend fun candidates(): List<ResearchCandidateEntity>

    @Query("DELETE FROM research_candidates WHERE runId = :runId")
    suspend fun deleteCandidatesForRun(runId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCandidates(candidates: List<ResearchCandidateEntity>)

    @Query("DELETE FROM research_candidates WHERE id = :id")
    suspend fun deleteCandidate(id: String)

    @Query("SELECT * FROM simulation_portfolios ORDER BY createdAt DESC")
    fun observeSimulationPortfolios(): Flow<List<SimulationPortfolioEntity>>

    @Query("SELECT * FROM simulation_portfolios ORDER BY createdAt DESC")
    suspend fun simulationPortfolios(): List<SimulationPortfolioEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSimulationPortfolio(portfolio: SimulationPortfolioEntity)

    @Query("DELETE FROM simulation_portfolios WHERE id = :id")
    suspend fun deleteSimulationPortfolio(id: String)

    @Query("SELECT * FROM ai_providers ORDER BY createdAt ASC")
    fun observeAiProviders(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY createdAt ASC")
    suspend fun aiProviders(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :id")
    suspend fun aiProvider(id: String): AiProviderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiProvider(provider: AiProviderEntity)

    @Query("DELETE FROM ai_providers WHERE id = :id")
    suspend fun deleteAiProvider(id: String)

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun observeChatSessions(): Flow<List<ChatSessionEntity>>

    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    suspend fun chatSessions(): List<ChatSessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatSession(session: ChatSessionEntity)

    @Query("DELETE FROM chat_sessions WHERE id = :id")
    suspend fun deleteChatSession(id: String)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeChatMessages(sessionId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages ORDER BY createdAt ASC")
    suspend fun chatMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChatMessage(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE id = :id")
    suspend fun deleteChatMessage(id: String)

    // 回测相关操作
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktest(backtest: LocalBacktestEntity)

    @Query("UPDATE local_backtests SET status = :status, metricsJson = :metricsJson, errorMessage = :errorMessage, completedAt = :completedAt WHERE id = :id")
    suspend fun updateBacktestResult(id: String, status: String, metricsJson: String?, errorMessage: String?, completedAt: Long)

    @Query("SELECT status FROM local_backtests WHERE id = :id")
    suspend fun getBacktestStatus(id: String): String?

    @Query("SELECT metricsJson FROM local_backtests WHERE id = :id")
    suspend fun getBacktestMetricsJson(id: String): String?

    @Query("SELECT errorMessage FROM local_backtests WHERE id = :id")
    suspend fun getBacktestErrorMessage(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBacktestTrade(trade: LocalBacktestTradeEntity)

    @Query("SELECT * FROM local_backtest_trades WHERE backtestId = :backtestId ORDER BY date ASC")
    suspend fun getBacktestTrades(backtestId: String): List<LocalBacktestTradeEntity>

    @Query("SELECT * FROM local_backtests ORDER BY createdAt DESC LIMIT :limit")
    fun listBacktests(limit: Int): Flow<List<LocalBacktestEntity>>

    @Query("SELECT * FROM local_backtests ORDER BY createdAt DESC")
    suspend fun allBacktests(): List<LocalBacktestEntity>

    @Query("SELECT * FROM local_backtests WHERE id = :id LIMIT 1")
    suspend fun backtest(id: String): LocalBacktestEntity?

    @Query("SELECT * FROM local_backtest_trades ORDER BY date ASC, id ASC")
    suspend fun allBacktestTrades(): List<LocalBacktestTradeEntity>

    @Query("DELETE FROM local_backtests WHERE id = :id")
    suspend fun deleteBacktest(id: String)

    @Query("DELETE FROM local_backtest_trades WHERE backtestId = :backtestId")
    suspend fun deleteBacktestTrades(backtestId: String)

    @Query("DELETE FROM local_backtest_trades WHERE id = :id")
    suspend fun deleteBacktestTrade(id: String)

    @Query("SELECT * FROM research_candidates WHERE runId = :reportId ORDER BY score DESC")
    suspend fun getCandidatesByReportId(reportId: String): List<ResearchCandidateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncOperation(operation: SyncOperationEntity)

    @Query("SELECT * FROM sync_operations WHERE idempotencyKey = :key LIMIT 1")
    suspend fun syncOperation(key: String): SyncOperationEntity?

    @Query("SELECT * FROM sync_operations ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun syncOperations(limit: Int = 50): List<SyncOperationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncTombstone(tombstone: SyncTombstoneEntity)

    @Query("SELECT * FROM sync_tombstones WHERE collection = :collection")
    suspend fun syncTombstones(collection: String): List<SyncTombstoneEntity>

    @Query("SELECT * FROM sync_tombstones")
    suspend fun allSyncTombstones(): List<SyncTombstoneEntity>

    @Query("DELETE FROM sync_tombstones WHERE collection = :collection AND recordKey = :key")
    suspend fun clearSyncTombstone(collection: String, key: String)

    @Query("SELECT * FROM sync_baselines WHERE accountKey = :accountKey LIMIT 1")
    suspend fun syncBaseline(accountKey: String): SyncBaselineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncBaseline(baseline: SyncBaselineEntity)

    @Query("DELETE FROM sync_baselines WHERE accountKey = :accountKey")
    suspend fun deleteSyncBaseline(accountKey: String)
}
