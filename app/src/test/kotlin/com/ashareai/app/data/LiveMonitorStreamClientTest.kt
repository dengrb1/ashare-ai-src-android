package com.ashareai.app.data

import com.ashareai.app.data.model.LiveMonitorSignal
import com.ashareai.app.data.model.LivePositionMonitor
import com.ashareai.app.data.model.Quote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveMonitorStreamClientTest {
    @Test
    fun parsesStatusEvent() {
        val event = LiveMonitorStreamClient.parse(
            "STATUS",
            """{"state":"OPEN","next_poll_seconds":45,"stale":true,"market_error":"delayed"}""",
        )

        assertEquals(LiveMonitorStreamEvent.Status("OPEN", 45, true, "delayed"), event)
    }

    @Test
    fun parsesPositionAndWatchlistQuotes() {
        val position = LiveMonitorStreamClient.parse("QUOTE", """{"symbol":"600000","quantity":10,"risk_state":"NORMAL"}""")
        val watchlist = LiveMonitorStreamClient.parse("QUOTE", """{"symbol":"000001","price":12.5}""")

        assertTrue(position is LiveMonitorStreamEvent.QuoteUpdate)
        assertEquals(LivePositionMonitor(symbol = "600000", quantity = 10, risk_state = "NORMAL"), (position as LiveMonitorStreamEvent.QuoteUpdate).position)
        assertEquals(Quote(symbol = "000001", price = 12.5), (watchlist as LiveMonitorStreamEvent.QuoteUpdate).watchlist)
    }

    @Test
    fun parsesRiskSignalAndIgnoresUnknownEvents() {
        val risk = LiveMonitorStreamClient.parse("RISK", """{"symbol":"600000","risk_state":"STOP_LOSS"}""")
        val signal = LiveMonitorStreamClient.parse("SIGNAL", """{"symbol":"000001","signal_type":"WATCH","severity":"INFO"}""")

        assertEquals("STOP_LOSS", (risk as LiveMonitorStreamEvent.Risk).position.risk_state)
        assertEquals(LiveMonitorSignal("000001", "WATCH", "INFO"), (signal as LiveMonitorStreamEvent.Signal).signal)
        assertNull(LiveMonitorStreamClient.parse("OTHER", "{}"))
        assertNull(LiveMonitorStreamClient.parse("SIGNAL", "not-json"))
    }

    @Test
    fun statusDefaultsAreCompatibleWithOlderPayloads() {
        val status = LiveMonitorStreamClient.parse("STATUS", """{"state":"IDLE"}""") as LiveMonitorStreamEvent.Status

        assertEquals(30, status.nextPollSeconds)
        assertFalse(status.stale)
        assertNull(status.marketError)
    }
}
