package com.ashareai.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResearchValidationTest {
    @Test
    fun parsesNormalizesAndDeduplicatesSymbols() {
        assertEquals(
            listOf("600519.SH", "000001.SZ", "430047.BJ"),
            parseResearchSymbols("600519, 000001.SZ；600519.SH 430047"),
        )
    }

    @Test
    fun validatesBudgetRelationships() {
        assertEquals(
            "单股最高投入不能超过总预算",
            validateResearchInput("MARKET", 0, emptyList(), 10.0, 11.0, "", null),
        )
        assertNull(validateResearchInput("MARKET", 0, emptyList(), 100.0, 10.0, "", null))
    }

    @Test
    fun requiresDirectedSymbols() {
        assertEquals(
            "请至少选择一只有效 A 股",
            validateResearchInput("CUSTOM", 0, emptyList(), 100.0, 10.0, "", null),
        )
    }
}
