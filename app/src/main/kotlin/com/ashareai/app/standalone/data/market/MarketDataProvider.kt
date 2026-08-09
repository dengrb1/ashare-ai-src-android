package com.ashareai.app.standalone.data.market

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security

interface MarketDataProvider {
    val id: String

    suspend fun catalog(limit: Int): List<Security>

    suspend fun quotes(symbols: List<String>): List<MarketQuote>

    suspend fun quote(symbol: String): MarketQuote = quotes(listOf(symbol)).firstOrNull {
        it.symbol == symbol
    } ?: throw MarketDataException("行情源未返回 $symbol")

    suspend fun dailyCandles(symbol: String, limit: Int): List<DailyCandle>
}

class MarketDataException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Adapter seam for an externally hosted AKShare-compatible implementation.
 *
 * The Android app never bundles Python, pandas, or Chaquopy. A future gateway only has to
 * satisfy this Kotlin interface to become a selectable provider.
 */
interface AkShareCompatibleGateway {
    suspend fun listSecurities(limit: Int): List<Security>
    suspend fun listQuotes(symbols: List<String>): List<MarketQuote>
    suspend fun listDailyCandles(symbol: String, limit: Int): List<DailyCandle>
}

class AkShareCompatibleProvider(
    private val gateway: AkShareCompatibleGateway,
) : MarketDataProvider {
    override val id: String = "akshare-compatible"

    override suspend fun catalog(limit: Int): List<Security> = gateway.listSecurities(limit)

    override suspend fun quotes(symbols: List<String>): List<MarketQuote> = gateway.listQuotes(symbols)

    override suspend fun dailyCandles(symbol: String, limit: Int): List<DailyCandle> =
        gateway.listDailyCandles(symbol, limit)
}
