package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.Backtest
import com.ashareai.app.data.model.BacktestRequest
import com.ashareai.app.data.model.BuyEntryMonitor
import com.ashareai.app.data.model.Snapshot
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BuyMonitorContent(val monitors: List<BuyEntryMonitor>)

data class BacktestWorkspaceContent(
    val backtests: List<Backtest> = emptyList(),
    val snapshots: List<Snapshot> = emptyList(),
)

/** Owns simulation-only actions and state for the connected client. */
class SimulationViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.simulationRepository

    private val _buyMonitorState = MutableStateFlow<ScreenState<BuyMonitorContent>>(ScreenState.Loading)
    val buyMonitorState: StateFlow<ScreenState<BuyMonitorContent>> = _buyMonitorState.asStateFlow()

    private val _backtestState = MutableStateFlow<ScreenState<BacktestWorkspaceContent>>(ScreenState.Loading)
    val backtestState: StateFlow<ScreenState<BacktestWorkspaceContent>> = _backtestState.asStateFlow()

    fun loadBuyMonitors() = viewModelScope.launch {
        _buyMonitorState.value = ScreenState.Loading
        runCatching { repository.buyEntryMonitors() }
            .onSuccess { monitors ->
                _buyMonitorState.value = if (monitors.isEmpty()) {
                    ScreenState.Empty
                } else {
                    ScreenState.Content(BuyMonitorContent(monitors))
                }
            }
            .onFailure { error -> _buyMonitorState.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun manualExitAdvice(symbol: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.manualExitAdvice(symbol) }
            .onSuccess { onDone(null) }
            .onFailure { onDone(it.toUserMessage()) }
    }

    fun loadBacktests() = viewModelScope.launch {
        _backtestState.value = ScreenState.Loading
        runCatching {
            val backtests = repository.backtests(limit = 20)
            val snapshots = runCatching { repository.snapshots() }.getOrDefault(emptyList())
            BacktestWorkspaceContent(backtests, snapshots)
        }.onSuccess { content ->
            _backtestState.value = ScreenState.Content(content)
        }.onFailure { error -> _backtestState.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun refreshBacktests() = viewModelScope.launch {
        val previous = (_backtestState.value as? ScreenState.Content)?.value
        runCatching { repository.backtests(limit = 20) }
            .onSuccess { backtests ->
                _backtestState.value = ScreenState.Content(
                    BacktestWorkspaceContent(backtests, previous?.snapshots.orEmpty()),
                )
            }
            .onFailure { error ->
                _backtestState.value = ScreenState.Error(error.toUserMessage(), previous)
            }
    }

    fun retryBacktest(id: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.retryBacktest(id) }
            .onSuccess { refreshBacktests(); onDone(null) }
            .onFailure {
                val previous = (_backtestState.value as? ScreenState.Content)?.value
                val message = it.toUserMessage()
                _backtestState.value = ScreenState.Error(message, previous)
                onDone(message)
            }
    }

    fun submitBacktest(request: BacktestRequest, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.submitBacktest(request) }
            .onSuccess { refreshBacktests(); onDone(null) }
            .onFailure {
                val previous = (_backtestState.value as? ScreenState.Content)?.value
                val message = it.toUserMessage()
                _backtestState.value = ScreenState.Error(message, previous)
                onDone(message)
            }
    }
}
