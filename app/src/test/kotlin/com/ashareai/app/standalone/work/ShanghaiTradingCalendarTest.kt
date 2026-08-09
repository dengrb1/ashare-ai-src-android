package com.ashareai.app.standalone.work

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShanghaiTradingCalendarTest {
    private val calendar = ShanghaiTradingCalendar()
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun stopsOutsideWeekdayTradingSessions() {
        assertFalse(calendar.isTradingDay(LocalDate.of(2026, 8, 8)))
        assertTrue(calendar.isTradingDay(LocalDate.of(2026, 8, 10)))
        assertTrue(calendar.isTradingSession(ZonedDateTime.of(2026, 8, 10, 9, 30, 0, 0, zone)))
        assertFalse(calendar.isTradingSession(ZonedDateTime.of(2026, 8, 10, 11, 30, 0, 0, zone)))
        assertFalse(calendar.isTradingSession(ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, zone)))
    }

    @Test
    fun schedulesAfterCloseOnNextWeekday() {
        val next = calendar.nextDailyResearchAt(ZonedDateTime.of(2026, 8, 7, 16, 0, 0, 0, zone))

        assertEquals(LocalDate.of(2026, 8, 10), next.toLocalDate())
        assertEquals(ShanghaiTradingCalendar.DAILY_RESEARCH_TIME, next.toLocalTime())
    }
}
