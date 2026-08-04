package com.ashareai.app.data

import com.ashareai.app.data.model.BuyEntryMonitor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

class BuyEntryMonitorSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun decodesMonitorListWithObjectRationaleAndStringDecimals() {
        val payload = Json.parseToJsonElement(
            """[
                {"monitor_id":"monitor-1","symbol":"600519.SH","status":"ACTIVE","effective_date":"2026-08-05","expires_at":"2026-08-05T15:05:00+08:00","entry_low":"1700.000000","entry_high":"1750.000000","score_run_id":"run-1","trade_plan_id":"plan-1","rationale":{"outcome":"BUY","source_plan_id":"plan-1","reference_price":"1725.0"},"triggered_at":null,"error_code":null,"created_at":"2026-08-04T15:05:00Z","updated_at":"2026-08-04T15:05:00Z"},
                {"monitor_id":"monitor-2","symbol":"000001.SZ","status":"TRIGGERED","effective_date":"2026-08-04","expires_at":"2026-08-04T15:05:00+08:00","entry_low":9.5,"entry_high":10.0,"rationale":{},"triggered_at":"2026-08-04T10:30:00Z","error_code":null,"created_at":"2026-08-03T15:05:00Z","updated_at":"2026-08-04T10:30:00Z"}
            ]""",
        )

        val monitors = json.decodeFromJsonElement<List<BuyEntryMonitor>>(payload)

        assertEquals(2, monitors.size)
        val active = monitors[0]
        assertEquals("monitor-1", active.monitor_id)
        assertEquals("600519.SH", active.symbol)
        assertEquals("ACTIVE", active.status)
        assertEquals(1700.0, active.entry_low!!, 0.0001)
        assertEquals(1750.0, active.entry_high!!, 0.0001)
        assertEquals("run-1", active.score_run_id)
        assertEquals("plan-1", active.trade_plan_id)
        assertEquals(JsonPrimitive("BUY"), active.rationale["outcome"])
        assertEquals(null, active.triggered_at)

        val triggered = monitors[1]
        assertEquals("TRIGGERED", triggered.status)
        assertEquals(9.5, triggered.entry_low!!, 0.0001)
        assertEquals("2026-08-04T10:30:00Z", triggered.triggered_at)
    }
}
