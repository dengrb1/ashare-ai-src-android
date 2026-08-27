package com.ashareai.app.repository

import com.ashareai.app.data.KlineRepository
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
) : MarketDataRepository {

    override suspend fun catalog(limit: Int): List<Security> {
        // Fusion 工作区不提供证券目录接口，返回空列表
        // 客户端应使用搜索接口
        return emptyList()
    }

    override suspend fun refreshQuotes(symbols: Collection<String>): List<MarketQuote> {
        // Fusion 工作区通过 K 线接口获取最新行情
        // 需要转换为 MarketQuote 格式
        // 暂时返回空列表，待 API 接口确认后实现
        return emptyList()
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
        // Fusion 工作区通过 KlineRepository 获取 K 线
        // 需要转换为 DailyCandle 格式
        // 暂时返回空列表，待接口转换逻辑实现后补全
        return emptyList()
    }

    override fun isStale(quote: MarketQuote, maximumAgeMillis: Long): Boolean {
        val now = System.currentTimeMillis()
        return now - quote.fetchedAt > maximumAgeMillis
    }

    override suspend fun getCachedCandle(symbol: String, date: String): DailyCandle? {
        // Fusion 工作区不缓存 K 线到本地，返回 null
        return null
    }
}
