package com.ashareai.app.standalone.data.ai

import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.ResearchScore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPayloadBuilderTest {
    @Test
    fun excludesHoldingCostAndQuantityWithoutExplicitAuthorization() {
        val prompt = AiPayloadBuilder.researchPrompt(
            result = result(),
            candles = emptyList(),
            holding = Holding("600519", "贵州茅台", 100.0, 1234.5, 0),
            includePortfolio = false,
        )

        assertFalse(prompt.contains("1234.5"))
        assertFalse(prompt.contains("数量 100"))
    }

    @Test
    fun includesHoldingOnlyAfterExplicitAuthorization() {
        val prompt = AiPayloadBuilder.researchPrompt(
            result = result(),
            candles = emptyList(),
            holding = Holding("600519", "贵州茅台", 100.0, 1234.5, 0),
            includePortfolio = true,
        )

        assertTrue(prompt.contains("1234.5"))
        assertTrue(prompt.contains("数量 100"))
    }

    private fun result() = ResearchResult(
        symbol = "600519",
        name = "贵州茅台",
        score = ResearchScore(null, null, null, null, null, null, null, 0.0, 50.0, emptyList()),
        risk = "中",
        summary = "本地摘要",
        quote = null,
    )
}
