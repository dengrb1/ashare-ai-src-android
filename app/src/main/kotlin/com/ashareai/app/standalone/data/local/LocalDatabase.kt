package com.ashareai.app.standalone.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HoldingEntity::class,
        WatchlistEntity::class,
        QuoteEntity::class,
        CandleEntity::class,
        AlertRuleEntity::class,
        LocalNotificationEntity::class,
        ResearchRunEntity::class,
        ResearchReportEntity::class,
        ResearchCandidateEntity::class,
        SimulationPortfolioEntity::class,
        AiProviderEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun localDao(): LocalDao

    companion object {
        const val DATABASE_NAME = "ashare_standalone.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                createLatestTables(db)
            }
        }

        fun create(context: Context): LocalDatabase = Room.databaseBuilder(
            context.applicationContext,
            LocalDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(
            MIGRATION_1_2,
        ).enableMultiInstanceInvalidation()
            .build()

        private fun createLatestTables(database: SupportSQLiteDatabase) {
            listOf(
                "CREATE TABLE IF NOT EXISTS holdings (symbol TEXT NOT NULL, name TEXT NOT NULL, quantity REAL NOT NULL, averageCost REAL NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(symbol))",
                "CREATE TABLE IF NOT EXISTS watchlist (symbol TEXT NOT NULL, name TEXT NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(symbol))",
                "CREATE TABLE IF NOT EXISTS quotes (symbol TEXT NOT NULL, name TEXT NOT NULL, lastPrice REAL, previousClose REAL, changePercent REAL, volume REAL, provider TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(symbol))",
                "CREATE TABLE IF NOT EXISTS candles (symbol TEXT NOT NULL, tradingDate TEXT NOT NULL, open REAL NOT NULL, close REAL NOT NULL, high REAL NOT NULL, low REAL NOT NULL, volume REAL, provider TEXT NOT NULL, fetchedAt INTEGER NOT NULL, PRIMARY KEY(symbol, tradingDate))",
                "CREATE TABLE IF NOT EXISTS alerts (id TEXT NOT NULL, symbol TEXT NOT NULL, name TEXT NOT NULL, kind TEXT NOT NULL, lowerBound REAL, upperBound REAL, enabled INTEGER NOT NULL, expiresAt INTEGER, cooldownMinutes INTEGER NOT NULL, lastTriggeredAt INTEGER, configJson TEXT NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS local_notifications (id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, priority TEXT NOT NULL, deepLink TEXT NOT NULL, createdAt INTEGER NOT NULL, isRead INTEGER NOT NULL, payloadJson TEXT NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS research_runs (id TEXT NOT NULL, scope TEXT NOT NULL, symbolsJson TEXT NOT NULL, state TEXT NOT NULL, totalCount INTEGER NOT NULL, completedCount INTEGER NOT NULL, startedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, cancellationRequested INTEGER NOT NULL, errorMessage TEXT, includePortfolioDataForAi INTEGER NOT NULL, aiProviderId TEXT, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS research_reports (id TEXT NOT NULL, runId TEXT NOT NULL, title TEXT NOT NULL, deterministicBody TEXT NOT NULL, aiExplanation TEXT, createdAt INTEGER NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS research_candidates (id TEXT NOT NULL, runId TEXT NOT NULL, symbol TEXT NOT NULL, name TEXT NOT NULL, score REAL NOT NULL, risk TEXT NOT NULL, reason TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS simulation_portfolios (id TEXT NOT NULL, runId TEXT NOT NULL, name TEXT NOT NULL, holdingsJson TEXT NOT NULL, score REAL NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS ai_providers (id TEXT NOT NULL, name TEXT NOT NULL, baseUrl TEXT NOT NULL, encryptedApiKey TEXT NOT NULL, model TEXT NOT NULL, organization TEXT, project TEXT, enabled INTEGER NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS chat_sessions (id TEXT NOT NULL, title TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))",
                "CREATE TABLE IF NOT EXISTS chat_messages (id TEXT NOT NULL, sessionId TEXT NOT NULL, role TEXT NOT NULL, body TEXT NOT NULL, createdAt INTEGER NOT NULL, selectedSymbol TEXT, includedPortfolio INTEGER NOT NULL, PRIMARY KEY(id))",
            ).forEach(database::execSQL)
        }
    }
}
