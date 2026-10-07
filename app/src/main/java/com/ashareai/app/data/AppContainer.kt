package com.ashareai.app.data

import com.ashareai.app.data.model.*
import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody

/** Manual dependency container for the connected Fusion client. */
class AppContainer(
    val settings: SettingsStore,
    val services: ApiServiceProvider = ApiClient,
) {
    val connectionRepository = ConnectionRepository(settings, services)
    val sessionRepository = SessionRepository(settings, services, connectionRepository)
    val marketRepository = MarketRepository(services)
    val monitoringRepository = MonitoringRepository(services)
    val researchRepository = ResearchRepository(services)
    val simulationRepository = SimulationRepository(services)
    val aiRepository = AiRepository(services)
    val notificationRepository = NotificationRepository(services)
    val administrationRepository = AdministrationRepository(services)
    val profileRepository = ProfileRepository(services)
}

data class SessionBootstrap(val user: UserResponse, val assets: AssetState)

class SessionRepository(
    private val settings: SessionSettings,
    private val services: ApiServiceProvider,
    private val connectionRepository: ConnectionRepository? = null,
) {
    suspend fun login(
        username: String,
        password: String,
        rememberPassword: Boolean,
    ): SessionBootstrap {
        ensureSupportedConnection()
        val api = services.service()
        val tokens = api.token(LoginRequest(username, password))
        settings.saveTokens(
            tokens.access_token,
            tokens.refresh_token,
            tokens.expires_in,
            username,
            password,
            rememberPassword,
        )
        return bootstrap(api)
    }

    suspend fun restore(): SessionBootstrap {
        ensureSupportedConnection()
        return try {
            bootstrap(services.service())
        } catch (error: retrofit2.HttpException) {
            if (error.code() == 401 || error.code() == 403) settings.clearTokens()
            throw error
        }
    }

    suspend fun logout() {
        val refreshToken = settings.currentRefreshToken()
        runCatching { refreshToken?.let { services.service().revoke(RefreshRequest(it)) } }
        settings.clearTokens()
    }

    private suspend fun bootstrap(api: ApiService): SessionBootstrap {
        val bootstrap = api.bootstrap()
        return SessionBootstrap(
            user = bootstrap.user ?: api.me(),
            assets = bootstrap.assets ?: api.assets(),
        )
    }

    private suspend fun ensureSupportedConnection() {
        val probe = connectionRepository?.probeConfiguredServer() ?: return
        if (!probe.canEstablishSession) throw ConnectionRejectedException(probe)
    }
}

class MarketRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun assets() = api.assets()
    suspend fun saveAssets(
        request: AssetStateRequest,
        idempotencyKey: String = newIdempotencyKey(),
    ) = api.saveAssets(idempotencyKey, request)
    suspend fun saveExitMonitor(request: ExitMonitorRequest) = api.saveExitMonitor(newIdempotencyKey(), request)
    suspend fun saveMarketRefresh(request: MarketRefreshRequest) = api.saveMarketRefresh(newIdempotencyKey(), request)
    suspend fun quotes(symbols: Collection<String>, refresh: Boolean? = null) =
        api.quotes(symbols.distinct().joinToString(","), refresh)
    suspend fun quote(symbol: String, refresh: Boolean? = null) = api.quote(symbol, refresh)
    suspend fun marketIndices(refresh: Boolean? = null) = api.marketIndices(refresh)
    suspend fun marketStatus() = api.marketStatus()
    suspend fun resolveSecurity(query: String) = api.resolveSecurity(query)
    suspend fun klines(symbol: String, period: String, limit: Int, start: String, end: String) =
        api.klines(symbol, period, limit, start = start, end = end)
    suspend fun financialSearch(query: String) = api.financialSearch(query)
    suspend fun searchStatus() = api.searchStatus()
}

class MonitoringRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun live(
        refresh: Boolean? = null,
        includeWatchlist: Boolean? = null,
    ) = api.liveMonitor(refresh, includeWatchlist)
}

class ResearchRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun runs(limit: Int = 20, mine: Boolean? = true) = api.researchRuns(limit = limit, mine = mine)
    suspend fun run(runId: String) = api.researchRun(runId)
    suspend fun submit(request: ResearchRequest) = api.submitResearch(newIdempotencyKey(), request)
    suspend fun cancel(runId: String) = api.cancelResearch(runId)
    suspend fun settings() = api.researchSettings()
    suspend fun saveSettings(request: ResearchSettingsRequest) = api.saveResearchSettings(request)
    suspend fun candidates(date: String, runId: String? = null) = api.candidates(date, runId)
    suspend fun score(date: String, symbol: String, runId: String? = null) = api.score(date, symbol, runId)
    suspend fun report(date: String, runId: String? = null) = api.report(date, runId)
    suspend fun reportSymbols(reportId: String) = api.reportSymbols(reportId)
    suspend fun activity(cursor: String? = null, type: String? = null, status: String? = null, limit: Int = 20) =
        api.runsActivity(cursor, type, status, limit)
    suspend fun audit(runId: String) = api.runAudit(runId)
    suspend fun predict(request: DecisionPredictRequest) = api.predictDecision(request)
    suspend fun batchPredict(request: DecisionBatchRequest) = api.batchPredictDecision(request)
    suspend fun evolve(request: StrategyEvolutionRequest) = api.strategyEvolution(request)
    suspend fun activeStrategyVersion() = api.activeStrategyVersion()
    suspend fun strategyCandidates(status: String? = null, limit: Int = 50) = api.strategyCandidates(status, limit)
    suspend fun approveStrategy(candidateId: String, note: String? = null) =
        api.approveStrategy(candidateId, StrategyReviewRequest(note))
    suspend fun rejectStrategy(candidateId: String, note: String? = null) =
        api.rejectStrategy(candidateId, StrategyReviewRequest(note))
    suspend fun rollbackStrategy(note: String? = null) = api.rollbackStrategy(StrategyReviewRequest(note))
    suspend fun strategyEvolutionSchedule() = api.strategyEvolutionSchedule()
    suspend fun setStrategyEvolutionSchedule(enabled: Boolean) =
        api.setStrategyEvolutionSchedule(StrategyEvolutionScheduleRequest(enabled))
}

class SimulationRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun portfolio(date: String, runId: String? = null) = api.portfolio(date, runId)
    suspend fun tradePlans(reportId: String) = api.reportTradePlans(reportId)
    suspend fun submitTradePlan(reportId: String, request: TradePlanRequest) =
        api.submitTradePlan(reportId, newIdempotencyKey(), request)
    suspend fun snapshots() = api.snapshots()
    suspend fun backtests(limit: Int = 20) = api.backtests(limit)
    suspend fun submitBacktest(request: BacktestRequest) = api.submitBacktest(newIdempotencyKey(), request)
    suspend fun retryBacktest(id: String) = api.retryBacktest(id)
    suspend fun exitAdvice(limit: Int = 50) = api.exitAdvice(limit)
    suspend fun manualExitAdvice(symbol: String) = api.manualExitAdvice(newIdempotencyKey(), ManualExitRequest(symbol))
    suspend fun buyEntryMonitors(limit: Int = 100) = api.buyEntryMonitors(limit)
    suspend fun saveBuyEntryMonitor(
        request: BuyEntryMonitorRequest,
        idempotencyKey: String = newIdempotencyKey(),
    ) = api.setBuyEntryMonitor(idempotencyKey, request)
    suspend fun tradeAdviceMonitors() = api.tradeAdviceMonitors()
    suspend fun saveTradeAdviceMonitor(
        request: TradeAdviceMonitorRequest,
        idempotencyKey: String = newIdempotencyKey(),
    ) = api.saveTradeAdviceMonitor(idempotencyKey, request)
}

class AiRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun models() = api.aiModels()
    suspend fun costs(days: Int = 30, limit: Int = 20, threadId: String? = null) = api.aiCosts(days, limit, threadId)
    suspend fun threads(limit: Int = 50, cursor: String? = null, archived: Boolean? = null, query: String? = null) =
        api.aiThreadIndex(limit, cursor, archived, query)
    suspend fun createThread(title: String) = api.createThread(AIChatThreadCreate(title))
    suspend fun patchThread(id: String, patch: AIChatThreadPatch) = api.patchThread(id, patch)
    suspend fun deleteThread(id: String) = api.deleteThread(id)
    suspend fun bulkDeleteThreads(ids: List<String>) = api.bulkDeleteThreads(BulkDeleteThreads(ids))
    suspend fun messages(id: String, limit: Int = 100) = api.aiMessages(id, limit)
    suspend fun uploadAttachments(files: List<MultipartBody.Part>, threadId: RequestBody) = api.uploadAttachments(files, threadId)
}

class NotificationRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun summary() = api.notificationSummary()
    suspend fun notifications(limit: Int = 30, cursor: String? = null, unreadOnly: Boolean? = null) =
        api.notifications(limit, cursor, unreadOnly)
    suspend fun markRead(ids: List<String>) = api.markRead(newIdempotencyKey(), NotificationReadRequest(ids))
    suspend fun markAllRead() = api.markAllRead(newIdempotencyKey())
    suspend fun notification(id: String) = api.notification(id)
    suspend fun registerDevice(request: PushDeviceRequest) = api.registerDevice(request)
    suspend fun unregisterDevice(id: String) = api.unregisterDevice(id)
    suspend fun acknowledgeDelivery(id: String, request: PushDeliveryReceipt) = api.acknowledgeDelivery(id, request)
}

class ProfileRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun createExport(passphrase: String) = api.createExport(ArchiveExportRequest(passphrase))
    suspend fun exportStatus(id: String) = api.exportStatus(id)
    suspend fun downloadExport(id: String): ResponseBody = api.downloadExport(id)
    suspend fun deleteExport(id: String) = api.deleteExport(id)
    suspend fun createImport(payload: ByteArray, passphrase: String): PersonalArchiveJob {
        val archiveBody = payload.toRequestBody("application/octet-stream".toMediaType())
        val archivePart = MultipartBody.Part.createFormData("archive", "personal-profile.ashare", archiveBody)
        val passphrasePart = MultipartBody.Part.createFormData("passphrase", passphrase)
        return api.createImport(archivePart, passphrasePart)
    }
    suspend fun importStatus(id: String) = api.importStatus(id)
    suspend fun applyImport(
        id: String,
        mergeOptions: JsonObject? = null,
        idempotencyKey: String = newIdempotencyKey(),
    ) = api.applyImport(id, idempotencyKey, ArchiveApplyRequest(mergeOptions))
}

class AdministrationRepository(private val services: ApiServiceProvider) {
    private val api get() = services.service()

    suspend fun modelSettings() = api.modelSettings()
    suspend fun saveModelSettings(request: ModelSettingsDraft) = api.saveModelSettings(request)
    suspend fun testModelSettings(request: ModelSettingsDraft) = api.testModelSettings(request)
    suspend fun listConfiguredModels(request: ModelSettingsDraft) = api.listConfiguredModels(request)
    suspend fun modelProbeLogs(limit: Int = 50) = api.modelProbeLogs(limit)
    suspend fun systemSettings() = api.systemSettings()
    suspend fun systemResources() = api.systemResources()
    suspend fun unlockSystemSettings(request: SystemSettingsUnlockRequest) = api.unlockSystemSettings(request)
    suspend fun saveSystemSettings(token: String, body: JsonObject) =
        api.saveSystemSettings(newIdempotencyKey(), token, body)
    suspend fun restoreSystemSetting(field: String, token: String) = api.restoreSystemSetting(field, token)
    suspend fun restoreAllSystemSettings(token: String) = api.restoreAllSystemSettings(token)
    suspend fun runtimeIdentity() = api.runtimeIdentity()
    suspend fun saveRuntimeIdentity(token: String, request: RuntimeIdentityRequest) =
        api.saveRuntimeIdentity(newIdempotencyKey(), token, request)
    suspend fun energySaving() = api.energySaving()
    suspend fun rearmEnergySaving() = api.rearmEnergySaving()
    suspend fun wakeEnergySaving() = api.wakeEnergySaving()
    suspend fun edgeGateway(token: String? = null) = api.edgeGateway(token)
    suspend fun edgeGatewayLogs(limit: Int = 200) = api.edgeGatewayLogs(limit)
    suspend fun validateEdgeGateway(request: EdgeGatewayDraft) = api.validateEdgeGateway(request)
    suspend fun saveEdgeGateway(token: String, request: EdgeGatewayDraft) =
        api.saveEdgeGateway(newIdempotencyKey(), token, request)
    suspend fun rollbackEdgeGateway(token: String) = api.rollbackEdgeGateway(token)
}

/** Naming aliases for callers that use acronym-style repository names. */
typealias AIRepository = AiRepository
typealias AdminRepository = AdministrationRepository
