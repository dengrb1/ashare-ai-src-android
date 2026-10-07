package com.ashareai.app.standalone.research

object DeterministicExplanationFallback {
    fun create(
        reason: Reason,
        marketSummary: String,
        candidateSummaries: List<String>,
    ): String = buildString {
        appendLine(
            when (reason) {
                Reason.NO_PROVIDER -> "未配置可调用的 LLM；本报告由本地确定性规则完整生成。"
                Reason.UNAVAILABLE -> "LLM 当前不可用；本报告由本地确定性规则完整生成。"
                Reason.LOCAL_ENGINE_UNAVAILABLE -> "未接入可调用的原生端侧推理引擎；本报告由本地确定性规则完整生成。"
            },
        )
        appendLine("确定性评分、风险分级、动作和交易门槛均保持原规则结果。")
        appendLine(marketSummary)
        if (candidateSummaries.isNotEmpty()) {
            appendLine("研究摘要：")
            candidateSummaries.take(5).forEach { appendLine("- $it") }
        }
    }.trim()

    enum class Reason {
        NO_PROVIDER,
        UNAVAILABLE,
        LOCAL_ENGINE_UNAVAILABLE,
    }
}
