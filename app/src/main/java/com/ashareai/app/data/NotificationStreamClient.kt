package com.ashareai.app.data

import com.ashareai.app.data.model.Notification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlinx.coroutines.runBlocking

/** Bounded notification SSE client. Callers keep REST polling as the fallback. */
sealed class NotificationStreamEvent {
    data class NotificationEvent(val notification: Notification) : NotificationStreamEvent()
    data class Status(val state: String, val unreadCount: Int = 0) : NotificationStreamEvent()
    data class Failure(val message: String) : NotificationStreamEvent()
    data object Closed : NotificationStreamEvent()
}

object NotificationStreamClient {
    fun stream(
        settings: SettingsStore,
        intervalSeconds: Int = 3,
        since: String? = null,
        services: ApiServiceProvider = ApiClient,
    ): Flow<NotificationStreamEvent> = callbackFlow {
        val base = runBlocking { settings.currentBaseUrl() }.trimEnd('/')
        val token = runBlocking { settings.currentAccessToken() }
        val query = buildString {
            append("?interval=")
            append(intervalSeconds.coerceIn(1, 30))
            if (!since.isNullOrBlank()) {
                append("&since=")
                append(java.net.URLEncoder.encode(since, Charsets.UTF_8.name()))
            }
        }
        val request = Request.Builder()
            .url("$base/api/v1/mobile/notifications/stream$query")
            .header("Accept", "text/event-stream")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        val listener = object : EventSourceListener() {
            override fun onEvent(source: EventSource, id: String?, type: String?, data: String) {
                parse(type, data)?.let { trySend(it) }
            }

            override fun onClosed(source: EventSource) {
                trySend(NotificationStreamEvent.Closed)
                close()
            }

            override fun onFailure(source: EventSource, t: Throwable?, response: Response?) {
                trySend(NotificationStreamEvent.Failure(t?.message ?: response?.let { "HTTP ${it.code}" } ?: "连接失败"))
                close()
            }
        }
        val eventSource = EventSources.createFactory(services.httpClient() ?: ApiClient.okHttp()).newEventSource(request, listener)
        awaitClose { eventSource.cancel() }
    }

    private fun parse(type: String?, data: String): NotificationStreamEvent? {
        val obj = runCatching { ApiClient.json.parseToJsonElement(data) as? JsonObject }.getOrNull()
            ?: return null
        return when (type) {
            "NOTIFICATION" -> runCatching {
                NotificationStreamEvent.NotificationEvent(ApiClient.json.decodeFromJsonElement(Notification.serializer(), obj))
            }.getOrNull()
            "STATUS" -> NotificationStreamEvent.Status(
                state = obj["state"]?.jsonPrimitive?.content ?: "UNKNOWN",
                unreadCount = obj["unread_count"]?.jsonPrimitive?.intOrNull ?: 0,
            )
            else -> null
        }
    }
}
