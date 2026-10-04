package com.ashareai.app.standalone.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.data.ai.AiAgentConfig
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
    val darkMode: String = "system",
    val glassEnabled: Boolean = true,
    val fullAnimationsEnabled: Boolean = true,
    val lastDailyScheduleAt: Long = 0,
    val automaticReports: List<AutomaticResearchReportConfig> = defaultAutomaticReports(),
)

data class AutomaticResearchReportConfig(
    val slot: String,
    val enabled: Boolean,
    val scope: ResearchScope,
    val symbols: List<String>,
    val totalBudget: Double,
    val perSymbolBudget: Double,
    val maxStockPrice: Double?,
    val marketLimit: Int,
    val configVersion: Int = 1,
)

fun defaultAutomaticReports(): List<AutomaticResearchReportConfig> = listOf(
    AutomaticResearchReportConfig("A", true, ResearchScope.MARKET, emptyList(), 1_000_000.0, 80_000.0, null, 100),
    AutomaticResearchReportConfig("B", false, ResearchScope.MARKET, emptyList(), 1_000_000.0, 80_000.0, null, 100),
)

class SettingsStore(
    private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<LocalSettings> = context.standaloneDataStore.data.map { preferences ->
        val automaticReports = listOf(
            automaticReport(
                "A",
                preferences,
                preferences[reportEnabledKey("A")] ?: preferences[DAILY_REPORT_A_ENABLED] ?: true,
            ),
            automaticReport(
                "B",
                preferences,
                preferences[reportEnabledKey("B")] ?: preferences[DAILY_REPORT_B_ENABLED] ?: false,
            ),
        )
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
            darkMode = preferences[DARK_MODE] ?: "system",
            glassEnabled = preferences[GLASS_ENABLED] ?: true,
            fullAnimationsEnabled = preferences[FULL_ANIMATIONS_ENABLED] ?: true,
            lastDailyScheduleAt = preferences[LAST_DAILY_SCHEDULE_AT] ?: 0,
            automaticReports = automaticReports,
        )
    }

    val aiAgents: Flow<List<AiAgentConfig>> = context.standaloneDataStore.data.map { preferences ->
        decodeAgents(preferences[AI_AGENTS_JSON])
    }

    suspend fun saveAiAgent(agent: AiAgentConfig) {
        context.standaloneDataStore.edit { preferences ->
            val agents = decodeAgents(preferences[AI_AGENTS_JSON])
                .filterNot { it.id == agent.id } + agent
            preferences[AI_AGENTS_JSON] = json.encodeToString(agents)
        }
    }

    suspend fun removeAiAgent(id: String) {
        context.standaloneDataStore.edit { preferences ->
            val agents = decodeAgents(preferences[AI_AGENTS_JSON]).filterNot { it.id == id }
            if (agents.isEmpty()) preferences.remove(AI_AGENTS_JSON)
            else preferences[AI_AGENTS_JSON] = json.encodeToString(agents)
        }
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

    suspend fun saveAutomaticReports(reports: List<AutomaticResearchReportConfig>) {
        require(reports.map { it.slot }.toSet() == setOf("A", "B")) { "自动报告必须同时包含 A 和 B" }
        reports.forEach(::validateAutomaticReport)
        context.standaloneDataStore.edit { preferences ->
            reports.forEach { report ->
                preferences[reportEnabledKey(report.slot)] = report.enabled
                preferences[reportScopeKey(report.slot)] = report.scope.name
                preferences[reportSymbolsKey(report.slot)] = report.symbols.joinToString(",")
                preferences[reportTotalBudgetKey(report.slot)] = report.totalBudget
                preferences[reportPerSymbolBudgetKey(report.slot)] = report.perSymbolBudget
                preferences[reportMaxPriceKey(report.slot)] = report.maxStockPrice?.toString().orEmpty()
                preferences[reportMarketLimitKey(report.slot)] = report.marketLimit.coerceIn(1, 500)
                val versionKey = reportConfigVersionKey(report.slot)
                val previousVersion = preferences[versionKey] ?: report.configVersion
                preferences[versionKey] = maxOf(previousVersion, report.configVersion).coerceAtLeast(1) + 1
            }
            preferences[DAILY_REPORT_A_ENABLED] = reports.first { it.slot == "A" }.enabled
            preferences[DAILY_REPORT_B_ENABLED] = reports.first { it.slot == "B" }.enabled
            preferences[DAILY_RESEARCH_ENABLED] = reports.any { it.enabled }
        }
    }

    suspend fun setMarketScanLimit(limit: Int) {
        context.standaloneDataStore.edit {
            it[MARKET_SCAN_LIMIT] = limit.coerceIn(1, 500)
        }
    }

    suspend fun setPortfolioDataAllowedForAi(allowed: Boolean) = setBoolean(PORTFOLIO_DATA_ALLOWED_FOR_AI, allowed)

    suspend fun setDarkMode(mode: String) {
        require(mode in setOf("system", "light", "dark"))
        context.standaloneDataStore.edit { it[DARK_MODE] = mode }
    }

    suspend fun setGlassEnabled(enabled: Boolean) = setBoolean(GLASS_ENABLED, enabled)

    suspend fun setFullAnimationsEnabled(enabled: Boolean) = setBoolean(FULL_ANIMATIONS_ENABLED, enabled)

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

    private fun automaticReport(
        slot: String,
        preferences: androidx.datastore.preferences.core.Preferences,
        enabled: Boolean,
    ): AutomaticResearchReportConfig {
        val defaults = defaultAutomaticReports().first { it.slot == slot }
        val scope = preferences[reportScopeKey(slot)]
            ?.let { value -> runCatching { ResearchScope.valueOf(value) }.getOrNull() }
            ?.takeIf { it in setOf(ResearchScope.MARKET, ResearchScope.WATCHLIST, ResearchScope.CUSTOM) }
            ?: defaults.scope
        return defaults.copy(
            enabled = enabled,
            scope = scope,
            symbols = preferences[reportSymbolsKey(slot)].orEmpty()
                .split(",", " ", "\n")
                .map(String::trim)
                .filter { it.length == 6 && it.all(Char::isDigit) }
                .distinct(),
            totalBudget = (preferences[reportTotalBudgetKey(slot)] ?: defaults.totalBudget).coerceAtLeast(1.0),
            perSymbolBudget = (preferences[reportPerSymbolBudgetKey(slot)] ?: defaults.perSymbolBudget).coerceAtLeast(1.0),
            maxStockPrice = preferences[reportMaxPriceKey(slot)]?.toDoubleOrNull()?.takeIf { it > 0.0 },
            marketLimit = (preferences[reportMarketLimitKey(slot)] ?: defaults.marketLimit).coerceIn(1, 500),
            configVersion = (preferences[reportConfigVersionKey(slot)] ?: defaults.configVersion).coerceAtLeast(1),
        )
    }

    private fun decodeAgents(value: String?): List<AiAgentConfig> =
        value?.let { runCatching { json.decodeFromString<List<AiAgentConfig>>(it) }.getOrNull() }.orEmpty()

    private fun validateAutomaticReport(report: AutomaticResearchReportConfig) {
        require(report.slot in setOf("A", "B")) { "未知自动报告槽位" }
        require(report.scope in setOf(ResearchScope.MARKET, ResearchScope.WATCHLIST, ResearchScope.CUSTOM)) { "自动报告范围无效" }
        require(report.totalBudget > 0.0) { "报告 ${report.slot} 总预算必须大于 0" }
        require(report.perSymbolBudget > 0.0 && report.perSymbolBudget <= report.totalBudget) {
            "报告 ${report.slot} 单股预算必须大于 0 且不超过总预算"
        }
        require(report.maxStockPrice == null || report.maxStockPrice > 0.0) { "报告 ${report.slot} 最高股价必须大于 0" }
        require(!report.enabled || report.scope != ResearchScope.CUSTOM || report.symbols.isNotEmpty()) {
            "报告 ${report.slot} 至少需要一只股票"
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
        val DARK_MODE = stringPreferencesKey("dark_mode")
        val GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
        val FULL_ANIMATIONS_ENABLED = booleanPreferencesKey("full_animations_enabled")
        val LAST_DAILY_SCHEDULE_AT = longPreferencesKey("last_daily_schedule_at")
        val AI_AGENTS_JSON = stringPreferencesKey("ai_agents_json")

        fun reportEnabledKey(slot: String) = booleanPreferencesKey("automatic_report_${slot.lowercase()}_enabled")
        fun reportScopeKey(slot: String) = stringPreferencesKey("automatic_report_${slot.lowercase()}_scope")
        fun reportSymbolsKey(slot: String) = stringPreferencesKey("automatic_report_${slot.lowercase()}_symbols")
        fun reportTotalBudgetKey(slot: String) = doublePreferencesKey("automatic_report_${slot.lowercase()}_total_budget")
        fun reportPerSymbolBudgetKey(slot: String) = doublePreferencesKey("automatic_report_${slot.lowercase()}_per_symbol_budget")
        fun reportMaxPriceKey(slot: String) = stringPreferencesKey("automatic_report_${slot.lowercase()}_max_price")
        fun reportMarketLimitKey(slot: String) = intPreferencesKey("automatic_report_${slot.lowercase()}_market_limit")
        fun reportConfigVersionKey(slot: String) = intPreferencesKey("automatic_report_${slot.lowercase()}_config_version")
    }
}
