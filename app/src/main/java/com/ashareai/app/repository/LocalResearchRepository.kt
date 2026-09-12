package com.ashareai.app.repository

import com.ashareai.app.standalone.data.LocalRepository

/**
 * 本地工作区研究仓库实现。
 *
 * 委托给 standalone.LocalRepository，适配统一接口。
 */
class LocalResearchRepository(
    private val local: LocalRepository,
) : ResearchRepository {

    override suspend fun submitResearch(request: ResearchRequest): String {
        // 本地研究通过后台服务触发，此接口暂不实现
        // 返回占位 ID
        return "local-${System.currentTimeMillis()}"
    }

    override suspend fun getRunStatus(runId: String): ResearchRunStatus {
        val run = local.researchRun(runId)
            ?: throw IllegalArgumentException("Run not found: $runId")

        return ResearchRunStatus(
            runId = run.id,
            status = run.state.name,
            progress = if (run.state == com.ashareai.app.standalone.domain.ResearchRunState.SUCCEEDED) 100 else 50,
            message = run.errorMessage,
            startedAt = run.startedAt,
            completedAt = if (run.state == com.ashareai.app.standalone.domain.ResearchRunState.SUCCEEDED) run.updatedAt else null,
        )
    }

    override suspend fun listRuns(limit: Int): List<ResearchRun> {
        // 本地工作区使用 recoverableResearchRuns 查询运行历史
        return local.recoverableResearchRuns().take(limit).map { run ->
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
        // 本地工作区使用 ResearchReport entity
        // 暂时不完全实现，返回占位数据
        val actualRunId = runId ?: "unknown"

        return ResearchReport(
            runId = actualRunId,
            date = date,
            summary = "本地研究报告",
            marketContext = "",
            candidateCount = 0,
            generatedAt = System.currentTimeMillis(),
        )
    }

    override suspend fun getCandidates(date: String, runId: String?): List<ResearchCandidate> {
        // 本地工作区通过 getCandidatesByReportId 查询候选
        // 需要根据 runId 关联 reportId
        // 暂时返回空列表
        return emptyList()
    }
}
