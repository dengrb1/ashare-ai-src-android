package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.KlinePeriod
import com.ashareai.app.data.KlineRange
import com.ashareai.app.data.KlineRepository
import com.ashareai.app.data.KlineSource
import com.ashareai.app.data.model.Candidate
import com.ashareai.app.data.model.FinancialSearchResult
import com.ashareai.app.data.model.KlineBar
import com.ashareai.app.data.model.Portfolio
import com.ashareai.app.data.model.SecurityResolveResponse
import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.data.model.TradeAdviceMonitorRequest
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CandidatesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.researchRepository
    private val _state = MutableStateFlow<ScreenState<List<Candidate>>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<List<Candidate>>> = _state.asStateFlow()

    fun load(date: String) = viewModelScope.launch {
        _state.value = ScreenState.Loading
        runCatching { repository.candidates(date) }
            .onSuccess { value -> _state.value = if (value.isEmpty()) ScreenState.Empty else ScreenState.Content(value) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun retry(date: String) = load(date)
}

class PortfolioViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.simulationRepository
    private val _state = MutableStateFlow<ScreenState<Portfolio>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<Portfolio>> = _state.asStateFlow()

    fun load(date: String) = viewModelScope.launch {
        _state.value = ScreenState.Loading
        runCatching { repository.portfolio(date) }
            .onSuccess { value ->
                _state.value = if (value.positions.isEmpty() && value.message == null) ScreenState.Empty else ScreenState.Content(value)
            }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun retry(date: String) = load(date)
}

class FinancialSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.marketRepository
    private val _state = MutableStateFlow<ScreenState<FinancialSearchResult>>(ScreenState.Empty)
    val state: StateFlow<ScreenState<FinancialSearchResult>> = _state.asStateFlow()

    fun search(query: String) = viewModelScope.launch {
        _state.value = ScreenState.Loading
        runCatching { repository.financialSearch(query.trim()) }
            .onSuccess { value -> _state.value = ScreenState.Content(value) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun retry(query: String) = search(query)
}

class SecurityResolveViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.marketRepository
    private val _state = MutableStateFlow<ScreenState<List<Pair<String, String>>>>(ScreenState.Empty)
    val state: StateFlow<ScreenState<List<Pair<String, String>>>> = _state.asStateFlow()

    fun resolve(query: String, onResult: (List<Pair<String, String>>) -> Unit = {}) = viewModelScope.launch {
        _state.value = ScreenState.Loading
        runCatching { repository.resolveSecurity(query.trim()) }
            .map(::toCandidates)
            .onSuccess { value ->
                _state.value = if (value.isEmpty()) ScreenState.Empty else ScreenState.Content(value)
                onResult(value)
            }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage()) }
    }

    private fun toCandidates(response: SecurityResolveResponse): List<Pair<String, String>> = buildList {
        response.match?.let { match -> if (match.symbol != null) add(match.symbol to (match.name ?: "")) }
        response.candidates.forEach { candidate -> if (candidate.symbol != null) add(candidate.symbol to (candidate.name ?: "")) }
    }.distinctBy { it.first }
}

data class KlineContent(
    val bars: List<KlineBar> = emptyList(),
    val requestCount: Int = 0,
)

/** Loads chart data outside the composable and exposes progress as state. */
class KlineViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = KlineRepository(
        KlineSource { symbol, period, limit, start, end ->
            (application as AShareApp).container.marketRepository.klines(symbol, period, limit, start, end)
        },
    )
    private val _state = MutableStateFlow<ScreenState<KlineContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<KlineContent>> = _state.asStateFlow()
    private val _requestCount = MutableStateFlow(0)
    val requestCount: StateFlow<Int> = _requestCount.asStateFlow()

    private var loadJob: kotlinx.coroutines.Job? = null

    fun load(symbol: String, period: KlinePeriod, range: KlineRange) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val previous = (_state.value as? ScreenState.Content)?.value
            _state.value = ScreenState.Loading
            _requestCount.value = 0
            runCatching {
                repository.load(symbol, period, range) { count ->
                    _requestCount.value = count
                }
            }.onSuccess { result ->
                _state.value = ScreenState.Content(KlineContent(result.bars, result.requestCount))
            }.onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                _state.value = ScreenState.Error(error.toUserMessage(), previous)
            }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        super.onCleared()
    }
}

data class ExitAdviceContent(
    val symbols: List<String> = emptyList(),
    val monitors: List<TradeAdviceMonitor> = emptyList(),
)

class ExitAdviceViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AShareApp
    private val market = app.container.marketRepository
    private val simulation = app.container.simulationRepository
    private val _state = MutableStateFlow<ScreenState<ExitAdviceContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<ExitAdviceContent>> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.value = ScreenState.Loading
        runCatching {
            coroutineScope {
                val symbols = async { market.assets().watchlist }
                val monitors = async { simulation.tradeAdviceMonitors() }
                ExitAdviceContent(symbols.await(), monitors.await())
            }
        }.onSuccess { value ->
            _state.value = if (value.symbols.isEmpty()) ScreenState.Empty else ScreenState.Content(value)
        }.onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun save(request: TradeAdviceMonitorRequest, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { simulation.saveTradeAdviceMonitor(request) }
            .onSuccess { saved ->
                val previous = (_state.value as? ScreenState.Content)?.value ?: ExitAdviceContent()
                _state.value = ScreenState.Content(
                    previous.copy(monitors = (previous.monitors.filterNot { it.symbol == saved.symbol } + saved).sortedBy { it.symbol }),
                )
                onDone(null)
            }
            .onFailure {
                val previous = (_state.value as? ScreenState.Content)?.value
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, previous)
                onDone(message)
            }
    }

    fun retry() = load()
}

data class RunsContent(
    val items: List<com.ashareai.app.data.model.RunActivity> = emptyList(),
    val cursor: String? = null,
    val auditEvents: List<com.ashareai.app.data.model.AuditEvent> = emptyList(),
)

class RunsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.researchRepository
    private val _state = MutableStateFlow<ScreenState<RunsContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<RunsContent>> = _state.asStateFlow()

    fun load(type: String?, reset: Boolean) = viewModelScope.launch {
        val previous = (_state.value as? ScreenState.Content)?.value
        if (reset) _state.value = ScreenState.Loading
        runCatching {
            val page = repository.activity(cursor = if (reset) null else previous?.cursor, type = type, limit = 20)
            if (reset) RunsContent(page.items, page.next_cursor) else RunsContent(
                items = previous?.items.orEmpty() + page.items,
                cursor = page.next_cursor,
                auditEvents = previous?.auditEvents.orEmpty(),
            )
        }.onSuccess { value ->
            _state.value = if (value.items.isEmpty()) ScreenState.Empty else ScreenState.Content(value)
        }.onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    fun loadAudit(runId: String) = viewModelScope.launch {
        val previous = (_state.value as? ScreenState.Content)?.value ?: return@launch
        runCatching { repository.audit(runId) }
            .onSuccess { events -> _state.value = ScreenState.Content(previous.copy(auditEvents = events)) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    fun retry(type: String?) = load(type, reset = true)
}
