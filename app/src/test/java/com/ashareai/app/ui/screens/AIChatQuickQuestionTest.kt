package com.ashareai.app.ui.screens

import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class AIChatQuickQuestionTest {
    @Test
    fun appendsQuickQuestionAndPlacesCaretAtEnd() {
        val result = appendQuickQuestion(
            TextFieldValue("@贵州茅台   "),
            "请生成个股省流版，并说明是否适合继续查看模拟方案。",
        )

        assertEquals("@贵州茅台 请生成个股省流版，并说明是否适合继续查看模拟方案。", result.text)
        assertEquals(result.text.length, result.selection.start)
        assertEquals(result.text.length, result.selection.end)
    }

    @Test
    fun usesQuestionForEmptyDraftAndMatchesWebLabels() {
        val result = appendQuickQuestion(TextFieldValue(""), quickQuestions[1].second)

        assertEquals("请生成个股省流版，并说明是否适合继续查看模拟方案。", result.text)
        assertEquals(
            listOf("解读最新系统研究报告", "生成个股省流版", "分析持仓风险", "比较候选股票"),
            quickQuestions.map { it.first },
        )
    }
}
