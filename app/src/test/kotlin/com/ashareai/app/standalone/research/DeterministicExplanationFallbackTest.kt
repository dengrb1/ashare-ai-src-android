package com.ashareai.app.standalone.research

import org.junit.Assert.assertTrue
import org.junit.Test

class DeterministicExplanationFallbackTest {
    @Test
    fun missingLlmStillProducesUsefulReportExplanation() {
        val explanation = DeterministicExplanationFallback.create(
            reason = DeterministicExplanationFallback.Reason.NO_PROVIDER,
            marketSummary = "冻结大盘环境：大盘中性",
            candidateSummaries = listOf("000001 平安银行：趋势中性，评分 62"),
        )

        assertTrue(explanation.contains("未配置可调用的 LLM"))
        assertTrue(explanation.contains("确定性规则完整生成"))
        assertTrue(explanation.contains("冻结大盘环境"))
        assertTrue(explanation.contains("000001 平安银行"))
    }
}
