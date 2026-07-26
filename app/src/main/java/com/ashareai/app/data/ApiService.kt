package com.ashareai.app.data

import com.ashareai.app.data.model.*
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // ---- 健康 / 认证 ----
    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @POST("api/v1/auth/token")
    suspend fun token(@Body body: LoginRequest): TokenResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequest): TokenResponse

    @POST("api/v1/auth/revoke")
    suspend fun revoke(@Body body: RefreshRequest): Response<Unit>

    @GET("api/v1/auth/me")
    suspend fun me(): UserResponse

    @GET("api/v1/app/bootstrap")
    suspend fun bootstrap(): AppBootstrap

    // ---- 资产 ----
    @GET("api/v1/assets")
    suspend fun assets(): AssetState

    @PUT("api/v1/assets")
    suspend fun saveAssets(@Body body: AssetStateRequest): AssetState

    @PUT("api/v1/assets/exit-monitor")
    suspend fun saveExitMonitor(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ExitMonitorRequest,
    ): AssetState

    @PUT("api/v1/assets/market-refresh")
    suspend fun saveMarketRefresh(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: MarketRefreshRequest,
    ): AssetState

    // ---- 行情 ----
    @GET("api/v1/market/quotes/{symbol}")
    suspend fun quote(
        @Path("symbol") symbol: String,
        @Query("refresh") refresh: Boolean? = null,
    ): Quote

    @GET("api/v1/market/quotes")
    suspend fun quotes(
        @Query("symbols") symbols: String,
        @Query("refresh") refresh: Boolean? = null,
    ): List<Quote>

    @GET("api/v1/market/klines/{symbol}")
    suspend fun klines(
        @Path("symbol") symbol: String,
        @Query("period") period: String = "daily",
        @Query("limit") limit: Int = 250,
        @Query("adjust") adjust: String = "hfq",
        @Query("start") start: String? = null,
        @Query("end") end: String? = null,
        @Query("refresh") refresh: Boolean? = null,
    ): KlineResponse

    @GET("api/v1/market/status")
    suspend fun marketStatus(): MarketStatusResponse

    // ---- 通知 ----
    @GET("api/v1/notifications/summary")
    suspend fun notificationSummary(): NotificationSummary

    @GET("api/v1/notifications")
    suspend fun notifications(
        @Query("limit") limit: Int = 30,
        @Query("cursor") cursor: String? = null,
        @Query("unread_only") unreadOnly: Boolean? = null,
    ): NotificationPage

    @POST("api/v1/notifications/read")
    suspend fun markRead(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: NotificationReadRequest,
    ): Response<Unit>

    @POST("api/v1/notifications/read-all")
    suspend fun markAllRead(@Header("Idempotency-Key") idempotencyKey: String): Response<Unit>

    // ---- 卖出建议 / 买入监控 ----
    @GET("api/v1/exit-advice")
    suspend fun exitAdvice(@Query("limit") limit: Int = 50): List<ExitAdvice>

    @GET("api/v1/exit-advice/{adviceId}")
    suspend fun exitAdviceDetail(@Path("adviceId") adviceId: String): ExitAdvice

    @POST("api/v1/exit-advice/manual")
    suspend fun manualExitAdvice(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ManualExitRequest,
    ): ExitAdvice

    @GET("api/v1/buy-entry-monitors")
    suspend fun buyEntryMonitors(@Query("limit") limit: Int = 100): List<BuyEntryMonitor>

    @PUT("api/v1/buy-entry-monitors")
    suspend fun setBuyEntryMonitor(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: BuyEntryMonitorRequest,
    ): List<BuyEntryMonitor>

    // ---- 研究 ----
    @POST("api/v1/research/runs")
    suspend fun submitResearch(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ResearchRequest,
    ): Run

    @GET("api/v1/research/runs")
    suspend fun researchRuns(
        @Query("limit") limit: Int = 20,
        @Query("trading_date") tradingDate: String? = null,
        @Query("mine") mine: Boolean? = null,
        @Query("published") published: Boolean? = null,
    ): List<Run>

    @GET("api/v1/research/runs/{runId}")
    suspend fun researchRun(@Path("runId") runId: String): Run

    @POST("api/v1/research/runs/{runId}/cancel")
    suspend fun cancelResearch(@Path("runId") runId: String): Run

    @GET("api/v1/research/settings")
    suspend fun researchSettings(): ResearchSettings

    @PUT("api/v1/research/settings")
    suspend fun saveResearchSettings(@Body body: ResearchSettings): ResearchSettings

    // ---- 评分 / 候选 / 组合 / 报告 ----
    @GET("api/v1/scores/{date}/{symbol}")
    suspend fun score(
        @Path("date") date: String,
        @Path("symbol") symbol: String,
        @Query("run_id") runId: String? = null,
    ): Score

    @GET("api/v1/candidates/{date}")
    suspend fun candidates(
        @Path("date") date: String,
        @Query("run_id") runId: String? = null,
    ): List<Candidate>

    @GET("api/v1/portfolios/{date}")
    suspend fun portfolio(
        @Path("date") date: String,
        @Query("run_id") runId: String? = null,
    ): Portfolio

    @GET("api/v1/reports/{date}")
    suspend fun report(
        @Path("date") date: String,
        @Query("run_id") runId: String? = null,
    ): Report

    @GET("api/v1/reports/{reportId}/content")
    suspend fun reportContent(@Path("reportId") reportId: String): ReportContent

    @GET("api/v1/reports/{reportId}/symbols")
    suspend fun reportSymbols(@Path("reportId") reportId: String): List<ReportSymbol>

    @GET("api/v1/reports/{reportId}/execution-status")
    suspend fun executionStatus(@Path("reportId") reportId: String): ExecutionStatus

    @GET("api/v1/reports/{reportId}/trade-plans")
    suspend fun reportTradePlans(@Path("reportId") reportId: String): List<TradePlan>

    @POST("api/v1/reports/{reportId}/trade-plans")
    suspend fun submitTradePlan(
        @Path("reportId") reportId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: TradePlanRequest,
    ): TradePlan

    @GET("api/v1/trade-plans/{planId}")
    suspend fun tradePlan(@Path("planId") planId: String): TradePlan

    // ---- 运行 / 审计 ----
    @GET("api/v1/runs/activity")
    suspend fun runsActivity(
        @Query("cursor") cursor: String? = null,
        @Query("type") type: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 20,
    ): RunActivityPage

    @GET("api/v1/runs/{runId}")
    suspend fun run(@Path("runId") runId: String): Run

    @GET("api/v1/runs/{runId}/audit")
    suspend fun runAudit(@Path("runId") runId: String): List<AuditEvent>

    // ---- 回测 / 快照 ----
    @GET("api/v1/snapshots")
    suspend fun snapshots(@Query("dataset") dataset: String = "backtest_bundle"): List<Snapshot>

    @POST("api/v1/backtests")
    suspend fun submitBacktest(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: BacktestRequest,
    ): Backtest

    @GET("api/v1/backtests")
    suspend fun backtests(@Query("limit") limit: Int = 20): List<Backtest>

    @GET("api/v1/backtests/{backtestId}")
    suspend fun backtest(@Path("backtestId") backtestId: String): Backtest

    @POST("api/v1/backtests/{backtestId}/retry")
    suspend fun retryBacktest(@Path("backtestId") backtestId: String): Backtest

    // ---- AI 对话 ----
    @GET("api/v1/ai/models")
    suspend fun aiModels(): AIModelsResponse

    @GET("api/v1/ai/costs")
    suspend fun aiCosts(
        @Query("days") days: Int = 30,
        @Query("limit") limit: Int = 20,
        @Query("thread_id") threadId: String? = null,
    ): AICostSummary

    @GET("api/v1/ai/chat/thread-index")
    suspend fun aiThreadIndex(
        @Query("limit") limit: Int = 50,
        @Query("cursor") cursor: String? = null,
        @Query("archived") archived: Boolean? = null,
        @Query("q") query: String? = null,
    ): AIChatThreadPage

    @POST("api/v1/ai/chat/threads")
    suspend fun createThread(@Body body: AIChatThreadCreate): AIChatThread

    @PATCH("api/v1/ai/chat/threads/{threadId}")
    suspend fun patchThread(
        @Path("threadId") threadId: String,
        @Body body: AIChatThreadPatch,
    ): AIChatThread

    @DELETE("api/v1/ai/chat/threads/{threadId}")
    suspend fun deleteThread(@Path("threadId") threadId: String): Response<Unit>

    @POST("api/v1/ai/chat/threads:bulk-delete")
    suspend fun bulkDeleteThreads(@Body body: BulkDeleteThreads): Response<Unit>

    @GET("api/v1/ai/chat/threads/{threadId}/messages")
    suspend fun aiMessages(
        @Path("threadId") threadId: String,
        @Query("limit") limit: Int = 100,
    ): List<AIChatMessage>

    @Multipart
    @POST("api/v1/ai/chat/attachments")
    suspend fun uploadAttachments(
        @Part files: List<MultipartBody.Part>,
    ): List<AIChatAttachment>

    // ---- 证券解析 / 搜索 ----
    @GET("api/v1/securities/resolve")
    suspend fun resolveSecurity(@Query("q") query: String): SecurityResolveResponse

    @GET("api/v1/search/financial")
    suspend fun financialSearch(@Query("q") query: String): FinancialSearchResult

    @GET("api/v1/search/status")
    suspend fun searchStatus(): FinancialSearchStatus

    // ---- 个人档案 ----
    @POST("api/v1/me/data-exports")
    suspend fun createExport(@Body body: ArchiveExportRequest): PersonalArchiveJob

    @GET("api/v1/me/data-exports/{exportId}")
    suspend fun exportStatus(@Path("exportId") exportId: String): PersonalArchiveJob

    @GET("api/v1/me/data-exports/{exportId}/download")
    suspend fun downloadExport(@Path("exportId") exportId: String): ResponseBody

    @DELETE("api/v1/me/data-exports/{exportId}")
    suspend fun deleteExport(@Path("exportId") exportId: String): Response<Unit>

    @Multipart
    @POST("api/v1/me/data-imports")
    suspend fun createImport(
        @Part archive: MultipartBody.Part,
        @Part passphrase: MultipartBody.Part,
    ): PersonalArchiveJob

    @GET("api/v1/me/data-imports/{importId}")
    suspend fun importStatus(@Path("importId") importId: String): PersonalArchiveJob

    @POST("api/v1/me/data-imports/{importId}/apply")
    suspend fun applyImport(
        @Path("importId") importId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: ArchiveApplyRequest,
    ): PersonalArchiveJob
}
