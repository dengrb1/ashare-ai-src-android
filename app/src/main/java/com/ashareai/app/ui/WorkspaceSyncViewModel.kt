package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.HybridApp
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.sync.SyncDirection
import com.ashareai.app.sync.SyncResolution
import com.ashareai.app.sync.WorkspaceSyncCoordinator
import com.ashareai.app.sync.WorkspaceSyncOutcome
import com.ashareai.app.sync.WorkspaceSyncPreview
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class WorkspaceSyncUiState(
    val direction: SyncDirection = SyncDirection.BIDIRECTIONAL,
    val scopes: Set<String> = ArchiveScope.all,
    val preview: WorkspaceSyncPreview? = null,
    val resolutions: Map<String, SyncResolution> = emptyMap(),
    val loading: Boolean = false,
    val error: String? = null,
    val outcome: WorkspaceSyncOutcome? = null,
    val idempotencyKey: String? = null,
)

/** Owns the explicit preview/confirmation flow; snapshots never enter preferences or logs. */
class WorkspaceSyncViewModel(application: Application) : AndroidViewModel(application) {
    private val coordinator = WorkspaceSyncCoordinator(HybridApp.from(application))
    private val _state = MutableStateFlow(WorkspaceSyncUiState())
    val state: StateFlow<WorkspaceSyncUiState> = _state.asStateFlow()

    fun setDirection(direction: SyncDirection) {
        _state.value = _state.value.copy(direction = direction, preview = null, outcome = null, error = null)
    }

    fun toggleScope(scope: String) {
        val current = _state.value.scopes.toMutableSet()
        if (!current.remove(scope)) current.add(scope)
        if (current.isEmpty()) current.addAll(ArchiveScope.all)
        _state.value = _state.value.copy(scopes = current, preview = null, outcome = null, error = null)
    }

    fun preview() = viewModelScope.launch {
        val current = _state.value
        _state.value = current.copy(loading = true, error = null, outcome = null, preview = null)
        runCatching { coordinator.preview(current.direction, current.scopes) }
            .onSuccess { result ->
                val default = when (result.direction) {
                    SyncDirection.LOCAL_TO_CONNECTED -> SyncResolution.KEEP_LOCAL
                    SyncDirection.CONNECTED_TO_LOCAL -> SyncResolution.KEEP_CONNECTED
                    SyncDirection.BIDIRECTIONAL -> SyncResolution.KEEP_LOCAL
                }
                val choices = result.conflicts.associate { "${it.collection}:${it.key}" to default }
                _state.value = _state.value.copy(
                    loading = false,
                    preview = result,
                    resolutions = choices,
                    idempotencyKey = UUID.randomUUID().toString(),
                )
            }
            .onFailure { error ->
                _state.value = _state.value.copy(loading = false, error = error.toUserMessage())
            }
    }

    fun resolve(collection: String, key: String, resolution: SyncResolution) {
        val choices = _state.value.resolutions.toMutableMap()
        choices["$collection:$key"] = resolution
        _state.value = _state.value.copy(resolutions = choices, error = null, outcome = null)
    }

    fun apply() = viewModelScope.launch {
        val current = _state.value
        val preview = current.preview ?: return@launch
        val key = current.idempotencyKey ?: UUID.randomUUID().toString()
        _state.value = current.copy(loading = true, error = null, outcome = null, idempotencyKey = key)
        runCatching { coordinator.apply(preview, current.resolutions, key) }
            .onSuccess { outcome -> _state.value = _state.value.copy(loading = false, outcome = outcome) }
            .onFailure { error ->
                // Keep the key so a retry replays the same idempotent operation.
                _state.value = _state.value.copy(loading = false, error = error.toUserMessage())
            }
    }

    fun retry() {
        if (_state.value.preview == null) preview() else apply()
    }
}
