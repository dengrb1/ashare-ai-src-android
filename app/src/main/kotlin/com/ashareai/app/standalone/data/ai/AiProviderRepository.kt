package com.ashareai.app.standalone.data.ai

import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.local.AiProviderEntity
import com.ashareai.app.standalone.domain.AiProvider
import java.util.UUID
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class AiProviderDraft(
    val id: String? = null,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val organization: String? = null,
    val project: String? = null,
    val enabled: Boolean = true,
)

internal data class AiProviderCredential(
    val provider: AiProvider,
    val apiKey: String,
)

class AiProviderRepository(
    private val local: LocalRepository,
    private val cipher: ApiKeyCipher,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val providers = local.aiProviders

    suspend fun save(draft: AiProviderDraft): AiProvider {
        val id = draft.id ?: UUID.randomUUID().toString()
        val existing = local.aiProvider(id)
        val baseUrl = normalizeBaseUrl(draft.baseUrl)
        val apiKey = when {
            draft.apiKey.isNotBlank() -> cipher.encrypt(draft.apiKey.trim())
            existing != null -> existing.encryptedApiKey
            AiProviderEndpointPolicy.isLoopbackHttp(baseUrl) -> cipher.encrypt(LOCAL_NO_AUTH_KEY)
            else -> throw IllegalArgumentException("新 Provider 必须填写 API Key")
        }
        val provider = AiProviderEntity(
            id = id,
            name = draft.name.trim().ifBlank { "未命名 Provider" },
            baseUrl = baseUrl,
            encryptedApiKey = apiKey,
            model = draft.model.trim().ifBlank { throw IllegalArgumentException("模型不能为空") },
            organization = draft.organization?.trim()?.takeIf(String::isNotBlank),
            project = draft.project?.trim()?.takeIf(String::isNotBlank),
            enabled = draft.enabled,
            createdAt = existing?.createdAt ?: clock(),
        )
        local.saveAiProvider(provider)
        return AiProvider(
            id = provider.id,
            name = provider.name,
            baseUrl = provider.baseUrl,
            model = provider.model,
            organization = provider.organization,
            project = provider.project,
            enabled = provider.enabled,
            createdAt = provider.createdAt,
        )
    }

    internal suspend fun credential(providerId: String): AiProviderCredential {
        val entity = local.aiProvider(providerId) ?: throw IllegalArgumentException("未找到 AI Provider")
        check(entity.enabled) { "AI Provider 已禁用" }
        return AiProviderCredential(
            provider = AiProvider(
                id = entity.id,
                name = entity.name,
                baseUrl = entity.baseUrl,
                model = entity.model,
                organization = entity.organization,
                project = entity.project,
                enabled = entity.enabled,
                createdAt = entity.createdAt,
            ),
            apiKey = cipher.decrypt(entity.encryptedApiKey).takeUnless { it == LOCAL_NO_AUTH_KEY }.orEmpty(),
        )
    }

    suspend fun remove(id: String) = local.removeAiProvider(id)

    private fun normalizeBaseUrl(value: String): String {
        val normalized = value.trim().removeSuffix("/")
        val url = normalized.toHttpUrlOrNull() ?: throw IllegalArgumentException("Base URL 无效")
        require(url.scheme == "https" || AiProviderEndpointPolicy.isLoopbackHttp(url.toString())) {
            "AI Provider 必须使用 HTTPS；HTTP 仅允许本机端侧服务"
        }
        return normalized
    }

    companion object {
        private const val LOCAL_NO_AUTH_KEY = "__local_endpoint_without_auth__"
        fun isLocalEndpoint(value: String): Boolean = AiProviderEndpointPolicy.isLoopbackHttp(value)
    }
}
