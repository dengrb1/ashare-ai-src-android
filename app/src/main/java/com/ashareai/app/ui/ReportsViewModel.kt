package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.Report
import com.ashareai.app.data.model.ReportSymbol
import com.ashareai.app.data.model.TradePlan
import com.ashareai.app.data.model.TradePlanRequest
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ReportsContent(
    val report: Report,
    val symbols: List<ReportSymbol> = emptyList(),
    val tradePlans: List<TradePlan> = emptyList(),
)

/** Owns report reads and simulated trade-plan actions for the report page. */
class ReportsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AShareApp
    private val research = app.container.researchRepository
    private val simulation = app.container.simulationRepository
    private val _state = MutableStateFlow<ScreenState<ReportsContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<ReportsContent>> = _state.asStateFlow()

    fun load(date: String, runId: String? = null) = viewModelScope.launch {
        val previous = (_state.value as? ScreenState.Content)?.value
        _state.value = ScreenState.Loading
        runCatching {
            val report = research.report(date, runId)
            if (report.report_id == null) return@runCatching null
            val reportId = report.report_id
            coroutineScope {
                val symbols = async { runCatching { research.reportSymbols(reportId) }.getOrDefault(emptyList()) }
                val plans = async { runCatching { simulation.tradePlans(reportId) }.getOrDefault(emptyList()) }
                ReportsContent(report, symbols.await(), plans.await())
            }
        }.onSuccess { content ->
            _state.value = if (content == null) ScreenState.Empty else ScreenState.Content(content)
        }.onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    fun refreshTradePlans(reportId: String) = viewModelScope.launch {
        val previous = (_state.value as? ScreenState.Content)?.value ?: return@launch
        runCatching { simulation.tradePlans(reportId) }
            .onSuccess { plans -> _state.value = ScreenState.Content(previous.copy(tradePlans = plans)) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    fun submitTradePlan(reportId: String, symbol: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { simulation.submitTradePlan(reportId, TradePlanRequest(symbols = listOf(symbol))) }
            .onSuccess { refreshTradePlans(reportId); onDone(null) }
            .onFailure {
                val message = it.toUserMessage()
                val previous = (_state.value as? ScreenState.Content)?.value
                _state.value = ScreenState.Error(message, previous)
                onDone(message)
            }
    }

    fun retry(date: String, runId: String? = null) = load(date, runId)
}
