package com.ashareai.app.data

import com.ashareai.app.data.model.*
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService : HealthApi {

    // ---- 确定性决策 / 策略演化 ----
    @POST("api/v1/decision/predict")
    suspend fun predictDecision(@Body body: DecisionPredictRequest): DecisionPredictResponse

    @POST("api/v1/decision/batch")
    suspend fun batchPredictDecision(@Body body: DecisionBatchRequest): DecisionBatchResponse

    @POST("api/v1/strategies/evolution")
    suspend fun strategyEvolution(@Body body: StrategyEvolutionRequest): StrategyEvolutionResponse

    @GET("api/v1/strategies/evolution/active")
    suspend fun activeStrategyVersion(): JsonObject

    @GET("api/v1/strategies/evolution/candidates")
    suspend fun strategyCandidates(
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 50,
    ): StrategyCandidatesResponse

    @POST("api/v1/strategies/evolution/candidates/{candidateId}/approve")
    suspend fun approveStrategy(
        @Path("candidateId") candidateId: String,
        @Body body: StrategyReviewRequest,
    ): JsonObject

    @POST("api/v1/strategies/evolution/candidates/{candidateId}/reject")
    suspend fun rejectStrategy(
        @Path("candidateId") candidateId: String,
        @Body body: StrategyReviewRequest,
    ): JsonObject

    @POST("api/v1/strategies/evolution/rollback")
    suspend fun rollbackStrategy(@Body body: StrategyReviewRequest): JsonObject

    @GET("api/v1/strategies/evolution/schedule")
    suspend fun strategyEvolutionSchedule(): StrategyEvolutionSchedule

    @POST("api/v1/strategies/evolution/schedule")
    suspend fun setStrategyEvolutionSchedule(@Body body: StrategyEvolutionScheduleRequest): StrategyEvolutionSchedule

    // ---- 健康 / 认证 ----
    @GET("api/v1/health")
    override suspend fun health(): HealthResponse

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
    suspend fun saveAssets(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: AssetStateRequest,
    ): AssetState

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

    // ---- 实时持仓监控（手机端） ----
    @GET("api/v1/mobile/monitor/live")
    suspend fun liveMonitor(
        @Query("refresh") refresh: Boolean? = null,
        @Query("include_watchlist") includeWatchlist: Boolean? = null,
    ): LiveMonitorResponse

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

    @GET("api/v1/market/indices")
    suspend fun marketIndices(
        @Query("refresh") refresh: Boolean? = null,
    ): MarketIndicesResponse

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

    @GET("api/v1/notifications/{notificationId}")
    suspend fun notification(@Path("notificationId") notificationId: String): Notification

    @POST("api/v1/devices")
    suspend fun registerDevice(@Body body: PushDeviceRequest): PushDevice

    @DELETE("api/v1/devices/{deviceId}")
    suspend fun unregisterDevice(@Path("deviceId") deviceId: String): Response<Unit>

    @POST("api/v1/devices/{deviceId}/deliveries")
    suspend fun acknowledgeDelivery(
        @Path("deviceId") deviceId: String,
        @Body body: PushDeliveryReceipt,
    ): Response<Unit>

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

    @GET("api/v1/trade-advice-monitors")
    suspend fun tradeAdviceMonitors(): List<TradeAdviceMonitor>

    @PUT("api/v1/trade-advice-monitors")
    suspend fun saveTradeAdviceMonitor(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body body: TradeAdviceMonitorRequest,
    ): TradeAdviceMonitor

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
    suspend fun saveResearchSettings(@Body body: ResearchSettingsRequest): ResearchSettings

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

    // ---- 管理员：模型与运行控制 ----
    @GET("api/v1/admin/model-settings")
    suspend fun modelSettings(): ModelSettings

    @PUT("api/v1/admin/model-settings")
    suspend fun saveModelSettings(@Body body: ModelSettingsDraft): ModelSettings

    @POST("api/v1/admin/model-settings/test")
    suspend fun testModelSettings(@Body body: ModelSettingsDraft): ModelProbeResult

    @POST("api/v1/admin/model-settings/models")
    suspend fun listConfiguredModels(@Body body: ModelSettingsDraft): ModelListResponse

    @GET("api/v1/admin/model-settings/logs")
    suspend fun modelProbeLogs(@Query("limit") limit: Int = 50): List<ModelProbeLog>

    @GET("api/v1/admin/system-settings")
    suspend fun systemSettings(): SystemSettings

    @GET("api/v1/admin/system-resources")
    suspend fun systemResources(): SystemResources

    @POST("api/v1/admin/system-settings/unlock")
    suspend fun unlockSystemSettings(@Body body: SystemSettingsUnlockRequest): SystemSettingsUnlockResponse

    @PUT("api/v1/admin/system-settings")
    suspend fun saveSystemSettings(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-System-Settings-Unlock") unlockToken: String,
        @Body body: JsonObject,
    ): SystemSettings

    @DELETE("api/v1/admin/system-settings/{field}")
    suspend fun restoreSystemSetting(
        @Path("field") field: String,
        @Header("X-System-Settings-Unlock") unlockToken: String,
    ): SystemSettings

    @DELETE("api/v1/admin/system-settings")
    suspend fun restoreAllSystemSettings(
        @Header("X-System-Settings-Unlock") unlockToken: String,
    ): SystemSettings

    @GET("api/v1/admin/runtime-identity")
    suspend fun runtimeIdentity(): RuntimeIdentity

    @PUT("api/v1/admin/runtime-identity")
    suspend fun saveRuntimeIdentity(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-System-Settings-Unlock") unlockToken: String,
        @Body body: RuntimeIdentityRequest,
    ): RuntimeIdentity

    @GET("api/v1/admin/energy-saving")
    suspend fun energySaving(): EnergySaving

    @POST("api/v1/admin/energy-saving/enable")
    suspend fun rearmEnergySaving(): EnergySaving

    @POST("api/v1/admin/energy-saving/disable")
    suspend fun wakeEnergySaving(): EnergySaving

    @GET("api/v1/admin/edge-gateway")
    suspend fun edgeGateway(
        @Header("X-System-Settings-Unlock") unlockToken: String? = null,
    ): EdgeGatewayConfiguration

    @GET("api/v1/admin/edge-gateway/logs")
    suspend fun edgeGatewayLogs(@Query("limit") limit: Int = 200): EdgeGatewayLogs

    @POST("api/v1/admin/edge-gateway/validate")
    suspend fun validateEdgeGateway(@Body body: EdgeGatewayDraft): EdgeGatewayValidation

    @PUT("api/v1/admin/edge-gateway")
    suspend fun saveEdgeGateway(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Header("X-System-Settings-Unlock") unlockToken: String,
        @Body body: EdgeGatewayDraft,
    ): EdgeGatewayConfiguration

    @POST("api/v1/admin/edge-gateway/rollback")
    suspend fun rollbackEdgeGateway(
        @Header("X-System-Settings-Unlock") unlockToken: String,
    ): EdgeGatewayConfiguration

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
        @Part("thread_id") threadId: okhttp3.RequestBody,
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
