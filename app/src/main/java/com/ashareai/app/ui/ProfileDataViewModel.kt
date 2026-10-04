package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.model.PersonalArchiveJob
import com.ashareai.app.data.toUserMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.ResponseBody

data class ProfileDataContent(
    val job: PersonalArchiveJob? = null,
    val importJob: PersonalArchiveJob? = null,
)

/** Owns archive export requests and their foreground status refreshes. */
class ProfileDataViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as AShareApp).container.profileRepository
    private val _state = MutableStateFlow<ScreenState<ProfileDataContent>>(ScreenState.Empty)
    val state: StateFlow<ScreenState<ProfileDataContent>> = _state.asStateFlow()

    fun createExport(passphrase: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.createExport(passphrase) }
            .onSuccess { job -> _state.value = ScreenState.Content(content().copy(job = job)); onDone(null) }
            .onFailure {
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, content())
                onDone(message)
            }
    }

    fun refreshExport(id: String) = viewModelScope.launch {
        val previous = content()
        runCatching { repository.exportStatus(id) }
            .onSuccess { job -> _state.value = ScreenState.Content(previous.copy(job = job)) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    /** Submit one encrypted server archive for preview. The job id is reused for retries. */
    fun createImport(payload: ByteArray, passphrase: String, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        val existing = content().importJob
        if (existing?.archiveId != null && existing.status.uppercase() !in setOf("FAILED", "ERROR", "EXPIRED")) {
            onDone(null)
            return@launch
        }
        runCatching { repository.createImport(payload, passphrase) }
            .onSuccess { job ->
                _state.value = ScreenState.Content(content().copy(importJob = job))
                onDone(null)
            }
            .onFailure {
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, content())
                onDone(message)
            }
    }

    fun archiveId(job: PersonalArchiveJob?): String? = job?.archiveId

    fun refreshImport(id: String) = viewModelScope.launch {
        val previous = content()
        runCatching { repository.importStatus(id) }
            .onSuccess { job -> _state.value = ScreenState.Content(previous.copy(importJob = job)) }
            .onFailure { error -> _state.value = ScreenState.Error(error.toUserMessage(), previous) }
    }

    fun applyImport(id: String, mergeOptions: JsonObject, onDone: (String?) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.applyImport(id, mergeOptions) }
            .onSuccess { job ->
                _state.value = ScreenState.Content(content().copy(importJob = job))
                onDone(null)
            }
            .onFailure {
                val message = it.toUserMessage()
                _state.value = ScreenState.Error(message, content())
                onDone(message)
            }
    }

    suspend fun downloadExport(id: String): ResponseBody = repository.downloadExport(id)

    private fun content(): ProfileDataContent = when (val current = _state.value) {
        is ScreenState.Content -> current.value
        is ScreenState.Error -> current.previous ?: ProfileDataContent()
        else -> ProfileDataContent()
    }
}
