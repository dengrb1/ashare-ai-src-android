package com.ashareai.app.repository

import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security

/**
 * 本地工作区行情数据仓库实现。
 *
 * 委托给 standalone.MarketRepository，适配统一接口。
 */
class LocalMarketDataRepository(
    private val market: MarketRepository,
) : MarketDataRepository {

    override suspend fun catalog(limit: Int): List<Security> =
        market.catalog(limit)

    override suspend fun refreshQuotes(symbols: Collection<String>): List<MarketQuote> =
        market.refreshQuotes(symbols)

    override suspend fun refreshQuote(symbol: String): MarketQuote =
        market.refreshQuote(symbol)

    override suspend fun dailyCandles(
        symbol: String,
        limit: Int,
        forceRefresh: Boolean,
    ): List<DailyCandle> =
        market.dailyCandles(symbol, limit, forceRefresh)

    override fun isStale(quote: MarketQuote, maximumAgeMillis: Long): Boolean =
        market.isStale(quote, maximumAgeMillis)

    override suspend fun getCachedCandle(symbol: String, date: String): DailyCandle? =
        market.getCachedCandle(symbol, date)
}
