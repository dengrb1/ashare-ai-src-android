package com.ashareai.app.standalone.research

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

    fun batches(symbols: List<String>): List<List<String>> =
        symbols.distinct().chunked(BATCH_SIZE)

    fun estimate(symbolCount: Int, aiEnabled: Boolean): ResearchEstimate {
        val safeCount = clampMarketLimit(symbolCount)
        val batches = (safeCount + BATCH_SIZE - 1) / BATCH_SIZE
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
