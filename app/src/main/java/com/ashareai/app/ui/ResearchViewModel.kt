package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.ResearchRequest
import com.ashareai.app.data.model.ResearchSettings
import com.ashareai.app.data.model.ResearchSettingsRequest
import com.ashareai.app.data.model.Run
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ResearchWorkspaceContent(
    val runs: List<Run> = emptyList(),
    val settings: ResearchSettings? = null,
)

/** Owns connected research runs, settings and simulation-only controls. */
class ResearchViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.researchRepository
    private val _state = MutableStateFlow<ScreenState<ResearchWorkspaceContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<ResearchWorkspaceContent>> = _state.asStateFlow()

    fun load(loadSettings: Boolean = false) = viewModelScope.launch {
        val previous = content()
        if (previous == null) _state.value = ScreenState.Loading
        runCatching {
            val runs = repository.runs(limit = 20, mine = true)
            val settings = if (loadSettings) {
                runCatching { repository.settings() }.getOrNull()
            } else {
                previous?.settings
            }
            ResearchWorkspaceContent(runs, settings)
        }.onSuccess { value -> _state.value = ScreenState.Content(value) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    fun refreshActiveRuns() = viewModelScope.launch {
        val current = content() ?: return@launch
        val activeIds = current.runs.filter { isActiveStatus(it.status) }.map { it.run_id }
        if (activeIds.isEmpty()) return@launch
        val updates = coroutineScope {
            activeIds.map { runId -> async { runCatching { repository.run(runId) }.getOrNull() } }.awaitAll()
        }.filterNotNull()
        if (updates.isNotEmpty()) {
            val byId = updates.associateBy { it.run_id }
            _state.value = ScreenState.Content(current.copy(runs = current.runs.map { byId[it.run_id] ?: it }))
        }
    }

    fun submit(request: ResearchRequest, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.submit(request) }
            .onSuccess { load(loadSettings = false); onDone(null) }
            .onFailure {
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, content())
                onDone(message)
            }
    }

    fun cancel(runId: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.cancel(runId) }
            .onSuccess { load(loadSettings = false); onDone(null) }
            .onFailure {
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, content())
                onDone(message)
            }
    }

    suspend fun saveSettings(request: ResearchSettingsRequest): ResearchSettings {
        val saved = repository.saveSettings(request)
        val previous = content()
        _state.value = ScreenState.Content((previous ?: ResearchWorkspaceContent()).copy(settings = saved))
        return saved
    }

    fun retry() = load(loadSettings = false)

    private fun content(): ResearchWorkspaceContent? = (_state.value as? ScreenState.Content)?.value
}
