package com.ashareai.app.standalone.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.HybridApp
import com.ashareai.app.standalone.data.ai.AiPayloadBuilder
import com.ashareai.app.standalone.data.ai.AiAgentConfig
import com.ashareai.app.standalone.data.ai.AiAgentRouter
import com.ashareai.app.standalone.data.ai.AiExecutionTarget
import com.ashareai.app.standalone.data.ai.AiTaskType
import com.ashareai.app.standalone.data.ai.AiProviderDraft
import com.ashareai.app.standalone.data.ai.AiRequest
import com.ashareai.app.standalone.data.ai.AiStreamEvent
import com.ashareai.app.standalone.data.ai.LocalInferenceDetector
import com.ashareai.app.standalone.data.settings.AutomaticResearchReportConfig
import com.ashareai.app.standalone.data.archive.ArchiveMergePreview
import com.ashareai.app.standalone.data.archive.ArchiveMergeResolution
import com.ashareai.app.standalone.domain.AlertKind
import com.ashareai.app.standalone.domain.AlertRule
import com.ashareai.app.standalone.domain.ChatMessage
import com.ashareai.app.standalone.domain.ChatSession
import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.ResearchRequest
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.domain.WatchlistItem
import com.ashareai.app.data.comparableSymbol
import com.ashareai.app.standalone.monitor.MarketMonitorService
import com.ashareai.app.standalone.research.DeterministicResearchEngine
import com.ashareai.app.standalone.research.ExitResearch
import com.ashareai.app.standalone.research.ExitResearchEngine
import com.ashareai.app.standalone.research.ResearchBatchPlanner
import com.ashareai.app.standalone.research.TechnicalIndicators
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

data class MarketUiState(
    val query: String = "600519",
    val catalog: List<com.ashareai.app.standalone.domain.Security> = emptyList(),
    val suggestions: List<com.ashareai.app.standalone.domain.Security> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val quote: MarketQuote? = null,
    val selectedSymbol: String? = null,
    val candles: List<com.ashareai.app.standalone.domain.DailyCandle> = emptyList(),
    val indexQuotes: List<MarketQuote> = emptyList(),
    val indicesLoading: Boolean = false,
    val loading: Boolean = false,
    val message: String? = null,
    val watchlistSaving: Boolean = false,
    val watchlistError: String? = null,
)

