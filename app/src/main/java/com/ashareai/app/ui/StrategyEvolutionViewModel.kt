package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.StrategyReviewRequest
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import retrofit2.HttpException

data class StrategyEvolutionContent(
    val active: JsonObject? = null,
    val candidates: List<JsonObject> = emptyList(),
    val message: String? = null,
)

/** Admin-only controls for candidate review. Historical scores are never rewritten. */
class StrategyEvolutionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.researchRepository
    private val _state = MutableStateFlow<ScreenState<StrategyEvolutionContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<StrategyEvolutionContent>> = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun load() = viewModelScope.launch {
        val previous = (_state.value as? ScreenState.Content)?.value
        _state.value = ScreenState.Loading
        runCatching {
            StrategyEvolutionContent(
                active = repository.activeStrategyVersion(),
                candidates = repository.strategyCandidates(status = "CANDIDATE").versions,
            )
        }.onSuccess { _state.value = ScreenState.Content(it) }
            .onFailure { error ->
                val message = if (error is HttpException && error.code() == 403) {
                    "策略演化仅对管理员开放"
                } else error.toUserMessage()
                _state.value = ScreenState.Error(message, previous)
            }
    }

    fun approve(id: String) = review(id) { repository.approveStrategy(id) }
    fun reject(id: String) = review(id) { repository.rejectStrategy(id) }

    fun rollback() = viewModelScope.launch {
        runAction { repository.rollbackStrategy() }
    }

    private fun review(id: String, action: suspend () -> JsonObject) = viewModelScope.launch {
        runAction(action)
    }

    private suspend fun runAction(action: suspend () -> JsonObject) {
        if (_busy.value) return
        _busy.value = true
        runCatching { action() }
            .onSuccess { load() }
            .onFailure { error ->
                val previous = (_state.value as? ScreenState.Content)?.value
                _state.value = ScreenState.Error(error.toUserMessage(), previous)
            }
        _busy.value = false
    }
}
