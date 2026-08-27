package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.ConnectionClassification
import com.ashareai.app.data.ConnectionProbe
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data object Probing : ConnectionUiState
    data class Result(val probe: ConnectionProbe) : ConnectionUiState
    data class Error(val message: String) : ConnectionUiState
}

/** ViewModel for address editing, manual diagnostics and connection-page retry. */
class ConnectionViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.connectionRepository
    private val _state = MutableStateFlow<ConnectionUiState>(ConnectionUiState.Idle)
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    fun probeConfigured() = viewModelScope.launch {
        _state.value = ConnectionUiState.Probing
        _state.value = runCatching { repository.probeConfiguredServer() }
            .fold(
                onSuccess = { ConnectionUiState.Result(it) },
                onFailure = { ConnectionUiState.Error(it.toUserMessage()) },
            )
    }

    fun probe(address: String, onResult: (ConnectionProbe) -> Unit = {}, onError: (String) -> Unit = {}) =
        viewModelScope.launch {
            _state.value = ConnectionUiState.Probing
            val next = runCatching { repository.probe(address) }
                .fold(
                    onSuccess = { ConnectionUiState.Result(it) },
                    onFailure = { ConnectionUiState.Error(it.toUserMessage()) },
                )
            _state.value = next
            when (next) {
                is ConnectionUiState.Result -> onResult(next.probe)
                is ConnectionUiState.Error -> onError(next.message)
                ConnectionUiState.Idle, ConnectionUiState.Probing -> Unit
            }
        }

    fun saveAndProbe(
        address: String,
        onResult: (ConnectionProbe) -> Unit = {},
        onError: (String) -> Unit = {},
    ) = viewModelScope.launch {
        _state.value = ConnectionUiState.Probing
        val next = runCatching { repository.configure(address) }
            .fold(
                onSuccess = { ConnectionUiState.Result(it) },
                onFailure = { ConnectionUiState.Error(it.toUserMessage()) },
            )
        _state.value = next
        when (next) {
            is ConnectionUiState.Result -> onResult(next.probe)
            is ConnectionUiState.Error -> onError(next.message)
            ConnectionUiState.Idle, ConnectionUiState.Probing -> Unit
        }
    }

    fun retry() = probeConfigured()

    companion object {
        fun label(classification: ConnectionClassification): String = when (classification) {
            ConnectionClassification.Fusion -> "Fusion"
            ConnectionClassification.LegacyCompatible -> "兼容服务"
            ConnectionClassification.Unsupported -> "不支持"
            ConnectionClassification.PendingConfiguration -> "待配置"
        }
    }
}
