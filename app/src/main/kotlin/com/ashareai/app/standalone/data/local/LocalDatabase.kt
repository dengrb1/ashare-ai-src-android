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
        LocalBacktestEntity::class,
        LocalBacktestTradeEntity::class,
    ],
    version = 4,
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE research_runs ADD COLUMN triggerSource TEXT NOT NULL DEFAULT 'MANUAL'")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN automaticReportSlot TEXT")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN totalBudget REAL NOT NULL DEFAULT 1000000")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN perSymbolBudget REAL NOT NULL DEFAULT 80000")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN maxStockPrice REAL")
                db.execSQL("ALTER TABLE research_runs ADD COLUMN configVersion INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_backtests (
                        id TEXT NOT NULL PRIMARY KEY,
                        startDate TEXT NOT NULL,
                        endDate TEXT NOT NULL,
                        initialCash REAL NOT NULL,
                        benchmark TEXT NOT NULL,
                        reportId TEXT,
                        feeRate REAL NOT NULL,
                        status TEXT NOT NULL,
                        metricsJson TEXT,
                        errorMessage TEXT,
                        createdAt INTEGER NOT NULL,
                        completedAt INTEGER
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_backtest_trades (
                        id TEXT NOT NULL PRIMARY KEY,
                        backtestId TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        name TEXT NOT NULL,
                        action TEXT NOT NULL,
                        date TEXT NOT NULL,
                        price REAL NOT NULL,
                        quantity INTEGER NOT NULL,
                        amount REAL NOT NULL,
                        fee REAL NOT NULL,
                        reason TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }

        fun create(context: Context): LocalDatabase = Room.databaseBuilder(
            context.applicationContext,
            LocalDatabase::class.java,
            DATABASE_NAME,
        ).addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
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
