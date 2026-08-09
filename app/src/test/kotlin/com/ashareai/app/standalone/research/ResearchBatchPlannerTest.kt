package com.ashareai.app.standalone.research

import org.junit.Assert.assertEquals
import org.junit.Test

class ResearchBatchPlannerTest {
    @Test
    fun batchesHundredAndFiveHundredSymbolsDeterministically() {
        assertEquals(4, ResearchBatchPlanner.batches((1..100).map(Int::toString)).size)
        assertEquals(20, ResearchBatchPlanner.batches((1..500).map(Int::toString)).size)
        assertEquals(500, ResearchBatchPlanner.clampMarketLimit(800))
    }

    @Test
    fun estimateSeparatesAiScopeFromDeterministicRequests() {
        val estimate = ResearchBatchPlanner.estimate(100, aiEnabled = true)

        assertEquals(104, estimate.estimatedNetworkRequests)
        assertEquals(100, estimate.aiSymbols)
    }
}
