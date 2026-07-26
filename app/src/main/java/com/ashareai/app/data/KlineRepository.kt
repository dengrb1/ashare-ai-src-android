package com.ashareai.app.data

import com.ashareai.app.data.model.KlineBar
import com.ashareai.app.data.model.KlineResponse
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

enum class KlinePeriod(val apiValue: String, val label: String, val chunkDays: Long?) {
    MINUTE_1("1m", "1分", 7),
    MINUTE_5("5m", "5分", 30),
    MINUTE_15("15m", "15分", 90),
    MINUTE_30("30m", "30分", 180),
    MINUTE_60("60m", "60分", 365),
    DAY("day", "日线", null),
}

enum class KlineRange(val label: String, val months: Long? = null, val tradingDays: Int? = null) {
    DAY_1("1日", tradingDays = 1),
    DAY_5("5日", tradingDays = 5),
    MONTH_1("1月", months = 1),
    MONTH_3("3月", months = 3),
    MONTH_6("6月", months = 6),
    YEAR_1("1年", months = 12),
}

data class KlineLoadResult(
    val bars: List<KlineBar>,
    val requestCount: Int,
)

private data class KlineWindow(val start: Instant, val end: Instant)

fun interface KlineSource {
    suspend fun load(
        symbol: String,
        period: String,
        limit: Int,
        start: String,
        end: String,
    ): KlineResponse
}

class KlineRepository(private val source: KlineSource) {
    constructor(api: ApiService) : this(
        KlineSource { symbol, period, limit, start, end ->
            api.klines(symbol, period = period, limit = limit, start = start, end = end)
        },
    )

    suspend fun load(
        symbol: String,
        period: KlinePeriod,
        range: KlineRange,
        now: Instant = Instant.now(),
        onProgress: (requestCount: Int) -> Unit = {},
    ): KlineLoadResult {
        val zone = SHANGHAI_ZONE
        val end = now
        val localEnd = now.atZone(zone)
        val start = when (range) {
            KlineRange.DAY_1 -> localEnd.minusDays(14)
            KlineRange.DAY_5 -> localEnd.minusDays(30)
            else -> localEnd.minusMonths(requireNotNull(range.months))
        }.toInstant()

        val merged = linkedMapOf<Instant, KlineBar>()
        var before: Instant? = null
        var requestCount = 0

        while (requestCount < MAX_CHUNKS) {
            val window = nextWindow(period, start, end, before) ?: break
            val response = source.load(symbol, period.apiValue, BARS_PER_REQUEST, window.start.toString(), window.end.toString())
            requestCount += 1
            onProgress(requestCount)
            response.bars.forEach { bar -> parseBarInstant(bar.timestamp)?.let { merged[it] = bar } }

            val ordered = merged.toSortedMap()
            if (hasCoverage(ordered.keys.toList(), start, range, zone)) break
            if (window.start <= start || period.chunkDays == null) break
            before = window.start
        }

        val ordered = merged.toSortedMap()
            .filterKeys { it in start..end }
        val selectedKeys = if (range.tradingDays != null) {
            val days = ordered.keys.map { it.atZone(zone).toLocalDate() }.distinct().takeLast(range.tradingDays)
            val selectedDays = days.toSet()
            ordered.keys.filter { it.atZone(zone).toLocalDate() in selectedDays }.toSet()
        } else {
            ordered.keys
        }
        return KlineLoadResult(
            bars = ordered.filterKeys { it in selectedKeys }.values.toList(),
            requestCount = requestCount,
        )
    }

    private fun nextWindow(
        period: KlinePeriod,
        planStart: Instant,
        planEnd: Instant,
        before: Instant?,
    ): KlineWindow? {
        val anchor = before ?: planEnd
        if (anchor <= planStart) return null
        val start = period.chunkDays
            ?.let { anchor.minus(it, ChronoUnit.DAYS).coerceAtLeast(planStart) }
            ?: planStart
        return KlineWindow(start, anchor)
    }

    private fun hasCoverage(
        timestamps: List<Instant>,
        planStart: Instant,
        range: KlineRange,
        zone: ZoneId,
    ): Boolean {
        range.tradingDays?.let { required ->
            return timestamps.map { it.atZone(zone).toLocalDate() }.distinct().size >= required
        }
        val first = timestamps.firstOrNull() ?: return false
        return first <= planStart.plus(14, ChronoUnit.DAYS)
    }

    companion object {
        private val SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai")
        private const val BARS_PER_REQUEST = 1_200
        private const val MAX_CHUNKS = 64

        internal fun parseBarInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(value).atZone(SHANGHAI_ZONE).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay(SHANGHAI_ZONE).toInstant() }.getOrNull()
    }
}

private fun Instant.coerceAtLeast(minimum: Instant): Instant = if (this < minimum) minimum else this
