package com.ashareai.app.workspace

import com.ashareai.app.data.model.HealthResponse
import com.ashareai.app.data.model.InfrastructureHealth

/**
 * Fusion 健康状态：解析 `/api/v1/health` 返回的执行模式、交易开关和基础设施可用性。
 * 用于判断服务器是否符合 RESEARCH_ONLY 契约，是否可以建立会话。
 */
data class FusionHealthState(
    val executionMode: String?,          // RESEARCH_ONLY | PRODUCTION | null
    val qmtEnabled: Boolean,
    val autoTradingEnabled: Boolean,
    val apiAvailable: Boolean,
    val databaseAvailable: Boolean,
    val quoteBridgeAvailable: Boolean,
    val newsBridgeAvailable: Boolean,
    val gatewayAvailable: Boolean,
) {
    /**
     * 是否可以建立会话：必须是 RESEARCH_ONLY 模式，且 QMT 和自动交易都未启用。
     */
    val canEstablishSession: Boolean
        get() = executionMode == "RESEARCH_ONLY" && !qmtEnabled && !autoTradingEnabled

    /**
     * 是否为纯研究模式：执行模式明确为 RESEARCH_ONLY。
     */
    val isResearchOnly: Boolean
        get() = executionMode == "RESEARCH_ONLY"
}

/**
 * 将 HealthResponse 转换为 FusionHealthState。
 */
fun HealthResponse.toFusionHealthState(): FusionHealthState {
    val executionMode = this.execution_mode
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.uppercase()

    return FusionHealthState(
        executionMode = executionMode,
        qmtEnabled = this.qmt_enabled == true,
        autoTradingEnabled = this.auto_trading_enabled == true,
        apiAvailable = this.status.toAvailability(),
        databaseAvailable = this.database.toAvailability(),
        quoteBridgeAvailable = this.quote_bridge.toAvailability(),
        newsBridgeAvailable = this.news_bridge.toAvailability(),
        gatewayAvailable = this.gateway.toAvailability(),
    )
}

private fun String?.toAvailability(): Boolean = when (this?.trim()?.lowercase()) {
    "ok", "healthy", "available", "up", "ready" -> true
    else -> false
}

private fun InfrastructureHealth?.toAvailability(): Boolean {
    val value = this ?: return false
    value.configured?.let { if (!it) return false }
    value.degraded?.let { if (it) return false }
    value.healthy?.let { return it }
    return value.status.toAvailability()
}
