package com.ashareai.app.standalone.data.market

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.Security
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

internal fun parseEastMoneySuggestions(payload: String, json: Json, limit: Int): List<Security> {
    val data = json.parseToJsonElement(payload).jsonObject["QuotationCodeTable"]
        ?.jsonObject?.get("Data") as? JsonArray ?: return emptyList()
    return data.mapNotNull { value ->
        val item = value as? JsonObject ?: return@mapNotNull null
        val symbol = item.string("Code")?.takeIf { it.length == 6 && it.all(Char::isDigit) }
            ?: return@mapNotNull null
        val name = item.string("Name") ?: symbol
        val exchange = if (item.string("MktNum") == "1") "SH" else "SZ"
        Security(symbol, name, exchange)
    }.distinctBy(Security::symbol).take(limit.coerceIn(1, 24))
}

internal fun parseEastMoneyCatalog(payload: String, json: Json): List<Security> =
    eastMoneyDiff(payload, json).mapNotNull { item ->
        val symbol = item.string("f12") ?: return@mapNotNull null
        val name = item.string("f14") ?: symbol
        Security(symbol, name, if (item.string("f13") == "1") "SH" else "SZ")
    }

internal fun parseEastMoneyQuotes(
    payload: String,
    provider: String,
    fetchedAt: Long,
    json: Json,
): List<MarketQuote> = eastMoneyDiff(payload, json).mapNotNull { item ->
    val symbol = item.string("f12") ?: return@mapNotNull null
    MarketQuote(
        symbol = symbol,
        name = item.string("f14") ?: symbol,
        lastPrice = item.number("f2"),
        previousClose = item.number("f18"),
        changePercent = item.number("f3"),
        volume = item.number("f5"),
        provider = provider,
        fetchedAt = fetchedAt,
        freshness = MarketFreshness.FRESH,
    )
}

internal fun parseEastMoneyDailyCandles(
    payload: String,
    symbol: String,
    provider: String,
    fetchedAt: Long,
    json: Json,
): List<DailyCandle> {
    val root = json.parseToJsonElement(payload).jsonObject
    val values = root["data"]?.jsonObject?.get("klines") as? JsonArray ?: emptyList()
    return values.mapNotNull { value ->
        val parts = (value as? JsonPrimitive)?.contentOrNull?.split(",") ?: return@mapNotNull null
        if (parts.size < 6) return@mapNotNull null
        val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
        val open = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val close = parts[2].toDoubleOrNull() ?: return@mapNotNull null
        val high = parts[3].toDoubleOrNull() ?: return@mapNotNull null
        val low = parts[4].toDoubleOrNull() ?: return@mapNotNull null
        DailyCandle(
            symbol = symbol,
            tradingDate = date,
            open = open,
            close = close,
            high = high,
            low = low,
            volume = parts[5].toDoubleOrNull(),
            provider = provider,
            fetchedAt = fetchedAt,
        )
    }
}

private fun eastMoneyDiff(payload: String, json: Json): List<JsonObject> {
    val root = json.parseToJsonElement(payload).jsonObject
    val diff = root["data"]?.jsonObject?.get("diff")
    return when (diff) {
        is JsonArray -> diff.mapNotNull { it as? JsonObject }
        is JsonObject -> diff.values.mapNotNull { it as? JsonObject }
        else -> emptyList()
    }
}

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "-" }

private fun JsonObject.number(key: String): Double? =
    string(key)?.toDoubleOrNull()?.takeIf { it.isFinite() && kotlin.math.abs(it) < 1.0e12 }
