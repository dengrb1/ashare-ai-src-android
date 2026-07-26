package com.ashareai.app.data

import com.ashareai.app.data.model.KlineBar
import com.ashareai.app.data.model.KlineResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class KlineRepositoryTest {
    @Test
    fun usesBackendPeriodAndKeepsRequestedTradingDays() = runTest {
        val requestedPeriods = mutableListOf<String>()
        val source = KlineSource { _, period, limit, _, _ ->
            requestedPeriods += period
            assertEquals(1_200, limit)
            KlineResponse(
                symbol = "600000.SH",
                period = period,
                bars = listOf(
                    bar("2026-07-21T09:30:00+08:00", 8.0),
                    bar("2026-07-22T09:30:00+08:00", 9.0),
                    bar("2026-07-23T09:30:00+08:00", 10.0),
                    bar("2026-07-24T09:30:00+08:00", 11.0),
                    bar("2026-07-27T09:30:00+08:00", 12.0),
                ),
            )
        }

        val result = KlineRepository(source).load(
            symbol = "600000.SH",
            period = KlinePeriod.MINUTE_60,
            range = KlineRange.DAY_5,
            now = Instant.parse("2026-07-27T08:00:00Z"),
        )

        assertEquals(listOf("60m"), requestedPeriods)
        assertEquals(5, result.bars.size)
        assertEquals(1, result.requestCount)
    }

    @Test
    fun chunksMinuteDataAndDeduplicatesBoundaries() = runTest {
        var call = 0
        val source = KlineSource { _, period, _, start, end ->
            assertEquals("1m", period)
            assertTrue(Instant.parse(start) < Instant.parse(end))
            call += 1
            val bars = if (call == 1) {
                listOf(bar("2026-07-20T09:30:00+08:00", 12.0))
            } else {
                listOf(
                    bar("2026-06-29T09:30:00+08:00", 10.0),
                    bar("2026-07-20T09:30:00+08:00", 13.0),
                )
            }
            KlineResponse("600000.SH", period, bars = bars)
        }

        val result = KlineRepository(source).load(
            symbol = "600000.SH",
            period = KlinePeriod.MINUTE_1,
            range = KlineRange.MONTH_1,
            now = Instant.parse("2026-07-27T08:00:00Z"),
        )

        assertTrue(result.requestCount > 1)
        assertEquals(2, result.bars.size)
        assertEquals(13.0, result.bars.last().close, 0.0)
    }

    @Test
    fun parsesDailyAndOffsetTimestamps() {
        assertEquals(
            Instant.parse("2026-07-26T16:00:00Z"),
            KlineRepository.parseBarInstant("2026-07-27"),
        )
        assertEquals(
            Instant.parse("2026-07-27T01:30:00Z"),
            KlineRepository.parseBarInstant("2026-07-27T09:30:00+08:00"),
        )
    }

    private fun bar(timestamp: String, close: Double) = KlineBar(
        timestamp = timestamp,
        open = close,
        high = close,
        low = close,
        close = close,
    )
}
