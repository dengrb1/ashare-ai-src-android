package com.ashareai.app.standalone.data.market

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EastMoneyPayloadParserTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesQuoteAndMissingPriceWithoutInventingValue() {
        val payload = """
            {"data":{"diff":[
              {"f12":"600519","f13":1,"f14":"贵州茅台","f2":1500.25,"f3":1.5,"f5":1234,"f18":1478.0},
              {"f12":"000001","f13":0,"f14":"平安银行","f2":"-","f3":"-","f5":0,"f18":10.0}
            ]}}
        """.trimIndent()

        val quotes = parseEastMoneyQuotes(payload, "eastmoney", 100L, json)

        assertEquals(2, quotes.size)
        assertEquals(1500.25, quotes.first().lastPrice ?: 0.0, 0.001)
        assertEquals("贵州茅台", quotes.first().name)
        assertNull(quotes.last().lastPrice)
    }

    @Test
    fun parsesDailyCandlesAndDropsMalformedRows() {
        val payload = """
            {"data":{"klines":[
              "2026-08-07,10,10.5,10.8,9.9,1000,0,0,0,0,0",
              "bad,row"
            ]}}
        """.trimIndent()

        val candles = parseEastMoneyDailyCandles(payload, "000001", "eastmoney", 200L, json)

        assertEquals(1, candles.size)
        assertEquals(10.5, candles.single().close, 0.001)
    }

    @Test
    fun parsesChineseNameSuggestionsWithExchange() {
        val payload = """
            {"QuotationCodeTable":{"Data":[
              {"Code":"600519","Name":"贵州茅台","MktNum":"1"},
              {"Code":"000001","Name":"平安银行","MktNum":"0"}
            ]}}
        """.trimIndent()

        val suggestions = parseEastMoneySuggestions(payload, json, 8)

        assertEquals(listOf("600519", "000001"), suggestions.map { it.symbol })
        assertEquals("贵州茅台", suggestions.first().name)
        assertEquals("SH", suggestions.first().exchange)
    }
}
