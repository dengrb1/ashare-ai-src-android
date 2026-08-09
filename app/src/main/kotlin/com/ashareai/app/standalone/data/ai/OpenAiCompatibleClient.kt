package com.ashareai.app.standalone.data.ai

import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
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
    data class Failed(val message: String) : AiStreamEvent
}

object AiFallbackPolicy {
    fun mayFallbackFromResponses(httpStatus: Int): Boolean = httpStatus in setOf(404, 405, 501)
}

class OpenAiCompatibleClient(
    private val providerRepository: AiProviderRepository,
    private val httpClient: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun stream(request: AiRequest): Flow<AiStreamEvent> = callbackFlow {
        val credential = try {
            providerRepository.credential(request.providerId)
        } catch (error: Exception) {
            trySend(AiStreamEvent.Failed(error.message ?: "无法读取 AI 凭据"))
            close()
            return@callbackFlow
        }
        trySend(AiStreamEvent.Started)
        try {
            try {
                streamEndpoint(
                    credential = credential,
                    path = "v1/responses",
                    payload = responsesPayload(credential, request),
                    responseFormat = ResponseFormat.RESPONSES,
                    onDelta = { trySend(AiStreamEvent.Delta(it)) },
                )
            } catch (error: EndpointHttpException) {
                if (!AiFallbackPolicy.mayFallbackFromResponses(error.statusCode)) throw error
                trySend(AiStreamEvent.FallingBackToChatCompletions)
                streamEndpoint(
                    credential = credential,
                    path = "v1/chat/completions",
                    payload = chatCompletionsPayload(credential, request),
                    responseFormat = ResponseFormat.CHAT_COMPLETIONS,
                    onDelta = { trySend(AiStreamEvent.Delta(it)) },
                )
            }
            trySend(AiStreamEvent.Completed)
        } catch (error: Exception) {
            trySend(AiStreamEvent.Failed(error.userSafeMessage()))
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
            .header("Authorization", "Bearer " + credential.apiKey)
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .apply {
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
