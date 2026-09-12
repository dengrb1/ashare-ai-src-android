package com.ashareai.app.repository

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security

/**
 * 统一行情数据仓库接口，本地和 Fusion 工作区共享。
 */
interface MarketDataRepository {
    /**
     * 获取证券目录。
     *
     * @param limit 返回数量限制
     * @return 证券列表
     */
    suspend fun catalog(limit: Int): List<Security>

    /**
     * 刷新实时行情。
     *
     * @param symbols 证券代码列表
     * @return 行情列表
     */
    suspend fun refreshQuotes(symbols: Collection<String>): List<MarketQuote>

    /**
     * 刷新单个证券行情。
     *
     * @param symbol 证券代码
     * @return 行情
     */
    suspend fun refreshQuote(symbol: String): MarketQuote

    /**
     * 获取日线 K 线。
     *
     * @param symbol 证券代码
     * @param limit 返回数量
     * @param forceRefresh 是否强制刷新缓存
     * @return K 线列表
     */
    suspend fun dailyCandles(
        symbol: String,
        limit: Int,
        forceRefresh: Boolean = false,
    ): List<DailyCandle>

    /**
     * 检查行情是否过期。
     *
     * @param quote 行情
     * @param maximumAgeMillis 最大年龄（毫秒）
     * @return 是否过期
     */
    fun isStale(quote: MarketQuote, maximumAgeMillis: Long): Boolean

    /**
     * 获取缓存的某日 K 线。
     *
     * @param symbol 证券代码
     * @param date 日期（yyyy-MM-dd）
     * @return K 线或 null
     */
    suspend fun getCachedCandle(symbol: String, date: String): DailyCandle?
}
