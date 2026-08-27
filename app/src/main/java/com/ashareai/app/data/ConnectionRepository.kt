package com.ashareai.app.data

import com.ashareai.app.data.model.HealthResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/**
 * Probes only FastAPI's public health endpoint. Bridge and gateway details are
 * reduced to availability states before reaching the UI.
 */
class ConnectionRepository(
    private val settings: ConnectionSettings,
    private val services: ApiServiceProvider,
    private val healthServiceForAddress: ((String) -> HealthApi)? = null,
    private val healthTimeoutMillis: Long = HEALTH_TIMEOUT_MILLIS,
    private val rebuildClient: () -> Unit = ApiClient::rebuild,
) {
    suspend fun probeConfiguredServer(): ConnectionProbe {
        val address = settings.currentBaseUrl().trim()
        if (address.isBlank()) {
            return ConnectionProbe(
                classification = ConnectionClassification.PendingConfiguration,
                message = "请先配置 Fusion 服务地址。",
            )
        }
        val normalized = normalizeServerUrl(address).getOrElse { error ->
            return ConnectionProbe(
                address = null,
                classification = ConnectionClassification.PendingConfiguration,
                message = error.message ?: "服务器地址格式不正确。",
            )
        }
        return probe(normalized)
    }

    suspend fun probe(addressInput: String): ConnectionProbe {
        val normalized = normalizeServerUrl(addressInput).getOrElse { error ->
            return ConnectionProbe(
                classification = ConnectionClassification.PendingConfiguration,
                message = error.message ?: "服务器地址格式不正确。",
            )
        }
        return probe(normalized, healthApi(normalized))
    }

    suspend fun configure(addressInput: String): ConnectionProbe {
        val normalized = normalizeServerUrl(addressInput).getOrElse { error ->
            return ConnectionProbe(
                classification = ConnectionClassification.PendingConfiguration,
                message = error.message ?: "服务器地址格式不正确。",
            )
        }
        val probe = probe(normalized)
        val previous = settings.currentBaseUrl()
        settings.setBaseUrl(normalized)
        val sessionInvalidated = previous != normalized || probe.classification == ConnectionClassification.Unsupported
        if (sessionInvalidated) settings.clearTokens()
        rebuildClient()
        return probe.copy(address = normalized, sessionInvalidated = sessionInvalidated)
    }

    private suspend fun probe(address: String, healthApi: HealthApi): ConnectionProbe = try {
        val response = withTimeout(healthTimeoutMillis) { healthApi.health() }
        fromHealth(address, response)
    } catch (_: TimeoutCancellationException) {
        ConnectionProbe(
            address = address,
            classification = ConnectionClassification.PendingConfiguration,
            message = "健康检查超时，请检查服务器地址和网络后重试。",
        )
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ConnectionProbe(
            address = address,
            classification = ConnectionClassification.PendingConfiguration,
            message = "无法连接 Fusion 服务，请检查服务器地址和网络后重试。",
        )
    }

    private fun healthApi(address: String): HealthApi =
        healthServiceForAddress?.invoke(address) ?: services.healthService(address)

    private fun fromHealth(address: String, response: HealthResponse): ConnectionProbe {
        val classification = HealthConnectionClassifier.classify(response)
        val message = when (classification) {
            ConnectionClassification.Fusion -> "Fusion 研究服务已就绪。"
            ConnectionClassification.LegacyCompatible -> "服务器使用兼容健康契约，研究功能以受限模式运行。"
            ConnectionClassification.Unsupported -> "服务器不是 RESEARCH_ONLY，或启用了 QMT/自动交易，已拒绝建立会话。"
            ConnectionClassification.PendingConfiguration -> "请先配置 Fusion 服务地址。"
        }
        return ConnectionProbe(
            address = address,
            classification = classification,
            infrastructure = HealthConnectionClassifier.summarize(response),
            message = message,
        )
    }

    companion object {
        const val HEALTH_TIMEOUT_MILLIS = 7_000L
    }
}
