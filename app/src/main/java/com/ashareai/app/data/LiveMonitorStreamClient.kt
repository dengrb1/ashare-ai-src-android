package com.ashareai.app.data

import com.ashareai.app.data.model.LiveMonitorSignal
import com.ashareai.app.data.model.LivePositionMonitor
import com.ashareai.app.data.model.Quote
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

/** Mobile live-monitor SSE. Consumers should retain REST as the recovery path. */
sealed class LiveMonitorStreamEvent {
    data class Status(
        val state: String,
        val nextPollSeconds: Int = 30,
        val stale: Boolean = false,
        val marketError: String? = null,
    ) : LiveMonitorStreamEvent()

    data class QuoteUpdate(
        val position: LivePositionMonitor? = null,
        val watchlist: com.ashareai.app.data.model.Quote? = null,
    ) : LiveMonitorStreamEvent()
    data class Risk(val position: LivePositionMonitor) : LiveMonitorStreamEvent()
    data class Signal(val signal: LiveMonitorSignal) : LiveMonitorStreamEvent()
    data class Failure(val message: String) : LiveMonitorStreamEvent()
    data object Closed : LiveMonitorStreamEvent()
}

object LiveMonitorStreamClient {
    fun stream(
        settings: SettingsStore,
        intervalSeconds: Int = 30,
        includeWatchlist: Boolean = false,
        services: ApiServiceProvider = ApiClient,
    ): Flow<LiveMonitorStreamEvent> = callbackFlow {
        val base = settings.currentBaseUrl().trimEnd('/')
        val token = settings.currentAccessToken()
        val query = "?interval=${intervalSeconds.coerceIn(5, 120)}&include_watchlist=$includeWatchlist"
        val request = Request.Builder()
            .url("$base/api/v1/mobile/monitor/stream$query")
            .header("Accept", "text/event-stream")
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                parse(type, data)?.let { trySend(it) }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(LiveMonitorStreamEvent.Closed)
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                trySend(LiveMonitorStreamEvent.Failure(t?.message ?: response?.let { "HTTP ${it.code}" } ?: "连接失败"))
                close()
            }
        }
        val source = EventSources.createFactory(services.httpClient() ?: ApiClient.okHttp()).newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    internal fun parse(type: String?, data: String): LiveMonitorStreamEvent? {
        val obj = runCatching { ApiClient.json.parseToJsonElement(data) as? JsonObject }.getOrNull()
            ?: return null
        return when (type?.uppercase()) {
            "STATUS" -> LiveMonitorStreamEvent.Status(
                state = obj["state"]?.jsonPrimitive?.content ?: "UNKNOWN",
                nextPollSeconds = obj["next_poll_seconds"]?.jsonPrimitive?.intOrNull ?: 30,
                stale = obj["stale"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false,
                marketError = obj["market_error"]?.jsonPrimitive?.content,
            )
            "QUOTE" -> runCatching {
                if (obj.containsKey("risk_state") || obj.containsKey("quantity")) {
                    LiveMonitorStreamEvent.QuoteUpdate(position = ApiClient.json.decodeFromJsonElement(LivePositionMonitor.serializer(), obj))
                } else {
                    LiveMonitorStreamEvent.QuoteUpdate(watchlist = ApiClient.json.decodeFromJsonElement(com.ashareai.app.data.model.Quote.serializer(), obj))
                }
            }.getOrNull()
            "RISK" -> runCatching {
                LiveMonitorStreamEvent.Risk(ApiClient.json.decodeFromJsonElement(LivePositionMonitor.serializer(), obj))
            }.getOrNull()
            "SIGNAL" -> runCatching {
                LiveMonitorStreamEvent.Signal(ApiClient.json.decodeFromJsonElement(LiveMonitorSignal.serializer(), obj))
            }.getOrNull()
            else -> null
        }
    }
}
