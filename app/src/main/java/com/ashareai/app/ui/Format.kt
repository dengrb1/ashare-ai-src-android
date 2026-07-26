package com.ashareai.app.ui

import java.text.DecimalFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val df2 = DecimalFormat("#,##0.00")
private val df0 = DecimalFormat("#,##0")

fun Double?.fmt2(): String = if (this == null) "--" else df2.format(this)

fun Double?.fmtSigned(): String = when {
    this == null -> "--"
    this > 0 -> "+${df2.format(this)}"
    else -> df2.format(this)
}

fun Double?.fmtPercent(): String = when {
    this == null -> "--"
    this > 0 -> "+${df2.format(this)}%"
    else -> "${df2.format(this)}%"
}

/** 成交额/市值缩写：1.23亿 / 4,567万 */
fun Double?.fmtAmount(): String = when {
    this == null -> "--"
    abs(this) >= 1e8 -> "${df2.format(this / 1e8)}亿"
    abs(this) >= 1e4 -> "${df0.format(this / 1e4)}万"
    else -> df2.format(this)
}

/** 手数：股→手，或大数缩写 */
fun Double?.fmtVolume(): String = when {
    this == null -> "--"
    abs(this) >= 1e8 -> "${df2.format(this / 1e8)}亿"
    abs(this) >= 1e4 -> "${df2.format(this / 1e4)}万"
    else -> df0.format(this)
}

private val shanghaiZone: ZoneId = ZoneId.of("Asia/Shanghai")
private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val dateTimeFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

/** ISO 时间转上海时区显示 */
fun String?.fmtTime(full: Boolean = false): String {
    if (this.isNullOrBlank()) return "--"
    return try {
        val zoned: ZonedDateTime = try {
            Instant.parse(this).atZone(shanghaiZone)
        } catch (_: Exception) {
            java.time.OffsetDateTime.parse(this).atZoneSameInstant(shanghaiZone)
        }
        zoned.format(if (full) dateTimeFmt else timeFmt)
    } catch (_: Exception) {
        // 可能是纯日期
        this.take(16)
    }
}

fun todayTradingDate(): String =
    ZonedDateTime.now(shanghaiZone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

/** 运行状态中文标签 */
fun statusLabel(status: String?): String = when (status?.uppercase()) {
    "PENDING", "QUEUED" -> "排队中"
    "RUNNING", "PROCESSING" -> "执行中"
    "SUCCEEDED", "COMPLETED", "SUCCESS" -> "已完成"
    "FAILED", "ERROR" -> "失败"
    "CANCELLED", "CANCELED" -> "已取消"
    "FUSED" -> "观察模式"
    "DATA_READINESS_WAITING" -> "等待数据"
    "ACTIVE" -> "生效中"
    "TRIGGERED" -> "已触发"
    "EXPIRED" -> "已过期"
    null, "" -> "--"
    else -> status
}

fun isActiveStatus(status: String?): Boolean =
    status?.uppercase() in setOf("PENDING", "QUEUED", "RUNNING", "PROCESSING", "DATA_READINESS_WAITING")
