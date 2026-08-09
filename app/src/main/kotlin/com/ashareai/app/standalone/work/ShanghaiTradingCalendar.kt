package com.ashareai.app.standalone.work

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ShanghaiTradingCalendar(
    private val marketClosures: Set<LocalDate> = emptySet(),
) {
    fun isTradingDay(date: LocalDate): Boolean =
        date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && date !in marketClosures

    fun isTradingSession(now: ZonedDateTime = ZonedDateTime.now(ZONE)): Boolean {
        if (!isTradingDay(now.toLocalDate())) return false
        val time = now.toLocalTime()
        return time in MORNING_OPEN..<MORNING_CLOSE || time in AFTERNOON_OPEN..<AFTERNOON_CLOSE
    }

    fun nextDailyResearchAt(now: ZonedDateTime = ZonedDateTime.now(ZONE)): ZonedDateTime {
        var date = now.toLocalDate()
        if (!isTradingDay(date) || now.toLocalTime() >= DAILY_RESEARCH_TIME) {
            do {
                date = date.plusDays(1)
            } while (!isTradingDay(date))
        }
        return ZonedDateTime.of(date, DAILY_RESEARCH_TIME, ZONE)
    }

    companion object {
        val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
        val MORNING_OPEN: LocalTime = LocalTime.of(9, 30)
        val MORNING_CLOSE: LocalTime = LocalTime.of(11, 30)
        val AFTERNOON_OPEN: LocalTime = LocalTime.of(13, 0)
        val AFTERNOON_CLOSE: LocalTime = LocalTime.of(15, 0)
        val DAILY_RESEARCH_TIME: LocalTime = LocalTime.of(15, 5)
    }
}
