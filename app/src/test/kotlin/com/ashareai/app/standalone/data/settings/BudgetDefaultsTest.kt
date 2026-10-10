package com.ashareai.app.standalone.data.settings

import com.ashareai.app.standalone.domain.ResearchRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class BudgetDefaultsTest {
    @Test
    fun newResearchDefaultsAreSmallSimulationBudgets() {
        val defaults = defaultAutomaticReports()
        assertEquals(100_000.0, defaults.first().totalBudget, 0.0)
        assertEquals(10_000.0, defaults.first().perSymbolBudget, 0.0)
        assertEquals(100_000.0, ResearchRequest(scope = com.ashareai.app.standalone.domain.ResearchScope.MARKET, symbols = emptyList(), marketLimit = 10, includePortfolioDataForAi = false, aiProviderId = null).totalBudget, 0.0)
        assertEquals(10_000.0, ResearchRequest(scope = com.ashareai.app.standalone.domain.ResearchScope.MARKET, symbols = emptyList(), marketLimit = 10, includePortfolioDataForAi = false, aiProviderId = null).perSymbolBudget, 0.0)
    }

    @Test
    fun migrationOnlyReplacesUntouchedLegacyDefaults() {
        assertEquals(100_000.0, migrateLegacyBudget(1_000_000.0, 1_000_000.0, 100_000.0), 0.0)
        assertEquals(10_000.0, migrateLegacyBudget(80_000.0, 80_000.0, 10_000.0), 0.0)
        assertEquals(250_000.0, migrateLegacyBudget(250_000.0, 1_000_000.0, 100_000.0), 0.0)
        assertEquals(100_000.0, migrateLegacyBudget(null, 1_000_000.0, 100_000.0), 0.0)
    }
}
