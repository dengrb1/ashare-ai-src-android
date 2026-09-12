package com.ashareai.app.standalone.search

import com.ashareai.app.standalone.domain.MarketQuote

/**
 * 本地金融搜索：使用 EastMoney 等公开数据源解析证券、行情、K 线、估值和财务指标。
 * 每条结果保留来源、抓取时间、新鲜度和不可用原因，不推断缺失数据。
 */
data class FinancialSearchResult(
    val securities: List<SecurityInfo>,
    val source: String,
    val fetchedAt: Long,
)

data class SecurityInfo(
    val symbol: String,
    val name: String,
    val exchange: String,
    val quote: MarketQuote?,
    val valuation: Valuation?,
    val financials: Financials?,
    val unavailableFields: List<String>,  // 数据源无法提供的字段
)

data class Valuation(
    val peRatio: Double?,
    val pbRatio: Double?,
    val marketCap: Double?,
    val totalShares: Double?,
)

data class Financials(
    val revenue: Double?,
    val netProfit: Double?,
    val roe: Double?,
    val debtToAssetRatio: Double?,
    val reportPeriod: String?,
)

data class SearchEngineStatus(
    val available: Boolean,
    val provider: String,
    val lastError: String?,
)
