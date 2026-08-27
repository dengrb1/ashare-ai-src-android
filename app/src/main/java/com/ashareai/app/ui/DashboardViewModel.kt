package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.data.model.Run
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DashboardContent(val latestRun: Run?)

/** Loads the small amount of research metadata needed by the overview page. */
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.researchRepository
    private val _state = MutableStateFlow<ScreenState<DashboardContent>>(ScreenState.Loading)
    val state: StateFlow<ScreenState<DashboardContent>> = _state.asStateFlow()

    fun load() = viewModelScope.launch {
        _state.value = ScreenState.Loading
        runCatching { repository.runs(limit = 1, mine = true).firstOrNull() }
            .onSuccess { latest ->
                _state.value = if (latest == null) {
                    ScreenState.Empty
                } else {
                    ScreenState.Content(DashboardContent(latest))
                }
            }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage()) }
    }

    fun retry() = load()
}
