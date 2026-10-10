package com.ashareai.app.standalone.data.market

import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MarketRepository(
    private val local: LocalRepository,
    private val provider: MarketDataProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val catalogMutex = Mutex()
    private var catalogCache: List<Security> = emptyList()
    private var catalogFetchedAt: Long = 0L

    suspend fun catalog(limit: Int): List<Security> {
        val requested = limit.coerceIn(1, 500)
        val values = catalogMutex.withLock {
            val now = clock()
            if (catalogCache.isEmpty() || now - catalogFetchedAt > CATALOG_CACHE_TTL_MILLIS) {
                catalogCache = runCatching { provider.catalog(500) }
                    .getOrDefault(fallbackCatalog)
                    .distinctBy(Security::symbol)
                    .take(500)
                catalogFetchedAt = now
            }
            catalogCache.ifEmpty { fallbackCatalog }
        }
        return values.take(requested)
    }

    /**
     * Resolves a security by code or Chinese display name. The query is deliberately bounded:
     * it is used by the UI as a suggestion source and must never turn into an unbounded catalog
     * fetch or an unbounded in-memory result set.
     */
    suspend fun searchSecurities(query: String, limit: Int = 12): List<Security> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val cachedMatches = (catalog(500) + fallbackCatalog)
            .asSequence()
            .filter { security ->
                security.symbol.contains(normalized, ignoreCase = true) ||
                    security.name.contains(normalized, ignoreCase = true)
            }
            .distinctBy(Security::symbol)
            .take(limit.coerceIn(1, 24))
            .toList()
        if (cachedMatches.isNotEmpty()) return cachedMatches
        return runCatching { provider.searchSecurities(normalized, limit) }
            .getOrDefault(emptyList())
            .distinctBy(Security::symbol)
            .take(limit.coerceIn(1, 24))
    }

    suspend fun refreshQuotes(symbols: Collection<String>): List<MarketQuote> {
        val requested = symbols.filter(String::isNotBlank).distinct()
        if (requested.isEmpty()) return emptyList()
        val cached = local.cachedQuotes(requested).associateBy { it.symbol }
        return try {
            val fresh = provider.quotes(requested)
            local.cacheQuotes(fresh)
            QuoteCachePolicy.merge(requested, fresh, cached, provider.id, clock())
        } catch (_: Exception) {
            QuoteCachePolicy.merge(requested, emptyList(), cached, provider.id, clock())
        }
    }

    suspend fun refreshQuote(symbol: String): MarketQuote = refreshQuotes(listOf(symbol)).first()

    suspend fun dailyCandles(symbol: String, limit: Int, forceRefresh: Boolean = false): List<DailyCandle> {
        val cached = local.cachedCandles(symbol)
        val cacheFresh = cached.isNotEmpty() && clock() - cached.last().fetchedAt < CANDLE_CACHE_TTL_MILLIS
        if (!forceRefresh && cacheFresh) return cached.takeLast(limit)
        return try {
            provider.dailyCandles(symbol, limit)
                .also { if (it.isNotEmpty()) local.replaceCandles(symbol, it) }
        } catch (_: Exception) {
            cached.takeLast(limit)
        }
    }

    fun isStale(quote: MarketQuote, maximumAgeMillis: Long = QUOTE_STALE_AFTER_MILLIS): Boolean =
        QuoteCachePolicy.isStale(quote, clock(), maximumAgeMillis)

    suspend fun getCachedCandle(symbol: String, date: String): DailyCandle? {
        return local.getCandleByDate(symbol, date)
    }

    private companion object {
        const val QUOTE_STALE_AFTER_MILLIS = 90_000L
        const val CANDLE_CACHE_TTL_MILLIS = 6 * 60 * 60 * 1000L
        const val CATALOG_CACHE_TTL_MILLIS = 30 * 60 * 1000L

        val fallbackCatalog = listOf(
            Security("600519", "贵州茅台", "SH"),
            Security("600036", "招商银行", "SH"),
            Security("601318", "中国平安", "SH"),
            Security("600900", "长江电力", "SH"),
            Security("601857", "中国石油", "SH"),
            Security("600030", "中信证券", "SH"),
            Security("601398", "工商银行", "SH"),
            Security("600276", "恒瑞医药", "SH"),
            Security("000001", "平安银行", "SZ"),
            Security("000333", "美的集团", "SZ"),
            Security("000858", "五粮液", "SZ"),
            Security("002594", "比亚迪", "SZ"),
            Security("300750", "宁德时代", "SZ"),
            Security("300059", "东方财富", "SZ"),
            Security("000651", "格力电器", "SZ"),
            Security("002475", "立讯精密", "SZ"),
            Security("000002", "万科A", "SZ"),
            Security("600000", "浦发银行", "SH"),
            Security("601888", "中国中免", "SH"),
            Security("601012", "隆基绿能", "SH"),
        )
    }
}

object QuoteCachePolicy {
    fun merge(
        requested: List<String>,
        fresh: List<MarketQuote>,
        cached: Map<String, MarketQuote>,
        provider: String,
        now: Long,
    ): List<MarketQuote> {
        val freshBySymbol = fresh.associateBy(MarketQuote::symbol)
        return requested.map { symbol ->
            freshBySymbol[symbol]
                ?: cached[symbol]?.copy(freshness = MarketFreshness.STALE)
                ?: unavailable(symbol, provider, now)
        }
    }

    fun isStale(quote: MarketQuote, now: Long, maximumAgeMillis: Long): Boolean =
        now - quote.fetchedAt > maximumAgeMillis

    private fun unavailable(symbol: String, provider: String, now: Long) = MarketQuote(
        symbol = symbol,
        name = symbol,
        lastPrice = null,
        previousClose = null,
        changePercent = null,
        volume = null,
        provider = provider,
        fetchedAt = now,
        freshness = MarketFreshness.UNAVAILABLE,
    )
}
