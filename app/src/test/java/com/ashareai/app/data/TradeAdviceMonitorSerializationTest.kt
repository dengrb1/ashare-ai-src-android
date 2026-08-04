package com.ashareai.app.data

import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.data.model.TradeAdviceMonitorRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TradeAdviceMonitorSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun decodesMonitorListWithDynamicRationale() {
        val payload = Json.parseToJsonElement(
            """[{"monitor_id":"monitor-1","symbol":"600900.SH","enabled":true,"rationale":{"summary":"monitoring"}}]""",
        )

        val monitors = json.decodeFromJsonElement<List<TradeAdviceMonitor>>(payload)

        assertEquals(1, monitors.size)
        assertEquals("monitor-1", monitors.single().monitor_id)
        assertEquals(JsonPrimitive("monitoring"), monitors.single().rationale["summary"])
    }

    @Test
    fun encodesMonitorRequest() {
        val request = TradeAdviceMonitorRequest(
            symbol = "600900.SH",
            enabled = true,
            manual_buy_price = 10.5,
            manual_sell_price = 11.25,
        )

        val encoded = json.encodeToJsonElement(TradeAdviceMonitorRequest.serializer(), request)

        assertTrue(encoded is JsonObject)
        val encodedObject = encoded as JsonObject
        assertEquals(JsonPrimitive("600900.SH"), encodedObject["symbol"])
        assertEquals(JsonPrimitive(true), encodedObject["enabled"])
        assertEquals(JsonPrimitive(10.5), encodedObject["manual_buy_price"])
        assertEquals(JsonPrimitive(11.25), encodedObject["manual_sell_price"])
    }
}
