package com.ashareai.app.standalone.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.standaloneDataStore by preferencesDataStore(name = "standalone_settings")

data class LocalSettings(
    val firstRun: Boolean = true,
    val monitoringEnabled: Boolean = true,
    val monitoringIntervalSeconds: Int = 30,
    val alertsEnabled: Boolean = true,
    val islandEnabled: Boolean = true,
    val dailyResearchEnabled: Boolean = true,
    val dailyReportAEnabled: Boolean = true,
    val dailyReportBEnabled: Boolean = false,
    val marketScanLimit: Int = 100,
    val portfolioDataAllowedForAi: Boolean = false,
    val lastDailyScheduleAt: Long = 0,
)

class SettingsStore(
    private val context: Context,
) {
    val settings: Flow<LocalSettings> = context.standaloneDataStore.data.map { preferences ->
        LocalSettings(
            firstRun = preferences[FIRST_RUN] ?: true,
            monitoringEnabled = preferences[MONITORING_ENABLED] ?: true,
            monitoringIntervalSeconds = (preferences[MONITOR_INTERVAL_SECONDS] ?: 30).coerceIn(15, 300),
            alertsEnabled = preferences[ALERTS_ENABLED] ?: true,
            islandEnabled = preferences[ISLAND_ENABLED] ?: true,
            dailyResearchEnabled = preferences[DAILY_RESEARCH_ENABLED] ?: true,
            dailyReportAEnabled = preferences[DAILY_REPORT_A_ENABLED] ?: true,
            dailyReportBEnabled = preferences[DAILY_REPORT_B_ENABLED] ?: false,
            marketScanLimit = (preferences[MARKET_SCAN_LIMIT] ?: 100).coerceIn(1, 500),
            portfolioDataAllowedForAi = preferences[PORTFOLIO_DATA_ALLOWED_FOR_AI] ?: false,
            lastDailyScheduleAt = preferences[LAST_DAILY_SCHEDULE_AT] ?: 0,
        )
    }

    suspend fun setFirstRunComplete() = setBoolean(FIRST_RUN, false)

    suspend fun setMonitoringEnabled(enabled: Boolean) = setBoolean(MONITORING_ENABLED, enabled)

    suspend fun setMonitoringIntervalSeconds(seconds: Int) {
        context.standaloneDataStore.edit {
            it[MONITOR_INTERVAL_SECONDS] = seconds.coerceIn(15, 300)
        }
    }

    suspend fun setAlertsEnabled(enabled: Boolean) = setBoolean(ALERTS_ENABLED, enabled)

    suspend fun setIslandEnabled(enabled: Boolean) = setBoolean(ISLAND_ENABLED, enabled)

    suspend fun setDailyResearchEnabled(enabled: Boolean) = setBoolean(DAILY_RESEARCH_ENABLED, enabled)

    suspend fun setDailyReportAEnabled(enabled: Boolean) = setBoolean(DAILY_REPORT_A_ENABLED, enabled)

    suspend fun setDailyReportBEnabled(enabled: Boolean) = setBoolean(DAILY_REPORT_B_ENABLED, enabled)

    suspend fun setMarketScanLimit(limit: Int) {
        context.standaloneDataStore.edit {
            it[MARKET_SCAN_LIMIT] = limit.coerceIn(1, 500)
        }
    }

    suspend fun setPortfolioDataAllowedForAi(allowed: Boolean) = setBoolean(PORTFOLIO_DATA_ALLOWED_FOR_AI, allowed)

    suspend fun setLastDailyScheduleAt(epochMillis: Long) {
        context.standaloneDataStore.edit {
            it[LAST_DAILY_SCHEDULE_AT] = epochMillis
        }
    }

    private suspend fun setBoolean(key: androidx.datastore.preferences.core.Preferences.Key<Boolean>, value: Boolean) {
        context.standaloneDataStore.edit {
            it[key] = value
        }
    }

    private companion object {
        val FIRST_RUN = booleanPreferencesKey("first_run")
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")
        val MONITOR_INTERVAL_SECONDS = intPreferencesKey("monitor_interval_seconds")
        val ALERTS_ENABLED = booleanPreferencesKey("alerts_enabled")
        val ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        val DAILY_RESEARCH_ENABLED = booleanPreferencesKey("daily_research_enabled")
        val DAILY_REPORT_A_ENABLED = booleanPreferencesKey("daily_report_a_enabled")
        val DAILY_REPORT_B_ENABLED = booleanPreferencesKey("daily_report_b_enabled")
        val MARKET_SCAN_LIMIT = intPreferencesKey("market_scan_limit")
        val PORTFOLIO_DATA_ALLOWED_FOR_AI = booleanPreferencesKey("portfolio_data_allowed_for_ai")
        val LAST_DAILY_SCHEDULE_AT = longPreferencesKey("last_daily_schedule_at")
    }
}
