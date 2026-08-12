package com.ashareai.app.data

import com.ashareai.app.data.model.AIChatSendRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.UUID

/** AI 对话 SSE 事件（对应后端 event: meta/stage/delta/done/error）。 */
sealed class ChatStreamEvent {
    data class Meta(val payload: JsonObject) : ChatStreamEvent()
    data class Stage(val stage: String, val status: String?, val payload: JsonObject) : ChatStreamEvent()
    data class Delta(val text: String) : ChatStreamEvent()
    data class Done(val payload: JsonObject) : ChatStreamEvent()
    data class Error(val code: String?, val message: String, val retryable: Boolean) : ChatStreamEvent()
    data object Closed : ChatStreamEvent()
}

object ChatStreamClient {

    private val json = ApiClient.json

    /** 发送消息并以 Flow 形式收流。collect 取消时自动断开连接。 */
    fun stream(
        settings: SettingsStore,
        threadId: String,
        request: AIChatSendRequest,
    ): Flow<ChatStreamEvent> = callbackFlow {
        val base = runBlocking { settings.currentBaseUrl() }.trimEnd('/')
        val token = runBlocking { settings.currentAccessToken() }
        val body = json.encodeToString(AIChatSendRequest.serializer(), request)
        val httpRequest = Request.Builder()
            .url("$base/api/v1/ai/chat/threads/$threadId/messages:stream")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "text/event-stream")
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
            .build()

        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val event = parseEvent(type, data)
                if (event != null) trySend(event)
                if (event is ChatStreamEvent.Done || event is ChatStreamEvent.Error) {
                    close()
                }
            }

            override fun onClosed(eventSource: EventSource) {
                trySend(ChatStreamEvent.Closed)
                close()
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                val msg = t?.message ?: response?.let { "HTTP ${it.code}" } ?: "连接失败"
                trySend(ChatStreamEvent.Error(code = null, message = msg, retryable = true))
                close()
            }
        }

        val source = EventSources.createFactory(ApiClient.okHttp()).newEventSource(httpRequest, listener)
        awaitClose { source.cancel() }
    }

    private fun parseEvent(type: String?, data: String): ChatStreamEvent? {
        val obj = try {
            json.parseToJsonElement(data) as? JsonObject
        } catch (_: Exception) {
            null
        }
        return when (type) {
            "delta" -> {
                val text = obj?.get("text")?.jsonPrimitive?.content
                    ?: obj?.get("content")?.jsonPrimitive?.content
                    ?: data
                ChatStreamEvent.Delta(text)
            }
            "stage" -> ChatStreamEvent.Stage(
                stage = obj?.get("stage")?.jsonPrimitive?.content ?: "",
                status = obj?.get("status")?.jsonPrimitive?.content,
                payload = obj ?: JsonObject(emptyMap()),
            )
            "meta" -> ChatStreamEvent.Meta(obj ?: JsonObject(emptyMap()))
            "done" -> ChatStreamEvent.Done(obj ?: JsonObject(emptyMap()))
            "error" -> ChatStreamEvent.Error(
                code = obj?.get("code")?.jsonPrimitive?.content,
                message = obj?.get("message")?.jsonPrimitive?.content ?: "生成失败",
                retryable = obj?.get("retryable")?.jsonPrimitive?.content == "true",
            )
            else -> if (data.isNotBlank()) ChatStreamEvent.Delta(data) else null
        }
    }
}
