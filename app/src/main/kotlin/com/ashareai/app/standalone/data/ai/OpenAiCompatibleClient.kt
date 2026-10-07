package com.ashareai.app.standalone.data.ai

import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class AiRequest(
    val providerId: String,
    val systemInstruction: String,
    val prompt: String,
)

sealed interface AiStreamEvent {
    data object Started : AiStreamEvent
    data object FallingBackToChatCompletions : AiStreamEvent
    data class Delta(val text: String) : AiStreamEvent
    data object Completed : AiStreamEvent
    data class Failed(val message: String, val retryable: Boolean = true) : AiStreamEvent
}

object AiFallbackPolicy {
    fun mayFallbackFromResponses(httpStatus: Int): Boolean = httpStatus in setOf(404, 405, 501)
}

class OpenAiCompatibleClient(
    private val providerRepository: AiProviderRepository,
    private val httpClient: OkHttpClient,
    private val cacheManager: AiCacheManager? = null,
    private val healthTracker: ProviderHealthTracker = ProviderHealthTracker(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun providerRuntimeStates(): List<AiProviderRuntimeState> = healthTracker.snapshot()

    fun streamCached(request: AiRequest): Flow<AiStreamEvent> = flow {
        val cached = cacheManager?.get(request.providerId, request.systemInstruction, request.prompt)
        if (cached == null) {
            emit(AiStreamEvent.Failed("缓存未命中", retryable = false))
        } else {
            emit(AiStreamEvent.Started)
            emit(AiStreamEvent.Delta(cached))
            emit(AiStreamEvent.Completed)
        }
    }

    fun streamWithFallback(request: AiRequest, fallbackProviderIds: List<String>): Flow<AiStreamEvent> = flow {
        val candidates = (listOf(request.providerId) + fallbackProviderIds).distinct()
        for ((index, providerId) in candidates.withIndex()) {
            var emittedDelta = false
            var failed = false
            var retryable = true
            stream(request.copy(providerId = providerId)).collect { event ->
                if (event is AiStreamEvent.Delta) emittedDelta = true
                if (event is AiStreamEvent.Failed) {
                    failed = true
                    retryable = event.retryable
                }
                emit(event)
            }
            if (!failed || emittedDelta || index == candidates.lastIndex || !retryable) return@flow
        }
    }

    fun stream(request: AiRequest): Flow<AiStreamEvent> = callbackFlow {
        val credential = try {
            providerRepository.credential(request.providerId)
        } catch (error: Exception) {
            healthTracker.markFailure(request.providerId)
            trySend(AiStreamEvent.Failed(error.message ?: "无法读取 AI 凭据", retryable = false))
            close()
            return@callbackFlow
        }

        // 尝试从缓存读取
        val cached = cacheManager?.get(request.providerId, request.systemInstruction, request.prompt)
        if (cached != null) {
            trySend(AiStreamEvent.Started)
            trySend(AiStreamEvent.Delta(cached))
            trySend(AiStreamEvent.Completed)
            close()
            return@callbackFlow
        }

        trySend(AiStreamEvent.Started)
        val responseBuilder = StringBuilder()
        try {
            try {
                streamEndpoint(
                    credential = credential,
                    path = "v1/responses",
                    payload = responsesPayload(credential, request),
                    responseFormat = ResponseFormat.RESPONSES,
                    onDelta = {
                        responseBuilder.append(it)
                        trySend(AiStreamEvent.Delta(it))
                    },
                )
            } catch (error: EndpointHttpException) {
                if (!AiFallbackPolicy.mayFallbackFromResponses(error.statusCode)) throw error
                trySend(AiStreamEvent.FallingBackToChatCompletions)
                streamEndpoint(
                    credential = credential,
                    path = "v1/chat/completions",
                    payload = chatCompletionsPayload(credential, request),
                    responseFormat = ResponseFormat.CHAT_COMPLETIONS,
                    onDelta = {
                        responseBuilder.append(it)
                        trySend(AiStreamEvent.Delta(it))
                    },
                )
            }
            // 保存到缓存
            val fullResponse = responseBuilder.toString()
            if (fullResponse.isNotBlank()) {
                cacheManager?.put(request.providerId, request.systemInstruction, request.prompt, fullResponse)
            }
            healthTracker.markSuccess(request.providerId)
            trySend(AiStreamEvent.Completed)
        } catch (error: Exception) {
            val retryable = error.isRetryable()
            healthTracker.markFailure(request.providerId)
            trySend(AiStreamEvent.Failed(error.userSafeMessage(), retryable))
        } finally {
            close()
        }
        awaitClose()
    }

    private suspend fun streamEndpoint(
        credential: AiProviderCredential,
        path: String,
        payload: JsonObject,
        responseFormat: ResponseFormat,
        onDelta: (String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(endpoint(credential.provider.baseUrl, path))
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .apply {
                credential.apiKey.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
                credential.provider.organization?.let { header("OpenAI-Organization", it) }
                credential.provider.project?.let { header("OpenAI-Project", it) }
            }
            .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA_TYPE))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw EndpointHttpException(response.code)
            val body = response.body ?: throw java.io.IOException("AI 响应为空")
            body.charStream().buffered().use { reader ->
                readServerSentEvents(reader, responseFormat, onDelta)
            }
        }
    }

    private fun readServerSentEvents(
        reader: BufferedReader,
        responseFormat: ResponseFormat,
        onDelta: (String) -> Unit,
    ) {
        reader.forEachLine { line ->
            if (!line.startsWith("data:")) return@forEachLine
            val data = line.removePrefix("data:").trim()
            if (data.isBlank() || data == "[DONE]") return@forEachLine
            val delta = parseDelta(data, responseFormat)
            if (!delta.isNullOrEmpty()) onDelta(delta)
        }
    }

    private fun parseDelta(data: String, format: ResponseFormat): String? = runCatching {
        val root = json.parseToJsonElement(data).jsonObject
        when (format) {
            ResponseFormat.RESPONSES -> {
                if (root["type"]?.jsonPrimitive?.contentOrNull != "response.output_text.delta") {
                    null
                } else {
                    root["delta"]?.jsonPrimitive?.contentOrNull
                }
            }
            ResponseFormat.CHAT_COMPLETIONS -> (root["choices"] as? JsonArray)
                ?.firstOrNull()
                ?.jsonObject
                ?.get("delta")
                ?.jsonObject
                ?.get("content")
                ?.jsonPrimitive
                ?.contentOrNull
        }
    }.getOrNull()

    private fun responsesPayload(credential: AiProviderCredential, request: AiRequest): JsonObject =
        buildJsonObject {
            put("model", credential.provider.model)
            put("stream", true)
            putJsonArray("input") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", request.systemInstruction)
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", request.prompt)
                    },
                )
            }
        }

    private fun chatCompletionsPayload(credential: AiProviderCredential, request: AiRequest): JsonObject =
        buildJsonObject {
            put("model", credential.provider.model)
            put("stream", true)
            putJsonArray("messages") {
                add(
                    buildJsonObject {
                        put("role", "system")
                        put("content", request.systemInstruction)
                    },
                )
                add(
                    buildJsonObject {
                        put("role", "user")
                        put("content", request.prompt)
                    },
                )
            }
        }

    private fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.removeSuffix("/")
        val suffix = if (base.endsWith("/v1")) path.removePrefix("v1/") else path
        return base + "/" + suffix
    }

    private fun Exception.userSafeMessage(): String = when (this) {
        is EndpointHttpException -> when (statusCode) {
            401, 403 -> "AI Provider 认证失败，未尝试其他端点"
            402, 429 -> "AI Provider 额度或限流失败，未尝试其他端点"
            else -> "AI 请求失败：HTTP " + statusCode
        }
        is java.net.SocketTimeoutException -> "AI 请求超时，未尝试其他端点"
        is java.io.IOException -> "AI 网络请求失败，未尝试其他端点"
        else -> message ?: "AI 请求失败"
    }

    private fun Exception.isRetryable(): Boolean = when (this) {
        is EndpointHttpException -> statusCode == 408 || statusCode == 425 || statusCode == 429 || statusCode >= 500
        is java.net.SocketTimeoutException, is java.io.IOException -> true
        else -> false
    }

    private enum class ResponseFormat {
        RESPONSES,
        CHAT_COMPLETIONS,
    }

    private class EndpointHttpException(
        val statusCode: Int,
    ) : Exception("AI endpoint failed")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
