package com.ashareai.app.repository

/**
 * Fusion 工作区研究仓库实现。
 *
 * 使用 Fusion API 提交研究、查询运行状态和获取报告。
 */
class FusionResearchRepository : ResearchRepository {

    override suspend fun submitResearch(request: ResearchRequest): String {
        // Fusion 工作区通过 /api/v1/research/submit 提交研究
        // 需要注入 ApiService 后实现
        // 暂时返回占位 ID
        return "fusion-${System.currentTimeMillis()}"
    }

    override suspend fun getRunStatus(runId: String): ResearchRunStatus {
        // Fusion 工作区通过 /api/v1/research/runs/{runId} 查询状态
        // 暂时返回占位状态
        return ResearchRunStatus(
            runId = runId,
            status = "PENDING",
            progress = 0,
            message = null,
            startedAt = System.currentTimeMillis(),
            completedAt = null,
        )
    }

    override suspend fun listRuns(limit: Int): List<ResearchRun> {
        // Fusion 工作区通过 /api/v1/research/runs 列出历史
        // 暂时返回空列表
        return emptyList()
    }

    override suspend fun getReport(date: String, runId: String?): ResearchReport {
        // Fusion 工作区通过 /api/v1/research/reports 获取报告
        // 暂时抛出异常
        throw NotImplementedError("Fusion research report API not yet implemented")
    }

    override suspend fun getCandidates(date: String, runId: String?): List<ResearchCandidate> {
        // Fusion 工作区通过 /api/v1/research/candidates 获取候选
        // 暂时返回空列表
        return emptyList()
    }
}
