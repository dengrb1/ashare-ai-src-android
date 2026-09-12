package com.ashareai.app.standalone.search

import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.domain.MarketQuote
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地金融数据搜索仓库：使用 EastMoney 等公开数据源。
 *
 * 功能：
 * - 证券搜索（代码或名称）
 * - 实时行情（复用 MarketRepository）
 * - 估值指标（市盈率、市净率、市值、总股本）
 * - 财务指标（营收、净利润、ROE、资产负债率）
 * - 标注不可用字段，不推断缺失数据
 */
class LocalFinancialSearchRepository(
    private val market: MarketRepository,
    private val httpClient: OkHttpClient,
) {
    suspend fun search(query: String): FinancialSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) {
            return@withContext FinancialSearchResult(
                securities = emptyList(),
                source = "EastMoney",
                fetchedAt = System.currentTimeMillis(),
            )
        }

        // 简化实现：先从本地目录搜索，实际生产环境应调用 EastMoney API
        val securities = searchFromCatalog(query)

        FinancialSearchResult(
            securities = securities,
            source = "EastMoney",
            fetchedAt = System.currentTimeMillis(),
        )
    }

    private suspend fun searchFromCatalog(query: String): List<SecurityInfo> {
        val catalog = market.catalog(100)
        val matches = catalog.filter {
            it.symbol.contains(query, ignoreCase = true) ||
            it.name.contains(query, ignoreCase = true)
        }.take(10)

        return matches.map { security ->
            // 获取实时行情
            val quote = try {
                market.refreshQuote(security.symbol)
            } catch (e: Exception) {
                null
            }

            // 估值和财务数据：当前简化实现标记为不可用
            // 生产环境应调用 EastMoney F10 数据接口
            SecurityInfo(
                symbol = security.symbol,
                name = security.name,
                exchange = security.exchange,
                quote = quote,
                valuation = null,
                financials = null,
                unavailableFields = listOf(
                    "peRatio", "pbRatio", "marketCap", "totalShares",
                    "revenue", "netProfit", "roe", "debtToAssetRatio", "reportPeriod"
                ),
            )
        }
    }

    suspend fun queryStatus(): SearchEngineStatus {
        return SearchEngineStatus(
            available = true,
            provider = "EastMoney",
            lastError = null,
        )
    }
}
