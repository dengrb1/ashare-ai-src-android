package com.ashareai.app.data

import com.ashareai.app.data.model.Notification
import com.ashareai.app.data.model.NotificationReadRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationCenterState(
    val items: List<Notification> = emptyList(),
    val nextCursor: String? = null,
    val unreadOnly: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val error: String? = null,
)

class NotificationCenter(
    private val api: ApiService,
    private val scope: CoroutineScope,
    private val onUnreadChanged: (Int) -> Unit,
) {
    private val _state = MutableStateFlow(NotificationCenterState())
    val state: StateFlow<NotificationCenterState> = _state.asStateFlow()

    fun setUnreadOnly(value: Boolean) {
        if (_state.value.unreadOnly == value) return
        _state.value = _state.value.copy(unreadOnly = value)
        refresh()
    }

    fun refresh() = scope.launch { load(reset = true) }

    fun loadMore() = scope.launch {
        if (_state.value.nextCursor != null) load(reset = false)
    }

    fun markRead(notificationId: String) = scope.launch {
        val target = _state.value.items.firstOrNull { it.notification_id == notificationId }
        if (target?.read_at != null) return@launch
        runCatching {
            api.markRead(newIdempotencyKey(), NotificationReadRequest(listOf(notificationId)))
            _state.value = _state.value.copy(
                items = _state.value.items.map {
                    if (it.notification_id == notificationId) it.copy(read_at = "read") else it
                },
            )
            refreshSummary()
        }.onFailure { error ->
            _state.value = _state.value.copy(error = error.toUserMessage())
        }
    }

    fun markAllRead() = scope.launch {
        runCatching { api.markAllRead(newIdempotencyKey()) }
            .onSuccess {
                _state.value = _state.value.copy(
                    items = if (_state.value.unreadOnly) emptyList() else {
                        _state.value.items.map { it.copy(read_at = it.read_at ?: "read") }
                    },
                )
                refreshSummary()
            }
            .onFailure { error ->
                _state.value = _state.value.copy(error = error.toUserMessage())
            }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private suspend fun load(reset: Boolean) {
        val current = _state.value
        if (current.loading || current.loadingMore) return
        _state.value = current.copy(
            loading = reset,
            loadingMore = !reset,
            error = null,
        )
        runCatching {
            api.notifications(
                limit = 30,
                cursor = if (reset) null else current.nextCursor,
                unreadOnly = current.unreadOnly.takeIf { it },
            )
        }.onSuccess { page ->
            val merged = if (reset) page.items else current.items + page.items
            _state.value = _state.value.copy(
                items = merged.distinctBy { it.notification_id },
                nextCursor = page.next_cursor,
                loading = false,
                loadingMore = false,
            )
            refreshSummary()
        }.onFailure { error ->
            _state.value = _state.value.copy(
                loading = false,
                loadingMore = false,
                error = error.toUserMessage(),
            )
        }
    }

    private suspend fun refreshSummary() {
        runCatching { api.notificationSummary() }
            .onSuccess { onUnreadChanged(it.unread_count) }
    }
}
