package com.ashareai.app.standalone.research

import com.ashareai.app.performance.ResourceBudget

data class ResearchEstimate(
    val symbols: Int,
    val batches: Int,
    val estimatedNetworkRequests: Int,
    val estimatedMinutes: Int,
    val aiSymbols: Int,
)

object ResearchBatchPlanner {
    const val DEFAULT_MARKET_LIMIT = 100
    const val MAX_MARKET_LIMIT = 500
    const val BATCH_SIZE = 25

    fun clampMarketLimit(limit: Int): Int = limit.coerceIn(1, MAX_MARKET_LIMIT)

    fun batches(symbols: List<String>, batchSize: Int = BATCH_SIZE): List<List<String>> =
        symbols.distinct().chunked(batchSize.coerceIn(1, MAX_MARKET_LIMIT))

    fun estimate(symbolCount: Int, aiEnabled: Boolean): ResearchEstimate {
        return estimate(symbolCount, aiEnabled, BATCH_SIZE)
    }

    /** Uses the same chunk size as the executor so estimates remain actionable. */
    fun estimate(symbolCount: Int, aiEnabled: Boolean, budget: ResourceBudget): ResearchEstimate =
        estimate(symbolCount, aiEnabled, budget.chunkSize)

    fun estimate(symbolCount: Int, aiEnabled: Boolean, batchSize: Int): ResearchEstimate {
        val safeCount = clampMarketLimit(symbolCount)
        val safeBatchSize = batchSize.coerceIn(1, MAX_MARKET_LIMIT)
        val batches = (safeCount + safeBatchSize - 1) / safeBatchSize
        // One batched quote request plus one K-line request for each selected security.
        val requests = batches + safeCount
        return ResearchEstimate(
            symbols = safeCount,
            batches = batches,
            estimatedNetworkRequests = requests,
            estimatedMinutes = maxOf(1, (requests * 0.7).toInt() / 60 + 1),
            aiSymbols = if (aiEnabled) safeCount else 0,
        )
    }
}
