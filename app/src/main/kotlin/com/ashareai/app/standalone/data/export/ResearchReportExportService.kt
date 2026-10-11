package com.ashareai.app.standalone.data.export

import com.ashareai.app.standalone.data.LocalRepository
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class ResearchReportExportService(private val local: LocalRepository) {
    suspend fun json(reportId: String? = null): ByteArray {
        val report = local.allReports().firstOrNull { reportId == null || it.id == reportId }
            ?: error("没有可导出的研究报告")
        val candidates = local.candidatesForRun(report.runId)
        val candidateSymbols = candidates.map { it.symbol }.distinct()
        val events = local.allMonitoringEvents().filter { it.reportId == report.id }
        val quotes = candidateSymbols.take(100).flatMap { local.cachedQuotes(listOf(it)) }
            .associateBy { it.symbol }
        val candles = candidateSymbols.take(30).associateWith { symbol -> local.cachedCandles(symbol).takeLast(100) }
        val payload = buildJsonObject {
            put("schema_version", 1)
            put("engine_version", report.engineVersion)
            putJsonObject("report") {
                put("id", report.id)
                put("run_id", report.runId)
                put("title", report.title)
                put("deterministic_body", report.deterministicBody)
                report.aiExplanation?.let { put("ai_explanation", it) }
                put("created_at", report.createdAt)
                report.signalSummaryJson?.let { put("signal_summary_json", it) }
                put("monitoring_event_count", report.monitoringEventCount)
            }
            putJsonArray("candidates") {
                candidates.forEach { candidate ->
                    add(buildJsonObject {
                        put("symbol", candidate.symbol)
                        put("name", candidate.name)
                        put("score", candidate.score)
                        put("risk", candidate.risk)
                        put("reason", candidate.reason)
                        put("trend_phase", candidate.trendPhase.name)
                        put("trend_signal", candidate.trendSignal.name)
                        put("volume_price_signal", candidate.volumePriceSignal.name)
                        candidate.capitalActivityProxy?.let { put("capital_activity_proxy", it) }
                        put("freshness", candidate.freshness.name)
                    })
                }
            }
            putJsonArray("monitoring_events") {
                events.forEach { event ->
                        add(buildJsonObject {
                            put("id", event.id)
                            put("symbol", event.symbol)
                            put("name", event.name)
                            put("type", event.type.name)
                            put("occurred_at", event.occurredAt)
                            put("severity", event.severity.name)
                            put("payload", event.payload)
                        })
                    }
            }
            putJsonArray("quotes") {
                quotes.values.forEach { quote ->
                        add(buildJsonObject {
                            put("symbol", quote.symbol)
                            put("name", quote.name)
                            quote.lastPrice?.let { put("last_price", it) }
                            quote.previousClose?.let { put("previous_close", it) }
                            quote.changePercent?.let { put("change_percent", it) }
                            quote.volume?.let { put("volume", it) }
                            put("provider", quote.provider)
                            put("fetched_at", quote.fetchedAt)
                            put("freshness", quote.freshness.name)
                        })
                }
            }
            putJsonObject("candles") {
                candles.forEach { (symbol, series) ->
                    putJsonArray(symbol) {
                        series.forEach { candle ->
                            add(buildJsonObject {
                                put("trading_date", candle.tradingDate.toString())
                                put("open", candle.open)
                                put("close", candle.close)
                                put("high", candle.high)
                                put("low", candle.low)
                                candle.volume?.let { put("volume", it) }
                                put("provider", candle.provider)
                                put("fetched_at", candle.fetchedAt)
                            })
                        }
                    }
                }
            }
        }
        return payload.toString().toByteArray(Charsets.UTF_8)
    }

    suspend fun csv(reportId: String? = null): ByteArray {
        val report = local.allReports().firstOrNull { reportId == null || it.id == reportId }
            ?: error("没有可导出的研究报告")
        val candidates = local.candidatesForRun(report.runId)
        return buildString {
            appendLine("report_id,monitoring_event_count,symbol,name,score,risk,reason,trend_phase,trend_signal,volume_price_signal,capital_activity_proxy,freshness")
            candidates.forEach { candidate ->
                listOf(
                    report.id,
                    report.monitoringEventCount.toString(),
                    candidate.symbol,
                    candidate.name,
                    candidate.score.toString(),
                    candidate.risk,
                    candidate.reason,
                    candidate.trendPhase.name,
                    candidate.trendSignal.name,
                    candidate.volumePriceSignal.name,
                    candidate.capitalActivityProxy?.toString().orEmpty(),
                    candidate.freshness.name,
                ).joinTo(this, separator = ",", transform = ::csv)
                appendLine()
            }
        }.toByteArray(Charsets.UTF_8)
    }

    private fun csv(value: String): String = "\"${value.replace("\"", "\"\"").replace("\n", " ")}\""
}
