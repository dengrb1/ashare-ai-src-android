package com.ashareai.app.data

import com.ashareai.app.data.model.HealthResponse
import com.ashareai.app.data.model.InfrastructureHealth

/** The server compatibility level inferred from the public health contract. */
enum class ConnectionClassification {
    PendingConfiguration,
    Fusion,
    LegacyCompatible,
    Unsupported,
}

/** Short alias used by UI and contract tests. */
typealias ConnectionType = ConnectionClassification

enum class InfrastructureAvailability {
    Available,
    Unavailable,
    Unknown,
}

data class InfrastructureSummary(
    val api: InfrastructureAvailability = InfrastructureAvailability.Unknown,
    val database: InfrastructureAvailability = InfrastructureAvailability.Unknown,
    val quoteBridge: InfrastructureAvailability = InfrastructureAvailability.Unknown,
    val newsBridge: InfrastructureAvailability = InfrastructureAvailability.Unknown,
    val modelGateway: InfrastructureAvailability = InfrastructureAvailability.Unknown,
)

data class ConnectionProbe(
    val address: String? = null,
    val classification: ConnectionClassification,
    val infrastructure: InfrastructureSummary = InfrastructureSummary(),
    val message: String? = null,
    /** True when configuring this address invalidated a previous API session. */
    val sessionInvalidated: Boolean = false,
) {
    val canEstablishSession: Boolean
        get() = classification == ConnectionClassification.Fusion ||
            classification == ConnectionClassification.LegacyCompatible
}

class ConnectionRejectedException(val probe: ConnectionProbe) : IllegalStateException(
    probe.message ?: "服务器不支持研究只读模式。",
)

object HealthConnectionClassifier {
    fun classify(response: HealthResponse): ConnectionClassification {
        // Older health responses omit this field; an explicitly blank value is
        // equivalent to an omitted value rather than an unsafe mode.
        val executionMode = response.execution_mode
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase()
        if (response.qmt_enabled == true || response.auto_trading_enabled == true) {
            return ConnectionClassification.Unsupported
        }
        if (executionMode != null && executionMode != "RESEARCH_ONLY") {
            return ConnectionClassification.Unsupported
        }
        if (executionMode == "RESEARCH_ONLY") {
            return ConnectionClassification.Fusion
        }
        return ConnectionClassification.LegacyCompatible
    }

    fun summarize(response: HealthResponse): InfrastructureSummary = InfrastructureSummary(
        api = response.status.toAvailability(),
        database = response.database.toAvailability(),
        quoteBridge = response.quote_bridge.toAvailability(),
        newsBridge = response.news_bridge.toAvailability(),
        modelGateway = response.gateway.toAvailability(),
    )

    private fun String?.toAvailability(): InfrastructureAvailability = when (this?.trim()?.lowercase()) {
        "ok", "healthy", "available", "up", "ready" -> InfrastructureAvailability.Available
        "unavailable", "down", "failed", "error", "degraded" -> InfrastructureAvailability.Unavailable
        else -> InfrastructureAvailability.Unknown
    }

    private fun InfrastructureHealth?.toAvailability(): InfrastructureAvailability {
        val value = this ?: return InfrastructureAvailability.Unknown
        value.configured?.let { if (!it) return InfrastructureAvailability.Unavailable }
        value.degraded?.let { if (it) return InfrastructureAvailability.Unavailable }
        value.healthy?.let { return if (it) InfrastructureAvailability.Available else InfrastructureAvailability.Unavailable }
        return value.status.toAvailability()
    }
}