data class AiStatusUiState(
    val capability: String = "检测中...",
    val target: String = "CACHE",
    val providerName: String? = null,
    val reason: String = "等待运行时路由",
    val cacheEnabled: Boolean = true,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class StandaloneViewModel(
    application: Application,
) : AndroidViewModel(application) {
    val appContext = application.applicationContext
    private val app = application as HybridApp
    private val local = app.localContainer.local
    private val market = app.localContainer.market
    private val alertEvaluator = app.localContainer.alertEvaluator
    private val technicalEngine = DeterministicResearchEngine()
    private val exitEngine = ExitResearchEngine(alertEvaluator)
    private val researchSubmitMutex = Mutex()
    private val catalogLoadMutex = Mutex()
    private var lookupJob: Job? = null

    // 省电模式管理器
    private val powerSaverManager = com.ashareai.app.ui.PowerSaverManager(appContext)
    val isPowerSaveMode: StateFlow<Boolean> get() = powerSaverManager.isPowerSaveMode
    val batteryLevel: StateFlow<Int> get() = powerSaverManager.batteryLevel
    val isCharging: StateFlow<Boolean> get() = powerSaverManager.isCharging
    val screenInteractive: StateFlow<Boolean> get() = powerSaverManager.screenInteractive
    val thermalStatus: StateFlow<Int> get() = powerSaverManager.thermalStatus

    val holdings = local.holdings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val watchlist = local.watchlist.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val quotes = local.quotes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val alerts = local.alerts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val notifications = local.notifications.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val monitoringEvents = local.monitoringEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val unreadNotifications = local.unreadNotificationCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val researchRuns = local.researchRuns.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val reports = local.reports.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val candidates = local.candidates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val portfolios = local.simulationPortfolios.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val aiProviders = app.localContainer.aiProviders.providers.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val aiAgents = app.localContainer.settings.aiAgents.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val aiStatus = combine(aiProviders, aiAgents) { providers, agents ->
        val decision = AiAgentRouter.route(
            runtime = LocalInferenceDetector.runtimeSnapshot(appContext, AiTaskType.CHAT),
            agents = agents,
            providers = providers,
            providerStates = app.localContainer.aiClient.providerRuntimeStates(),
        )
        AiStatusUiState(
            capability = LocalInferenceDetector.getCapabilityDescription(appContext),
            target = decision.target.name,
            providerName = decision.provider?.name,
            reason = decision.reason,
            cacheEnabled = decision.agent?.enableCache != false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiStatusUiState())
    val chatSessions = local.chatSessions.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )
    val settings = app.localContainer.settings.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        com.ashareai.app.standalone.data.settings.LocalSettings(),
    )
    val backtests = app.localContainer.backtest.listBacktests(20).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    private val _marketState = MutableStateFlow(MarketUiState())
    val marketState: StateFlow<MarketUiState> = _marketState
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    private val _aiTestResult = MutableStateFlow<String?>(null)
    val aiTestResult: StateFlow<String?> = _aiTestResult
    private val _exitResearch = MutableStateFlow<List<ExitResearch>>(emptyList())
    val exitResearch: StateFlow<List<ExitResearch>> = _exitResearch
    private val activeChatSessionId = MutableStateFlow<String?>(null)
    val activeChatMessages = activeChatSessionId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else local.chatMessages(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val holdingsWithQuotes = combine(holdings, quotes) { held, cached ->
        val quoteMap = cached.associateBy(MarketQuote::symbol)
        held.map { it to quoteMap[it.symbol] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        powerSaverManager.start()
    }

    fun loadCatalog(limit: Int = 100) {
        if (!catalogLoadMutex.tryLock()) return
        viewModelScope.launch {
            try {
                _marketState.update { it.copy(loading = true, message = null) }
                val catalog = market.catalog(limit)
                _marketState.update { it.copy(catalog = catalog, loading = false) }
            } finally {
                catalogLoadMutex.unlock()
            }
        }
    }

    fun updateMarketQuery(query: String) {
        val next = query.take(30)
        _marketState.update {
            it.copy(
                query = next,
                suggestions = emptyList(),
                suggestionsLoading = next.trim().length >= 2,
                message = null,
            )
        }
        lookupJob?.cancel()
        if (next.trim().length < 2) return
        lookupJob = viewModelScope.launch {
            delay(220)
            val needle = next.trim()
            val localMatches = marketState.value.catalog.asSequence()
                .filter { it.symbol.contains(needle, ignoreCase = true) || it.name.contains(needle, ignoreCase = true) }
                .take(12)
                .toList()
            val matches = if (localMatches.isNotEmpty()) localMatches else market.searchSecurities(needle, 12)
            _marketState.update { it.copy(suggestions = matches, suggestionsLoading = false) }
        }
    }

    fun refreshMarketSymbol(symbol: String = marketState.value.query) {
        val input = symbol.trim()
        val normalized = input.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: marketState.value.catalog.firstOrNull {
                it.name.equals(input, ignoreCase = true) || it.name.contains(input, ignoreCase = true)
            }?.symbol
        if (normalized == null || normalized.length != 6) {
            _marketState.update { it.copy(message = "请输入 6 位证券代码，或从搜索结果选择股票") }
            return
        }
        lookupJob?.cancel()
        viewModelScope.launch {
            _marketState.update {
                it.copy(query = normalized, selectedSymbol = normalized, suggestions = emptyList(), suggestionsLoading = false, loading = true, message = null, watchlistError = null)
            }
            runCatching {
                val quote = market.refreshQuote(normalized)
                val candles = market.dailyCandles(normalized, 365, forceRefresh = false)
                _marketState.update {
                    it.copy(
                        quote = quote,
                        candles = candles,
                        loading = false,
                        message = if (quote.lastPrice == null) "行情源暂不可用，已保留缓存结果" else null,
                    )
                }
            }.onFailure { error ->
                _marketState.update {
                    it.copy(loading = false, message = error.message ?: "行情加载失败，请稍后重试")
                }
            }
        }
    }

    fun toggleWatchlist(symbol: String, name: String) {
        val normalized = symbol.trim().takeIf { it.length == 6 && it.all(Char::isDigit) } ?: return
        val current = watchlist.value
        val exists = current.any { comparableSymbol(it.symbol) == normalized }
        _marketState.update { it.copy(watchlistSaving = true, watchlistError = null) }
        viewModelScope.launch {
            runCatching {
                if (exists) local.removeWatchlist(normalized)
                else local.saveWatchlist(WatchlistItem(normalized, name.ifBlank { normalized }, System.currentTimeMillis()))
            }.onSuccess {
                _marketState.update { it.copy(watchlistSaving = false) }
            }.onFailure { error ->
                _marketState.update { it.copy(watchlistSaving = false, watchlistError = error.message ?: "自选状态保存失败") }
            }
        }
    }

    /** The three index quotes are only fetched while the market page is visible. */
    fun loadMarketIndices(forceRefresh: Boolean = false) {
        if (marketState.value.indicesLoading) return
        viewModelScope.launch {
            _marketState.update { it.copy(indicesLoading = true) }
            val quotes = market.refreshQuotes(MARKET_INDEX_SYMBOLS)
            _marketState.update {
                it.copy(
                    indexQuotes = quotes,
                    indicesLoading = false,
                    message = if (forceRefresh && quotes.none(MarketQuote::isUsable)) "大盘指数行情暂不可用" else it.message,
                )
            }
        }
    }

    fun refreshHoldingQuotes() {
        viewModelScope.launch {
            val symbols = holdings.value.map(Holding::symbol)
            if (symbols.isNotEmpty()) market.refreshQuotes(symbols)
        }
    }

    fun refreshExitResearch() {
        viewModelScope.launch {
            _exitResearch.value = holdings.value.map { holding ->
                val quote = market.refreshQuote(holding.symbol)
                val candles = market.dailyCandles(holding.symbol, 100)
                exitEngine.analyse(holding, quote, candles)
            }
        }
    }

    fun saveHolding(
        symbol: String,
        name: String,
        quantity: Double,
        averageCost: Double,
    ) {
        if (symbol.length != 6 || quantity <= 0 || averageCost <= 0) {
            _message.value = "请填写 6 位代码、数量和平均成本"
            return
        }
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            local.saveHolding(
                Holding(
                    symbol = symbol,
                    name = name.ifBlank { symbol },
                    quantity = quantity,
                    averageCost = averageCost,
                    updatedAt = now,
                ),
            )
            val candles = market.dailyCandles(symbol, 100)
            val stopPrice = alertEvaluator.stopLossPrice(
                averageCost,
                TechnicalIndicators.atr(candles, 20),
            )
            local.saveAlert(
                AlertRule(
                    id = "auto-stop-" + symbol,
                    symbol = symbol,
                    name = name.ifBlank { symbol },
                    kind = AlertKind.STOP_LOSS,
                    lowerBound = stopPrice,
                    upperBound = null,
                    enabled = true,
                    expiresAt = null,
                    cooldownMinutes = 30,
                    lastTriggeredAt = null,
                    configJson = "automatic_atr20_x2",
                ),
            )
            MarketMonitorService.start(app)
            _message.value = "已保存持仓，并按 ATR20×2 创建止损提醒"
        }
    }

    fun removeHolding(symbol: String) {
        viewModelScope.launch {
            local.removeHolding(symbol)
            if (holdings.value.size <= 1) MarketMonitorService.stop(app)
        }
    }

    fun addWatchlist(symbol: String, name: String) {
        if (symbol.length != 6) {
            _message.value = "请输入 6 位证券代码"
            return
        }
        viewModelScope.launch {
            local.saveWatchlist(WatchlistItem(symbol, name.ifBlank { symbol }, System.currentTimeMillis()))
        }
    }

    fun removeWatchlist(symbol: String) {
        viewModelScope.launch { local.removeWatchlist(symbol) }
    }

    fun saveManualAlert(
        symbol: String,
        name: String,
        kind: AlertKind,
        lowerBound: Double?,
        upperBound: Double?,
        expiryEpochMillis: Long? = null,
    ) {
        viewModelScope.launch {
            local.saveAlert(
                AlertRule(
                    id = UUID.randomUUID().toString(),
                    symbol = symbol,
                    name = name.ifBlank { symbol },
                    kind = kind,
                    lowerBound = lowerBound,
                    upperBound = upperBound,
                    enabled = true,
                    expiresAt = expiryEpochMillis,
                    cooldownMinutes = 30,
                    lastTriggeredAt = null,
                    configJson = "manual",
                ),
            )
        }
    }

    fun removeAlert(id: String) {
        viewModelScope.launch { local.removeAlert(id) }
    }

    fun startResearch(
        scope: ResearchScope,
        customSymbols: String,
        marketLimit: Int,
        totalBudget: Double,
        perSymbolBudget: Double,
        maxStockPrice: Double?,
        aiProviderId: String?,
        includePortfolioData: Boolean,
    ) {
        if (!totalBudget.isFinite() || totalBudget <= 0.0) {
            _message.value = "总预算必须大于 0"
            return
        }
        if (!perSymbolBudget.isFinite() || perSymbolBudget <= 0.0 || perSymbolBudget > totalBudget) {
            _message.value = "单股最高投入必须大于 0 且不超过总预算"
            return
        }
        if (maxStockPrice != null && (!maxStockPrice.isFinite() || maxStockPrice <= 0.0)) {
            _message.value = "最高可接受股价必须大于 0"
            return
        }
        if (!researchSubmitMutex.tryLock()) return
        viewModelScope.launch {
            try {
                runCatching {
                    app.localContainer.research.enqueue(
                        ResearchRequest(
                            scope = scope,
                            symbols = customSymbols.split(",", " ", "\n").map(String::trim),
                            marketLimit = ResearchBatchPlanner.clampMarketLimit(marketLimit),
                            includePortfolioDataForAi = includePortfolioData && settings.value.portfolioDataAllowedForAi,
                            aiProviderId = aiProviderId,
                            totalBudget = totalBudget,
                            perSymbolBudget = perSymbolBudget,
                            maxStockPrice = maxStockPrice,
                        ),
                    )
                }.onSuccess {
                    _message.value = "研究已加入独立 :research 进程"
                }.onFailure {
                    _message.value = it.message ?: "无法启动研究"
                }
            } finally {
                researchSubmitMutex.unlock()
            }
        }
    }

    fun cancelResearch(runId: String) {
        viewModelScope.launch { app.localContainer.research.cancel(runId) }
    }

    fun researchEstimate(scope: ResearchScope, customSymbols: String, marketLimit: Int, aiEnabled: Boolean) =
        ResearchBatchPlanner.estimate(
            when (scope) {
                ResearchScope.HOLDINGS -> holdings.value.size
                ResearchScope.WATCHLIST -> watchlist.value.size
                ResearchScope.CUSTOM -> customSymbols.split(",", " ", "\n").count { it.length == 6 }
                ResearchScope.MARKET -> marketLimit
            }.coerceAtLeast(1),
            aiEnabled,
        )

    fun setMonitoringEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.localContainer.settings.setMonitoringEnabled(enabled)
            if (enabled) {
                MarketMonitorService.start(app)
                app.localContainer.monitoringFallbackScheduler.schedule()
            } else {
                MarketMonitorService.stop(app)
                app.localContainer.monitoringFallbackScheduler.cancel()
            }
        }
    }

    fun setMonitoringInterval(seconds: Int) {
        viewModelScope.launch { app.localContainer.settings.setMonitoringIntervalSeconds(seconds) }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setAlertsEnabled(enabled) }
    }

    fun setIslandEnabled(enabled: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setIslandEnabled(enabled) }
    }

    fun setDailyResearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            app.localContainer.settings.setDailyResearchEnabled(enabled)
            app.localContainer.dailyResearchScheduler.schedule()
        }
    }

    fun setDailyReportAEnabled(enabled: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setDailyReportAEnabled(enabled) }
    }

    fun setDailyReportBEnabled(enabled: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setDailyReportBEnabled(enabled) }
    }

    fun saveAutomaticReports(reports: List<AutomaticResearchReportConfig>) {
        viewModelScope.launch {
            runCatching {
                app.localContainer.settings.saveAutomaticReports(reports)
                app.localContainer.dailyResearchScheduler.schedule()
            }.onSuccess {
                _message.value = "自动报告 A/B 配置已保存"
            }.onFailure {
                _message.value = it.message ?: "自动报告配置无效"
            }
        }
    }

    fun setMarketScanLimit(limit: Int) {
        viewModelScope.launch { app.localContainer.settings.setMarketScanLimit(limit) }
    }

    fun setPortfolioDataAllowedForAi(allowed: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setPortfolioDataAllowedForAi(allowed) }
    }

    fun submitWatchlistTraining(
        startDate: String,
        endDate: String,
        initialCash: Double,
        onDone: (String?) -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching {
                if (local.watchlistNow().isEmpty()) error("请先添加自选股")
                app.localContainer.backtest.submitBacktest(
                    startDate = startDate,
                    endDate = endDate,
                    initialCash = initialCash,
                    benchmark = "000300",
                    reportId = null,
                )
            }.onSuccess { onDone(null) }
                .onFailure { onDone(it.message ?: "训练任务失败") }
        }
    }

    fun setDarkMode(mode: String) {
        viewModelScope.launch { app.localContainer.settings.setDarkMode(mode) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { app.localContainer.settings.setAccentColor(hex) }
    }

    fun exportReportJson(reportId: String?, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { app.localContainer.reportExports.json(reportId) }) }
    }

    fun exportReportCsv(reportId: String?, onResult: (Result<ByteArray>) -> Unit) {
        viewModelScope.launch { onResult(runCatching { app.localContainer.reportExports.csv(reportId) }) }
    }

    fun setGlassEnabled(enabled: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setGlassEnabled(enabled) }
    }

    fun setFullAnimationsEnabled(enabled: Boolean) {
        viewModelScope.launch { app.localContainer.settings.setFullAnimationsEnabled(enabled) }
    }

    fun completeFirstRun() {
        viewModelScope.launch { app.localContainer.settings.setFirstRunComplete() }
    }

    fun clearMarketCache() {
        viewModelScope.launch {
            local.clearMarketCache()
            _message.value = "已清除行情和 K 线缓存"
        }
    }

    fun showIslandTest() {
        app.localContainer.notifications.showIslandTest()
    }

    fun testMarketProvider() {
        viewModelScope.launch {
            val result = market.catalog(1).firstOrNull()
            _message.value = if (result == null) {
                "行情 Provider 无可用目录数据"
            } else {
                "行情 Provider 可用：" + result.name + " · " + result.symbol
            }
        }
    }

    fun saveAiProvider(draft: AiProviderDraft) {
        viewModelScope.launch {
            runCatching { app.localContainer.aiProviders.save(draft) }
                .onSuccess { _message.value = "AI Provider 已保存（密钥已由 Keystore 加密）" }
                .onFailure { _message.value = it.message ?: "保存 AI Provider 失败" }
        }
    }

    fun removeAiProvider(id: String) {
        viewModelScope.launch { app.localContainer.aiProviders.remove(id) }
    }

    fun saveAiAgent(agent: AiAgentConfig) {
        viewModelScope.launch {
            runCatching { app.localContainer.settings.saveAiAgent(agent) }
                .onSuccess { _message.value = "AI Agent 已保存" }
                .onFailure { _message.value = it.message ?: "保存 AI Agent 失败" }
        }
    }

    fun removeAiAgent(id: String) {
        viewModelScope.launch {
            app.localContainer.settings.removeAiAgent(id)
            _message.value = "AI Agent 已删除"
        }
    }

    // 用于新的 Provider 配置界面的挂起函数版本
    suspend fun saveAiProviderSuspend(draft: AiProviderDraft): Result<Unit> = runCatching {
        app.localContainer.aiProviders.save(draft)
    }

    suspend fun deleteAiProvider(id: String) {
        if (aiAgents.value.any { it.providerId == id }) {
            throw IllegalStateException("该 Provider 仍被 Agent 使用，请先改为自动路由或重新绑定")
        }
        app.localContainer.aiProviders.remove(id)
    }

    suspend fun testAiProviderSuspend(providerId: String): String {
        val output = StringBuilder()
        var error: String? = null
        app.localContainer.aiClient.stream(
            AiRequest(
                providerId = providerId,
                systemInstruction = "Reply with a short connection confirmation.",
                prompt = "connection test",
            ),
        ).collect { event ->
            when (event) {
                is AiStreamEvent.Delta -> output.append(event.text)
                is AiStreamEvent.Failed -> error = event.message
                else -> Unit
            }
        }
        return if (error != null) {
            "✗ 测试失败: $error"
        } else if (output.isNotBlank()) {
            "✓ 连接成功: ${output.toString().take(100)}"
        } else {
            "✗ 连接未返回文本"
        }
    }

    fun createChatSession() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val session = ChatSession(UUID.randomUUID().toString(), "本地 AI 问答", now)
            local.saveChatSession(session)
            activeChatSessionId.value = session.id
        }
    }

    fun selectChatSession(id: String) {
        activeChatSessionId.value = id
    }

    fun sendChat(
        text: String,
        providerId: String?,
        includePortfolio: Boolean,
    ) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val sessionId = activeChatSessionId.value ?: UUID.randomUUID().toString().also {
                local.saveChatSession(ChatSession(it, "本地 AI 问答", System.currentTimeMillis()))
                activeChatSessionId.value = it
            }
            val now = System.currentTimeMillis()
            val selectedSymbol = marketState.value.query.takeIf { it.length == 6 }
            val authorizedPortfolio = includePortfolio && settings.value.portfolioDataAllowedForAi
            local.saveChatMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "user",
                    body = text,
                    createdAt = now,
                    selectedSymbol = selectedSymbol,
                    includedPortfolio = authorizedPortfolio,
                ),
            )
            val routing = AiAgentRouter.route(
                runtime = LocalInferenceDetector.runtimeSnapshot(appContext, AiTaskType.CHAT),
                agents = aiAgents.value,
                providers = aiProviders.value,
                providerStates = app.localContainer.aiClient.providerRuntimeStates(),
                explicitProviderId = providerId,
            )
            val selectedProviderId = routing.provider?.id
            if (selectedProviderId == null || routing.target == AiExecutionTarget.LOCAL || routing.target == AiExecutionTarget.DETERMINISTIC) {
                local.saveChatMessage(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        sessionId = sessionId,
                        role = "assistant",
                        body = "${routing.reason}；本地行情、研究、提醒和报告仍可独立使用。",
                        createdAt = System.currentTimeMillis(),
                        selectedSymbol = selectedSymbol,
                        includedPortfolio = false,
                    ),
                )
                return@launch
            }
            val symbol = selectedSymbol
            val quote = symbol?.let { market.refreshQuote(it) }
            val candles = symbol?.let { market.dailyCandles(it, 100) }.orEmpty()
            val holding = symbol?.let { code -> holdings.value.firstOrNull { it.symbol == code } }
            val result = symbol?.let {
                technicalEngine.analyse(it, quote?.name ?: it, quote, candles)
            }
            val prompt = AiPayloadBuilder.chatPrompt(text, result, candles, holding, authorizedPortfolio)
            val output = StringBuilder()
            var error: String? = null
            val aiRequest = AiRequest(
                providerId = selectedProviderId,
                systemInstruction = "你是 A 股本地助手。不要虚构基础面、事件或价格，并且不能修改风险门槛。",
                prompt = prompt,
            )
            val stream = if (routing.target == AiExecutionTarget.CACHE) {
                app.localContainer.aiClient.streamCached(aiRequest)
            } else {
                app.localContainer.aiClient.streamWithFallback(aiRequest, routing.fallbackProviders.map { it.id })
            }
            stream.collect { event ->
                when (event) {
                    is AiStreamEvent.Delta -> output.append(event.text)
                    is AiStreamEvent.Failed -> error = event.message
                    else -> Unit
                }
            }
            local.saveChatMessage(
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    sessionId = sessionId,
                    role = "assistant",
                    body = output.toString().takeIf(String::isNotBlank) ?: error ?: "AI 未返回文本",
                    createdAt = System.currentTimeMillis(),
                    selectedSymbol = selectedSymbol,
                    includedPortfolio = authorizedPortfolio,
                ),
            )
        }
    }

    suspend fun exportArchive(passphrase: CharArray): ByteArray = app.localContainer.archive.export(passphrase)

    suspend fun previewArchive(bytes: ByteArray, passphrase: CharArray): ArchiveMergePreview =
        app.localContainer.archive.preview(bytes, passphrase)

    suspend fun applyArchive(
        bytes: ByteArray,
        passphrase: CharArray,
        preview: ArchiveMergePreview,
        resolutions: Map<String, ArchiveMergeResolution> = emptyMap(),
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): String {
        val summary = app.localContainer.archive.apply(bytes, passphrase, preview, resolutions, idempotencyKey)
        return "已合并 ${summary.holdings} 个持仓、${summary.watchlist} 个自选和 ${summary.reports} 份报告"
    }

    suspend fun importArchive(bytes: ByteArray, passphrase: CharArray): String {
        val summary = app.localContainer.archive.import(bytes, passphrase)
        return "已导入 " + summary.holdings + " 个持仓、" + summary.watchlist + " 个自选和 " + summary.reports + " 份报告"
    }

    fun markNotificationRead(id: String) {
        viewModelScope.launch { local.markNotificationRead(id) }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch { local.markAllNotificationsRead() }
    }

    fun dismissMessage() {
        _message.value = null
    }

    override fun onCleared() {
        powerSaverManager.stop()
        super.onCleared()
    }

    private companion object {
        val MARKET_INDEX_SYMBOLS = listOf("000300", "000905", "000852")
    }
}
