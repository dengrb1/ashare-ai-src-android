package com.ashareai.app.standalone

import android.content.Context
import com.ashareai.app.standalone.alerts.AlertEvaluator
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.ai.AiProviderRepository
import com.ashareai.app.standalone.data.ai.ApiKeyCipher
import com.ashareai.app.standalone.data.ai.OpenAiCompatibleClient
import com.ashareai.app.standalone.data.archive.LocalArchiveService
import com.ashareai.app.standalone.data.local.LocalDatabase
import com.ashareai.app.standalone.data.market.EastMoneyMarketDataProvider
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.data.settings.SettingsStore
import com.ashareai.app.standalone.monitor.MarketMonitoringCoordinator
import com.ashareai.app.standalone.monitor.MonitoringFallbackScheduler
import com.ashareai.app.standalone.notifications.NotificationRepository
import com.ashareai.app.standalone.research.DeterministicResearchEngine
import com.ashareai.app.standalone.research.ResearchCoordinator
import com.ashareai.app.standalone.work.DailyResearchScheduler
import com.ashareai.app.standalone.work.ShanghaiTradingCalendar
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: LocalDatabase = LocalDatabase.create(appContext)
    val local = LocalRepository(database.localDao())
    val settings = SettingsStore(appContext)
    private val islandEnabled = AtomicBoolean(true)
    val calendar = ShanghaiTradingCalendar()
    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()
    val market = MarketRepository(
        local = local,
        provider = EastMoneyMarketDataProvider(httpClient),
    )
    val notifications = NotificationRepository(
        context = appContext,
        local = local,
        attachIslandPayload = { islandEnabled.get() },
    )
    val alertEvaluator = AlertEvaluator()
    val aiProviders = AiProviderRepository(local, ApiKeyCipher())
    val aiClient = OpenAiCompatibleClient(aiProviders, httpClient)
    val archive = LocalArchiveService(local)
    val backtest = com.ashareai.app.standalone.backtest.BacktestService(
        engine = com.ashareai.app.standalone.backtest.LocalBacktestEngine(market, local),
        local = local,
    )
    val financialSearch = com.ashareai.app.standalone.search.LocalFinancialSearchRepository(
        market = market,
        httpClient = httpClient,
    )
    val research = ResearchCoordinator(
        context = appContext,
        local = local,
        market = market,
        engine = DeterministicResearchEngine(),
        aiClient = aiClient,
        notifications = notifications,
    )
    val monitoring = MarketMonitoringCoordinator(
        local = local,
        market = market,
        settings = settings,
        alerts = alertEvaluator,
        notifications = notifications,
        calendar = calendar,
    )
    val dailyResearchScheduler = DailyResearchScheduler(appContext, settings, calendar)
    val monitoringFallbackScheduler = MonitoringFallbackScheduler(appContext)

    init {
        appScope.launch {
            settings.settings.collect { islandEnabled.set(it.islandEnabled) }
        }
    }

    fun onMainProcessStarted() {
        appScope.launch {
            dailyResearchScheduler.schedule()
            research.recoverPendingRuns()
        }
    }
}
