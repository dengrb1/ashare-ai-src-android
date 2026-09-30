package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.KlineRepository
import com.ashareai.app.data.KlineSource
import com.ashareai.app.data.NotificationCenter
import com.ashareai.app.data.NotificationStreamClient
import com.ashareai.app.data.NotificationStreamEvent
import com.ashareai.app.data.model.AssetState
import com.ashareai.app.data.model.AssetStateRequest
import com.ashareai.app.data.model.ExitMonitorRequest
import com.ashareai.app.data.model.MarketIndicesResponse
import com.ashareai.app.data.model.MarketSession
import com.ashareai.app.data.model.Quote
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MarketWorkspaceContent(
    val assets: AssetState? = null,
    val quotes: Map<String, Quote> = emptyMap(),
    val marketIndices: MarketIndicesResponse = MarketIndicesResponse(),
    val marketSession: MarketSession? = null,
    val unreadCount: Int = 0,
)

/**
 * Owns connected-market state and the only foreground quote polling loop. This
 * keeps page composables observational and keeps AppViewModel session-scoped.
 */
class MarketViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AShareApp
    private val market = app.container.marketRepository
    private val notifications = app.container.notificationRepository
    private val settings = app.settings

    private val _state = MutableStateFlow<ScreenState<MarketWorkspaceContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<MarketWorkspaceContent>> = _state.asStateFlow()
    val assets: StateFlow<AssetState?> = state.map { it.contentOrNull()?.assets }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val quotes: StateFlow<Map<String, Quote>> = state.map { it.contentOrNull()?.quotes.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val marketIndices: StateFlow<MarketIndicesResponse> = state.map { it.contentOrNull()?.marketIndices ?: MarketIndicesResponse() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MarketIndicesResponse())
    val marketSession: StateFlow<MarketSession?> = state.map { it.contentOrNull()?.marketSession }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val unreadCount: StateFlow<Int> = state.map { it.contentOrNull()?.unreadCount ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _refreshIntervalSeconds = MutableStateFlow(MarketRefreshIntervals.DEFAULT_SECONDS)
    val refreshIntervalSeconds: StateFlow<Int> = _refreshIntervalSeconds.asStateFlow()

    val notificationCenter = NotificationCenter(notifications, viewModelScope) { unread ->
        updateContent { it.copy(unreadCount = unread) }
    }

    private var pollJob: Job? = null
    private var notificationStreamJob: Job? = null
    private var signedIn = false
    private var foreground = false

    init {
        viewModelScope.launch {
            settings.foregroundMarketRefreshIntervalSeconds.collect { seconds ->
                _refreshIntervalSeconds.value = MarketRefreshIntervals.normalize(seconds)
                restartPolling()
            }
        }
    }

    fun bindSession(isSignedIn: Boolean, isForeground: Boolean) {
        signedIn = isSignedIn
        foreground = isForeground
        if (!foreground) notificationStreamJob?.cancel()
        if (!signedIn) {
            pollJob?.cancel()
            notificationStreamJob?.cancel()
            _state.value = ScreenState.Loading
            return
        }
        if (content() == null) loadWorkspace() else restartPolling()
        if (foreground) startNotificationStream()
    }

    fun loadWorkspace() = viewModelScope.launch {
        val existing = content()
        if (existing == null) _state.value = ScreenState.Loading
        runCatching { market.assets() }
            .onSuccess { assets ->
                updateContent { it.copy(assets = assets) }
                refreshAll()
                restartPolling()
            }
            .onFailure { error ->
                _state.value = ScreenState.Error(error.toUserMessage(), existing)
            }
    }

    fun retry() = loadWorkspace()

    private fun startNotificationStream() {
        if (notificationStreamJob?.isActive == true) return
        notificationStreamJob = viewModelScope.launch {
            NotificationStreamClient.stream(settings).collect { event ->
                when (event) {
                    is NotificationStreamEvent.NotificationEvent,
                    is NotificationStreamEvent.Status -> notificationCenter.refresh()
                    else -> Unit
                }
            }
        }
    }

    fun refreshAll() = viewModelScope.launch { refreshAllInternal() }

    fun refreshMarketIndices(refresh: Boolean = false) = viewModelScope.launch {
        runCatching { market.marketIndices(refresh) }
            .onSuccess { indices -> updateContent { it.copy(marketIndices = indices) } }
            .onFailure { error -> retainError(error) }
    }

    fun saveAssets(request: AssetStateRequest, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { market.saveAssets(request) }
            .onSuccess { assets ->
                updateContent { it.copy(assets = assets) }
                restartPolling()
                onDone(null)
            }
            .onFailure { error ->
                retainError(error)
                onDone(error.toUserMessage())
            }
    }

    fun saveExitMonitor(request: ExitMonitorRequest, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { market.saveExitMonitor(request) }
            .onSuccess { assets ->
                updateContent { it.copy(assets = assets) }
                onDone(null)
            }
            .onFailure { error ->
                retainError(error)
                onDone(error.toUserMessage())
            }
    }

    fun saveRefreshInterval(seconds: Int, onDone: (String?) -> Unit = {}) {
        if (seconds !in MarketRefreshIntervals.OPTIONS) {
            onDone("不支持的自动刷新间隔")
            return
        }
        viewModelScope.launch {
            runCatching { settings.setForegroundMarketRefreshIntervalSeconds(seconds) }
                .onSuccess {
                    _refreshIntervalSeconds.value = seconds
                    restartPolling()
                    onDone(null)
                }
                .onFailure { onDone(it.toUserMessage()) }
        }
    }

    fun refreshQuote(symbol: String, onDone: (Quote?, String?) -> Unit = { _, _ -> }) = viewModelScope.launch {
        runCatching { market.quote(symbol, refresh = true) }
            .onSuccess { quote ->
                updateContent { it.copy(quotes = it.quotes + (quote.symbol to quote)) }
                onDone(quote, null)
            }
            .onFailure { onDone(null, it.toUserMessage()) }
    }

    fun klineRepository(): KlineRepository = KlineRepository(
        KlineSource { symbol, period, limit, start, end -> market.klines(symbol, period, limit, start, end) },
    )

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    private fun restartPolling() {
        pollJob?.cancel()
        if (!signedIn || !foreground) return
        pollJob = viewModelScope.launch {
            var tick = 0
            while (signedIn && foreground) {
                refreshQuotesInternal()
                if (tick % 4 == 0) {
                    coroutineScope {
                        val status = async { refreshMarketStatusInternal() }
                        val summary = async { refreshNotificationSummaryInternal() }
                        status.await()
                        summary.await()
                    }
                }
                tick++
                delay(_refreshIntervalSeconds.value * 1_000L)
            }
        }
    }

    private suspend fun refreshAllInternal() = coroutineScope {
        val quotes = async { refreshQuotesInternal() }
        val status = async { refreshMarketStatusInternal() }
        val summary = async { refreshNotificationSummaryInternal() }
        quotes.await()
        status.await()
        summary.await()
    }

    private suspend fun refreshQuotesInternal() {
        val assets = content()?.assets ?: return
        val symbols = (assets.watchlist + assets.positions.map { it.symbol }).distinct()
        if (symbols.isEmpty()) return
        runCatching { market.quotes(symbols) }
            .onSuccess { quotes -> updateContent { it.copy(quotes = it.quotes + quotes.associateBy(Quote::symbol)) } }
    }

    private suspend fun refreshMarketStatusInternal() {
        runCatching { market.marketStatus().market_session }
            .onSuccess { session -> updateContent { it.copy(marketSession = session) } }
    }

    private suspend fun refreshNotificationSummaryInternal() {
        runCatching { notifications.summary().unread_count }
            .onSuccess { unread -> updateContent { it.copy(unreadCount = unread) } }
    }

    private fun content(): MarketWorkspaceContent? = _state.value.contentOrNull()

    private fun updateContent(transform: (MarketWorkspaceContent) -> MarketWorkspaceContent) {
        _state.value = ScreenState.Content(transform(content() ?: MarketWorkspaceContent()))
    }

    private fun retainError(error: Throwable) {
        _state.value = ScreenState.Error(error.toUserMessage(), content())
    }
}
