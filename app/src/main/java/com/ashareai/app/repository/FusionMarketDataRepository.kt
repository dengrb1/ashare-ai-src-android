package com.ashareai.app.repository

import com.ashareai.app.data.KlineRepository
import com.ashareai.app.data.KlinePeriod
import com.ashareai.app.data.KlineRange
import com.ashareai.app.data.ApiService
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security

/**
 * Fusion 工作区行情数据仓库实现。
 *
 * 使用 Fusion API 获取行情和 K 线数据。
 */
class FusionMarketDataRepository(
    private val klineRepository: KlineRepository,
    private val api: ApiService? = null,
) : MarketDataRepository {

    override suspend fun catalog(limit: Int): List<Security> {
        val service = api ?: return emptyList()
        return service.financialSearch("").entities.take(limit.coerceIn(1, 500)).mapNotNull {
            val code = it.code ?: return@mapNotNull null
            Security(code, it.name ?: code, it.type ?: "")
        }
    }

    override suspend fun refreshQuotes(symbols: Collection<String>): List<MarketQuote> {
        val service = api ?: return emptyList()
        if (symbols.isEmpty()) return emptyList()
        val fetchedAt = System.currentTimeMillis()
        return service.quotes(symbols.distinct().joinToString(","), refresh = true).map {
            MarketQuote(
                symbol = it.symbol,
                name = it.name ?: it.symbol,
                lastPrice = it.price,
                previousClose = it.previous_close,
                changePercent = it.change_percent,
                volume = it.volume,
                provider = it.status?.source ?: "fusion",
                fetchedAt = fetchedAt,
                freshness = if (it.price != null) com.ashareai.app.standalone.domain.MarketFreshness.FRESH
                else com.ashareai.app.standalone.domain.MarketFreshness.UNAVAILABLE,
            )
        }
    }

    override suspend fun refreshQuote(symbol: String): MarketQuote {
        return refreshQuotes(listOf(symbol)).firstOrNull()
            ?: throw IllegalStateException("Quote not available for $symbol")
    }

    override suspend fun dailyCandles(
        symbol: String,
        limit: Int,
        forceRefresh: Boolean,
    ): List<DailyCandle> {
        val result = klineRepository.load(
            symbol = symbol,
            period = KlinePeriod.DAY,
            range = KlineRange.YEAR_1,
        )
        return result.bars.takeLast(limit).mapNotNull { bar ->
            val date = runCatching { java.time.Instant.parse(bar.timestamp).atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate() }
                .getOrNull() ?: return@mapNotNull null
            DailyCandle(symbol, date, bar.open, bar.close, bar.high, bar.low, bar.volume, "fusion", System.currentTimeMillis())
        }
    }

    override fun isStale(quote: MarketQuote, maximumAgeMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return now - quote.fetchedAt > maximumAgeMillis
    }

    override suspend fun getCachedCandle(symbol: String, date: String): DailyCandle? {
        return null
    }
}
