package com.ashareai.app.repository

/**
 * 统一研究仓库接口，本地和 Fusion 工作区共享。
 */
interface ResearchRepository {
    /**
     * 提交研究请求。
     *
     * @param request 研究请求参数
     * @return 研究运行 ID
     */
    suspend fun submitResearch(request: ResearchRequest): String

    /**
     * 获取研究运行状态。
     *
     * @param runId 运行 ID
     * @return 运行状态
     */
    suspend fun getRunStatus(runId: String): ResearchRunStatus

    /**
     * 列出研究运行历史。
     *
     * @param limit 返回数量限制
     * @return 运行列表
     */
    suspend fun listRuns(limit: Int): List<ResearchRun>

    /**
     * 获取研究报告。
     *
     * @param date 报告日期（yyyy-MM-dd）
     * @param runId 运行 ID（可选，未指定时返回最新）
     * @return 研究报告
     */
    suspend fun getReport(date: String, runId: String?): ResearchReport

    /**
     * 获取候选池。
     *
     * @param date 日期（yyyy-MM-dd）
     * @param runId 运行 ID（可选，未指定时返回最新）
     * @return 候选列表
     */
    suspend fun getCandidates(date: String, runId: String?): List<ResearchCandidate>
}

/**
 * 研究请求参数。
 */
data class ResearchRequest(
    val symbols: List<String>,
    val date: String?,
    val mode: ResearchMode,
)

enum class ResearchMode {
    FULL,       // 完整研究
    QUICK,      // 快速研究
    BACKTEST,   // 回测模式
}

/**
 * 研究运行状态。
 */
data class ResearchRunStatus(
    val runId: String,
    val status: String,
    val progress: Int,
    val message: String?,
    val startedAt: Long,
    val completedAt: Long?,
)

/**
 * 研究运行记录。
 */
data class ResearchRun(
    val runId: String,
    val date: String,
    val status: String,
    val symbolCount: Int,
    val startedAt: Long,
    val completedAt: Long?,
)

/**
 * 研究报告。
 */
data class ResearchReport(
    val runId: String,
    val date: String,
    val summary: String,
    val marketContext: String,
    val candidateCount: Int,
    val generatedAt: Long,
)

/**
 * 研究候选。
 */
data class ResearchCandidate(
    val symbol: String,
    val name: String,
    val score: Double,
    val reason: String,
    val risk: String,
)
