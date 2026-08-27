package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.PersonalArchiveJob
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody

data class ProfileDataContent(val job: PersonalArchiveJob)

/** Owns archive export requests and their foreground status refreshes. */
class ProfileDataViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.profileRepository
    private val _state = MutableStateFlow<ScreenState<ProfileDataContent>>(ScreenState.Empty)
    val state: StateFlow<ScreenState<ProfileDataContent>> = _state.asStateFlow()

    fun createExport(passphrase: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.createExport(passphrase) }
            .onSuccess { job -> _state.value = ScreenState.Content(ProfileDataContent(job)); onDone(null) }
            .onFailure {
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, content())
                onDone(message)
            }
    }

    fun refreshExport(id: String) = viewModelScope.launch {
        val previous = content()
        runCatching { repository.exportStatus(id) }
            .onSuccess { job -> _state.value = ScreenState.Content(ProfileDataContent(job)) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    suspend fun downloadExport(id: String): ResponseBody = repository.downloadExport(id)

    private fun content(): ProfileDataContent? = (_state.value as? ScreenState.Content)?.value
}
