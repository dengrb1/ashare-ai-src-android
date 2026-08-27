package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.ashareai.app.AShareApp
import com.ashareai.app.data.ApiServiceProvider
import com.ashareai.app.data.ChatStreamClient
import com.ashareai.app.data.ChatStreamEvent
import com.ashareai.app.data.model.AIChatThreadCreate
import com.ashareai.app.data.model.AIChatThreadPatch
import com.ashareai.app.data.model.BulkDeleteThreads
import com.ashareai.app.data.model.AIChatSendRequest
import kotlinx.coroutines.flow.Flow
import okhttp3.MultipartBody
import okhttp3.RequestBody

/** Boundary for AI thread operations; model secrets remain server-managed. */
class AIChatViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AShareApp
    private val repository = app.container.aiRepository
    val streamServices: ApiServiceProvider = app.container.services

    suspend fun threads(limit: Int = 50, cursor: String? = null, archived: Boolean? = null, query: String? = null) =
        repository.threads(limit, cursor, archived, query)

    suspend fun messages(id: String, limit: Int = 100) = repository.messages(id, limit)
    suspend fun costs(days: Int = 30, limit: Int = 20, threadId: String? = null) = repository.costs(days, limit, threadId)
    suspend fun models() = repository.models()
    suspend fun createThread(title: String) = repository.createThread(title)
    suspend fun patchThread(id: String, patch: AIChatThreadPatch) = repository.patchThread(id, patch)
    suspend fun deleteThread(id: String) = repository.deleteThread(id)
    suspend fun bulkDeleteThreads(ids: List<String>) = repository.bulkDeleteThreads(ids)
    suspend fun uploadAttachments(files: List<MultipartBody.Part>, threadId: RequestBody) = repository.uploadAttachments(files, threadId)

    fun stream(threadId: String, request: AIChatSendRequest): Flow<ChatStreamEvent> =
        ChatStreamClient.stream(app.settings, threadId, request, streamServices)
}
