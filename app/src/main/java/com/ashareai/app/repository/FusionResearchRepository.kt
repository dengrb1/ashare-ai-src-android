package com.ashareai.app.repository

import com.ashareai.app.data.ApiServiceProvider
import com.ashareai.app.data.model.ResearchRequest as FusionResearchRequest
import com.ashareai.app.data.newIdempotencyKey

/**
 * Fusion 工作区研究仓库实现。
 *
 * 使用 Fusion API 提交研究、查询运行状态和获取报告。
 */
class FusionResearchRepository(private val services: ApiServiceProvider) : ResearchRepository {
    private val api get() = services.service()

    override suspend fun submitResearch(request: ResearchRequest): String {
        val run = api.submitResearch(
            newIdempotencyKey(),
            FusionResearchRequest(
                trading_date = request.date ?: java.time.LocalDate.now().toString(),
                scope = if (request.symbols.isEmpty()) "WATCHLIST" else "CUSTOM",
                symbols = request.symbols.takeIf { it.isNotEmpty() },
            ),
        )
        return run.run_id
    }

    override suspend fun getRunStatus(runId: String): ResearchRunStatus {
        val run = api.researchRun(runId)
        return ResearchRunStatus(
            runId = run.run_id,
            status = run.status,
            progress = run.progress ?: 0,
            message = run.error_message ?: run.reason_message,
            startedAt = parseTime(run.started_at ?: run.created_at),
            completedAt = parseTime(run.completed_at),
        )
    }

    override suspend fun listRuns(limit: Int): List<ResearchRun> {
        return api.researchRuns(limit = limit.coerceIn(1, 100), mine = true).map {
            ResearchRun(
                runId = it.run_id,
                date = it.trading_date ?: it.requested_date.orEmpty(),
                status = it.status,
                symbolCount = it.target_symbols.size,
                startedAt = parseTime(it.started_at ?: it.created_at),
                completedAt = parseTime(it.completed_at),
            )
        }
    }

    override suspend fun getReport(date: String, runId: String?): ResearchReport {
        val report = api.report(date, runId)
        report.report_id ?: throw IllegalStateException("report id missing")
        val candidates = api.candidates(date, runId)
        return ResearchReport(
            runId = report.run_id ?: runId.orEmpty(),
            date = report.trading_date ?: date,
            summary = report.result.toString(),
            marketContext = report.market_index_snapshot?.regime.orEmpty(),
            candidateCount = candidates.size,
            generatedAt = parseTime(report.created_at),
        )
    }

    override suspend fun getCandidates(date: String, runId: String?): List<ResearchCandidate> {
        return api.candidates(date, runId).map { candidate ->
            ResearchCandidate(
                symbol = candidate.symbol,
                name = candidate.name.orEmpty(),
                score = candidate.total_score ?: 0.0,
                reason = "${candidate.industry_name.orEmpty()} ${candidate.trading_date.orEmpty()}".trim(),
                risk = when {
                    (candidate.event_risk_multiplier ?: 1.0) < .8 -> "HIGH"
                    (candidate.total_score ?: 0.0) >= 70 -> "LOW"
                    else -> "MEDIUM"
                },
            )
        }
    }

    private fun parseTime(value: String?): Long = value?.let {
        runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull()
            ?: runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull()
    } ?: 0L
}
