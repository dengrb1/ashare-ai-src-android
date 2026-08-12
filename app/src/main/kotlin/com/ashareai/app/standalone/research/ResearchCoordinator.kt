package com.ashareai.app.standalone.research

import android.content.Context
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.ai.AiRequest
import com.ashareai.app.standalone.data.ai.AiStreamEvent
import com.ashareai.app.standalone.data.ai.OpenAiCompatibleClient
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.domain.ResearchCandidate
import com.ashareai.app.standalone.domain.ResearchReport
import com.ashareai.app.standalone.domain.ResearchRequest
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.ResearchRun
import com.ashareai.app.standalone.domain.ResearchRunState
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.domain.SimulationPortfolio
import com.ashareai.app.standalone.notifications.NotificationRepository
import com.ashareai.app.standalone.work.ResearchFallbackWorker
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ResearchCoordinator(
    private val context: Context,
    private val local: LocalRepository,
    private val market: MarketRepository,
    private val engine: ResearchEngine,
    private val aiClient: OpenAiCompatibleClient,
    private val notifications: NotificationRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = Json,
) {
    suspend fun enqueue(request: ResearchRequest): ResearchRun {
        val symbols = resolveSymbols(request)
        require(symbols.isNotEmpty()) { "没有可研究的股票" }
        val now = clock()
        val run = ResearchRun(
            id = UUID.randomUUID().toString(),
            scope = request.scope,
            symbols = symbols,
            state = ResearchRunState.QUEUED,
            totalCount = symbols.size,
            completedCount = 0,
            startedAt = now,
            updatedAt = now,
            cancellationRequested = false,
            errorMessage = null,
            includePortfolioDataForAi = request.includePortfolioDataForAi,
            aiProviderId = request.aiProviderId,
        )
        local.createResearchRun(run)
        ResearchService.start(context, run.id)
        return run
    }

    suspend fun enqueueDaily(): ResearchRun? {
        val holdings = local.holdingsNow()
        val watchlist = local.watchlistNow()
        val scope = if (holdings.isNotEmpty()) ResearchScope.HOLDINGS else ResearchScope.WATCHLIST
        val symbols = if (scope == ResearchScope.HOLDINGS) {
            holdings.map { it.symbol }
        } else {
            watchlist.map { it.symbol }
        }
        if (symbols.isEmpty()) return null
        return enqueue(
            ResearchRequest(
                scope = scope,
                symbols = symbols,
                marketLimit = symbols.size,
                includePortfolioDataForAi = false,
                aiProviderId = null,
            ),
        )
    }

    suspend fun cancel(runId: String) {
        local.requestResearchCancellation(runId, clock())
    }

    suspend fun recoverPendingRuns() {
        local.recoverableResearchRuns().forEach { ResearchFallbackWorker.enqueue(context, it.id) }
    }

    suspend fun run(
        runId: String,
        onProgress: suspend (ResearchRun) -> Unit = {},
    ) {
        val original = local.researchRun(runId) ?: return
        if (original.cancellationRequested) {
            finishCancelled(original)
            return
        }
        local.updateResearchRun(runId, ResearchRunState.RUNNING, original.completedCount, now = clock())
        val analysed = mutableListOf<AnalysedSecurity>()
        try {
            // Freeze one shared market environment before stock analysis. It is passed to every
            // result and persisted in the report rather than refreshed while a report is viewed.
            val marketContext = MarketIndexContextAnalyzer.analyze(
                MarketIndexContextAnalyzer.specs.associate { spec ->
                    spec.symbol to market.dailyCandles(spec.symbol, limit = 100)
                },
            )
            val batches = ResearchBatchPlanner.batches(original.symbols)
            var completed = original.completedCount
            batches.forEach { batch ->
                if (isCancellationRequested(runId)) {
                    finishCancelled(local.researchRun(runId) ?: original)
                    return
                }
                val quotes = market.refreshQuotes(batch).associateBy { it.symbol }
                batch.forEach { symbol ->
                    if (isCancellationRequested(runId)) {
                        finishCancelled(local.researchRun(runId) ?: original)
                        return
                    }
                    val candles = market.dailyCandles(symbol, limit = 100)
                    val quote = quotes[symbol]
                    val result = engine.analyse(
                        symbol = symbol,
                        name = quote?.name ?: symbol,
                        quote = quote,
                        candles = candles,
                        marketContext = marketContext,
                    )
                    analysed += AnalysedSecurity(result, candles)
                    completed += 1
                    local.updateResearchRun(runId, ResearchRunState.RUNNING, completed, now = clock())
                    val progressRun = local.researchRun(runId)
                    if (progressRun != null) onProgress(progressRun)
                }
            }

            val ranked = analysed.sortedByDescending { it.result.score.total }
            saveDeterministicOutputs(original, ranked)
            val aiExplanation = requestAiExplanation(original, ranked, marketContext)
            val report = ResearchReport(
                id = UUID.randomUUID().toString(),
                runId = original.id,
                title = "本地研究报告",
                deterministicBody = reportBody(original, ranked, marketContext),
                aiExplanation = aiExplanation,
                createdAt = clock(),
            )
            local.saveReport(report)
            local.updateResearchRun(
                id = original.id,
                state = ResearchRunState.SUCCEEDED,
                completedCount = completed,
                now = clock(),
            )
            notifications.publish(
                title = "本地研究已完成",
                body = "已完成 " + completed + " 只股票的确定性研究",
                priority = com.ashareai.app.standalone.domain.NotificationPriority.PROGRESS,
                deepLink = "reports",
            )
        } catch (error: Exception) {
            local.updateResearchRun(
                id = original.id,
                state = ResearchRunState.FAILED,
                completedCount = local.researchRun(original.id)?.completedCount ?: 0,
                errorMessage = error.message ?: "本地研究失败",
                now = clock(),
            )
            notifications.publish(
                title = "本地研究失败",
                body = error.message ?: "请检查网络或行情源",
                priority = com.ashareai.app.standalone.domain.NotificationPriority.WARNING,
                deepLink = "research",
            )
        } finally {
            analysed.clear()
        }
    }

    private suspend fun resolveSymbols(request: ResearchRequest): List<String> = when (request.scope) {
        ResearchScope.HOLDINGS -> local.holdingsNow().map { it.symbol }
        ResearchScope.WATCHLIST -> local.watchlistNow().map { it.symbol }
        ResearchScope.CUSTOM -> request.symbols
        ResearchScope.MARKET -> market.catalog(
            ResearchBatchPlanner.clampMarketLimit(request.marketLimit),
        ).map { it.symbol }
    }.map(String::trim).filter(String::isNotBlank).distinct()

    private suspend fun isCancellationRequested(runId: String): Boolean =
        local.researchRun(runId)?.cancellationRequested == true

    private suspend fun finishCancelled(run: ResearchRun) {
        local.updateResearchRun(
            id = run.id,
            state = ResearchRunState.CANCELLED,
            completedCount = run.completedCount,
            now = clock(),
        )
    }

    private suspend fun saveDeterministicOutputs(
        run: ResearchRun,
        ranked: List<AnalysedSecurity>,
    ) {
        val candidates = ranked.take(30).mapIndexed { index, item ->
            ResearchCandidate(
                id = run.id + ":" + item.result.symbol,
                runId = run.id,
                symbol = item.result.symbol,
                name = item.result.name,
                score = item.result.score.total,
                risk = item.result.risk,
                reason = (index + 1).toString() + " 名：" + item.result.summary,
                createdAt = clock(),
            )
        }
        local.replaceCandidates(run.id, candidates)
        val portfolioItems = candidates.take(10).map {
            mapOf(
                "symbol" to it.symbol,
                "name" to it.name,
                "score" to it.score.toString(),
                "weight" to (100.0 / minOf(10, candidates.size)).toString(),
            )
        }
        if (portfolioItems.isNotEmpty()) {
            local.saveSimulationPortfolio(
                SimulationPortfolio(
                    id = UUID.randomUUID().toString(),
                    runId = run.id,
                    name = "本地模拟组合",
                    holdingsJson = json.encodeToString(portfolioItems),
                    score = candidates.take(10).map(ResearchCandidate::score).average(),
                    createdAt = clock(),
                ),
            )
        }
    }

    private suspend fun requestAiExplanation(
        run: ResearchRun,
        ranked: List<AnalysedSecurity>,
        marketContext: MarketIndexContext,
    ): String? {
        val providerId = run.aiProviderId ?: return null
        val output = StringBuilder()
        var failure: String? = null
        val context = buildString {
            append("请为以下本地确定性研究写简洁解释。不得修改评分、风险或交易门槛；不可用数据必须保持不可用。\n")
            append(marketContext.reportSummary())
            append("\n")
            ranked.take(10).forEach { item ->
                append(item.result.summary)
                append("\n近20日收盘：")
                append(item.candles.takeLast(20).joinToString(",") { it.close.toString() })
                append("\n")
            }
            if (run.includePortfolioDataForAi) {
                append("用户已授权本次研究使用持仓信息，但本次自动研究未附带成本与数量。\n")
            }
        }
        aiClient.stream(
            AiRequest(
                providerId = providerId,
                systemInstruction = "你是 A 股本地研究的解释助手。只解释已有确定性数据。",
                prompt = context,
            ),
        ).collect { event ->
            when (event) {
                is AiStreamEvent.Delta -> output.append(event.text)
                is AiStreamEvent.Failed -> failure = event.message
                else -> Unit
            }
        }
        return output.toString().takeIf(String::isNotBlank)
            ?: failure?.let { "AI 解释未生成：" + it }
    }

    private fun reportBody(
        run: ResearchRun,
        ranked: List<AnalysedSecurity>,
        marketContext: MarketIndexContext,
    ): String = buildString {
        append("范围：")
        append(run.scope.name)
        append("；样本：")
        append(run.totalCount)
        append("；评分完全由本地规则生成。\n\n")
        append(marketContext.reportSummary())
        append("\n\n")
        ranked.take(30).forEachIndexed { index, item ->
            append(index + 1)
            append(". ")
            append(item.result.summary)
            append("\n")
        }
    }

    private data class AnalysedSecurity(
        val result: ResearchResult,
        val candles: List<com.ashareai.app.standalone.domain.DailyCandle>,
    )
}

private fun MarketIndexContext.reportSummary(): String = buildString {
    val marketIndices = this@reportSummary.indices
    append("冻结大盘指数环境：")
    append(
        when (regime) {
            MarketRegime.RISK_ON -> "风险偏好改善"
            MarketRegime.RISK_OFF -> "风险偏好收缩"
            MarketRegime.NEUTRAL -> "大盘中性"
            MarketRegime.UNKNOWN -> "大盘数据不足（中性处理）"
        },
    )
    append("；综合 1/5/20 日 ")
    append(compositeReturn1d.percentText())
    append(" / ")
    append(compositeReturn5d.percentText())
    append(" / ")
    append(compositeReturn20d.percentText())
    append("；评分调整 ")
    append(if (scoreAdjustment >= 0) "+" else "")
    append(scoreAdjustment)
    append("；风险乘数 ")
    append(riskMultiplier)
    marketIndices.forEach { index ->
        append("\n")
        append(index.name)
        append("：1/5/20 日 ")
        append(index.return1d.percentText())
        append(" / ")
        append(index.return5d.percentText())
        append(" / ")
        append(index.return20d.percentText())
    }
}

private fun Double?.percentText(): String = this?.let { "%.2f%%".format(java.util.Locale.US, it * 100) } ?: "--"
