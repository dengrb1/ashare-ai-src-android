package com.ashareai.app.standalone.data.market

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Direct Android client for public Eastmoney quote and daily-K-line endpoints.
 *
 * The endpoint is public rather than contractual, so every request is bounded and consumers
 * must accept cache fallback from [MarketRepository].
 */
class EastMoneyMarketDataProvider(
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val clock: () -> Long = System::currentTimeMillis,
    private val limiter: RequestRateLimiter = RequestRateLimiter(350),
) : MarketDataProvider {
    override val id: String = "eastmoney-public"

    override suspend fun catalog(limit: Int): List<Security> {
        val url = "https://82.push2.eastmoney.com/api/qt/clist/get".toHttpUrl().newBuilder()
            .addQueryParameter("pn", "1")
            .addQueryParameter("pz", limit.coerceIn(1, 500).toString())
            .addQueryParameter("po", "1")
            .addQueryParameter("np", "1")
            .addQueryParameter("fltt", "2")
            .addQueryParameter("invt", "2")
            .addQueryParameter("fid", "f3")
            .addQueryParameter("fs", "m:0+t:6,m:0+t:13,m:0+t:80,m:1+t:2,m:1+t:23")
            .addQueryParameter("fields", "f12,f13,f14")
            .build()
        return parseEastMoneyCatalog(get(url), json)
    }

    override suspend fun quotes(symbols: List<String>): List<MarketQuote> {
        if (symbols.isEmpty()) return emptyList()
        return symbols.distinct().chunked(50).flatMap { chunk ->
            val url = "https://push2.eastmoney.com/api/qt/ulist.np/get".toHttpUrl().newBuilder()
                .addQueryParameter("fltt", "2")
                .addQueryParameter("invt", "2")
                .addQueryParameter("fields", "f12,f13,f14,f2,f3,f4,f5,f6,f18")
                .addQueryParameter("secids", chunk.joinToString(",") { toSecId(it) })
                .build()
            val fetchedAt = clock()
            parseEastMoneyQuotes(get(url), id, fetchedAt, json)
        }
    }

    override suspend fun dailyCandles(symbol: String, limit: Int): List<DailyCandle> {
        val fetchedAt = clock()
        val url = "https://push2his.eastmoney.com/api/qt/stock/kline/get".toHttpUrl().newBuilder()
            .addQueryParameter("secid", toSecId(symbol))
            .addQueryParameter("klt", "101")
            .addQueryParameter("fqt", "1")
            .addQueryParameter("lmt", limit.coerceIn(20, 365).toString())
            .addQueryParameter("end", "20500101")
            .addQueryParameter("fields1", "f1,f2,f3,f4,f5,f6")
            .addQueryParameter("fields2", "f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61")
            .build()
        return parseEastMoneyDailyCandles(get(url), symbol, id, fetchedAt, json)
    }

    private suspend fun get(url: HttpUrl): String {
        limiter.acquire()
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "AShareStandalone/2.0 (Android)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw MarketDataException("行情源请求失败：HTTP " + response.code)
                }
                (response.body?.string()).orEmpty().takeIf(String::isNotBlank)
                    ?: throw MarketDataException("行情源返回空数据")
            }
        }
    }

    private fun toSecId(symbol: String): String = when {
        symbol.startsWith("6") -> "1." + symbol
        else -> "0." + symbol
    }
}

class RequestRateLimiter(
    private val minimumIntervalMillis: Long,
) {
    private val mutex = Mutex()
    private var nextAllowedAt: Long = 0

    suspend fun acquire() {
        val waitMillis = mutex.withLock {
            val now = System.currentTimeMillis()
            val wait = (nextAllowedAt - now).coerceAtLeast(0)
            nextAllowedAt = maxOf(now, nextAllowedAt) + minimumIntervalMillis
            wait
        }
        if (waitMillis > 0) delay(waitMillis)
    }
}
