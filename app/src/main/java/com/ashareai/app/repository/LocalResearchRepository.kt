package com.ashareai.app.repository

import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.domain.ResearchRequest as LocalResearchRequest
import com.ashareai.app.standalone.domain.ResearchRunState
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.research.ResearchCoordinator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 本地工作区研究仓库实现。
 *
 * 委托给 standalone.LocalRepository，适配统一接口。
 */
class LocalResearchRepository(
    private val local: LocalRepository,
    private val coordinator: ResearchCoordinator? = null,
) : ResearchRepository {

    override suspend fun submitResearch(request: ResearchRequest): String {
        val runner = requireNotNull(coordinator) { "本地研究协调器未配置" }
        val symbols = request.symbols.map(String::trim).filter(String::isNotBlank).distinct()
        val scope = if (symbols.isNotEmpty()) ResearchScope.CUSTOM else ResearchScope.WATCHLIST
        return runner.enqueue(
            LocalResearchRequest(
                scope = scope,
                symbols = symbols,
                marketLimit = symbols.size.coerceAtLeast(1),
                includePortfolioDataForAi = false,
                aiProviderId = null,
            ),
            startImmediately = true,
        ).id
    }

    override suspend fun getRunStatus(runId: String): ResearchRunStatus {
        val run = local.researchRun(runId)
            ?: throw IllegalArgumentException("Run not found: $runId")

        val completed = run.state in setOf(
            ResearchRunState.SUCCEEDED,
            ResearchRunState.FAILED,
            ResearchRunState.CANCELLED,
        )
        return ResearchRunStatus(
            runId = run.id,
            status = run.state.name,
            progress = if (run.totalCount > 0) {
                (run.completedCount * 100 / run.totalCount).coerceIn(0, 100)
            } else if (completed) 100 else 0,
            message = run.errorMessage,
            startedAt = run.startedAt,
            completedAt = if (completed) run.updatedAt else null,
        )
    }

    override suspend fun listRuns(limit: Int): List<ResearchRun> {
        return local.allResearchRuns().take(limit).map { run ->
            ResearchRun(
                runId = run.id,
                date = run.startedAt.toString(),
                status = run.state.name,
                symbolCount = run.totalCount,
                startedAt = run.startedAt,
                completedAt = if (run.state == com.ashareai.app.standalone.domain.ResearchRunState.SUCCEEDED) run.updatedAt else null,
            )
        }
    }

    override suspend fun getReport(date: String, runId: String?): ResearchReport {
        val reports = local.allReports()
        val report = runId?.let { id -> reports.firstOrNull { it.runId == id } }
            ?: reports.firstOrNull { reportDate(it.createdAt) == date }
            ?: reports.firstOrNull()
            ?: throw IllegalArgumentException("本地没有可用研究报告")
        val candidates = local.candidatesForRun(report.runId)
        val body = report.deterministicBody.trim()
        return ResearchReport(
            runId = report.runId,
            date = reportDate(report.createdAt),
            summary = body.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: report.title,
            marketContext = body.substringAfter("## 冻结大盘环境", "").substringBefore("## ").trim(),
            candidateCount = candidates.size,
            generatedAt = report.createdAt,
        )
    }

    override suspend fun getCandidates(date: String, runId: String?): List<ResearchCandidate> {
        val selectedRunId = runId ?: local.allReports()
            .firstOrNull { reportDate(it.createdAt) == date }
            ?.runId
            ?: local.allReports().firstOrNull()?.runId
            ?: return emptyList()
        return local.candidatesForRun(selectedRunId).map {
            ResearchCandidate(
                symbol = it.symbol,
                name = it.name,
                score = it.score,
                reason = it.reason,
                risk = it.risk,
            )
        }
    }

    private fun reportDate(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)
}
