package com.ashareai.app.standalone.research

import android.content.Context
import com.ashareai.app.performance.DeviceResourcePolicy
import com.ashareai.app.performance.ResourceBudget
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.ai.AiRequest
import com.ashareai.app.standalone.data.ai.AiStreamEvent
import com.ashareai.app.standalone.data.ai.AiAgentRouter
import com.ashareai.app.standalone.data.ai.AiTaskType
import com.ashareai.app.standalone.data.ai.LocalInferenceDetector
import com.ashareai.app.standalone.data.ai.OpenAiCompatibleClient
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.data.settings.AutomaticResearchReportConfig
import com.ashareai.app.standalone.data.settings.SettingsStore
import com.ashareai.app.standalone.domain.ResearchCandidate
import com.ashareai.app.standalone.domain.ResearchReport
import com.ashareai.app.standalone.domain.ResearchRequest
import com.ashareai.app.standalone.domain.ResearchResult
import com.ashareai.app.standalone.domain.ResearchRun
import com.ashareai.app.standalone.domain.ResearchRunState
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.domain.ResearchTriggerSource
import com.ashareai.app.standalone.domain.SimulationPortfolio
import com.ashareai.app.standalone.notifications.NotificationRepository
import com.ashareai.app.standalone.work.ResearchFallbackWorker
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ResearchCoordinator(
    private val context: Context,
    private val local: LocalRepository,
    private val market: MarketRepository,
    private val engine: ResearchEngine,
    private val aiClient: OpenAiCompatibleClient,
    private val settings: SettingsStore,
    private val notifications: NotificationRepository,
    private val resourceBudget: () -> ResourceBudget = { DeviceResourcePolicy.from(context) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val json: Json = Json,
) {
    suspend fun enqueue(request: ResearchRequest, startImmediately: Boolean = true): ResearchRun {
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
            triggerSource = request.triggerSource,
            automaticReportSlot = request.automaticReportSlot,
            totalBudget = request.totalBudget,
            perSymbolBudget = request.perSymbolBudget,
            maxStockPrice = request.maxStockPrice,
            configVersion = request.configVersion,
        )
        local.createResearchRun(run)
        if (startImmediately) ResearchService.start(context, run.id)
        return run
    }

    suspend fun enqueueAutomatic(
        config: AutomaticResearchReportConfig,
        startImmediately: Boolean = true,
    ): ResearchRun = enqueue(
        request = ResearchRequest(
            scope = config.scope,
            symbols = config.symbols,
            marketLimit = config.marketLimit,
            includePortfolioDataForAi = false,
            aiProviderId = null,
            triggerSource = ResearchTriggerSource.AUTO,
            automaticReportSlot = config.slot,
            totalBudget = config.totalBudget,
            perSymbolBudget = config.perSymbolBudget,
            maxStockPrice = config.maxStockPrice,
            configVersion = config.configVersion,
        ),
        startImmediately = startImmediately,
    )

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
            val batches = ResearchBatchPlanner.batches(original.symbols, resourceBudget().chunkSize)
            var completed = original.completedCount
            batches.forEach { batch ->
                if (isCancellationRequested(runId)) {
                    finishCancelled(local.researchRun(runId) ?: original)
                    return
                }
                val quotes = market.refreshQuotes(batch).associateBy { it.symbol }
                val parallelism = resourceBudget().maxParallelTasks.coerceIn(1, batch.size.coerceAtLeast(1))
                val analysisDispatcher = Dispatchers.Default.limitedParallelism(parallelism)
                val batchResults = coroutineScope {
                    batch.map { symbol ->
                        async(analysisDispatcher) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val candles = market.dailyCandles(symbol, limit = 100)
                            val quote = quotes[symbol]
                            val result = engine.analyse(
                                symbol = symbol,
                                name = quote?.name ?: symbol,
                                quote = quote,
                                candles = candles,
                                marketContext = marketContext,
                            )
                            AnalysedSecurity(result, candles)
                        }
                    }.awaitAll()
                }
                batchResults.forEach { item ->
                    if (isCancellationRequested(runId)) {
                        finishCancelled(local.researchRun(runId) ?: original)
                        return
                    }
                    analysed += item
                    completed += 1
                    kotlinx.coroutines.yield()
                    local.updateResearchRun(runId, ResearchRunState.RUNNING, completed, now = clock())
                    val progressRun = local.researchRun(runId)
                    if (progressRun != null) onProgress(progressRun)
                }
            }

            val ranked = analysed.sortedByDescending { it.result.score.total }
            val buyAdvices = ResearchAdviceEngine.buildBuyAdvices(
                ranked = ranked.map { it.result to it.candles },
                totalBudget = original.totalBudget,
                perSymbolBudget = original.perSymbolBudget,
                maxStockPrice = original.maxStockPrice,
            )
            val exitAdvices = buildExitAdvices(ranked)
            saveDeterministicOutputs(original, ranked, buyAdvices)
            val aiExplanation = requestAiExplanation(original, ranked, marketContext)
            val report = ResearchReport(
                id = UUID.randomUUID().toString(),
                runId = original.id,
                title = original.automaticReportSlot?.let { "自动研究报告 $it" } ?: "本地研究报告",
                deterministicBody = reportBody(original, ranked, marketContext, buyAdvices, exitAdvices),
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
                systemNotificationId = NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
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
                systemNotificationId = NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
            )
        } finally {
            analysed.clear()
        }
    }

    private suspend fun resolveSymbols(request: ResearchRequest): List<String> {
        val resolved = when (request.scope) {
        ResearchScope.HOLDINGS -> local.holdingsNow().map { it.symbol }
        ResearchScope.WATCHLIST -> if (request.triggerSource == ResearchTriggerSource.AUTO) {
            local.watchlistNow().map { it.symbol } + local.holdingsNow().map { it.symbol }
        } else {
            local.watchlistNow().map { it.symbol }
        }
        ResearchScope.CUSTOM -> request.symbols
        ResearchScope.MARKET -> market.catalog(
            ResearchBatchPlanner.clampMarketLimit(request.marketLimit),
        ).map { it.symbol }
        }.map(String::trim).filter(String::isNotBlank).distinct()
        val maximum = request.maxStockPrice ?: return resolved
        val quotes = market.refreshQuotes(resolved).associateBy { it.symbol }
        return resolved.filter { symbol -> quotes[symbol]?.lastPrice?.let { it <= maximum } == true }
    }

    private suspend fun isCancellationRequested(runId: String): Boolean =
        local.researchRun(runId)?.cancellationRequested == true

    private suspend fun finishCancelled(run: ResearchRun) {
        local.updateResearchRun(
            id = run.id,
            state = ResearchRunState.CANCELLED,
            completedCount = run.completedCount,
            now = clock(),
        )
        notifications.publish(
            title = "本地研究已取消",
            body = "已完成 ${run.completedCount} / ${run.totalCount} 只股票",
            priority = com.ashareai.app.standalone.domain.NotificationPriority.NORMAL,
            deepLink = "research",
            systemNotificationId = NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
        )
    }

    private suspend fun saveDeterministicOutputs(
        run: ResearchRun,
        ranked: List<AnalysedSecurity>,
        buyAdvices: List<BuyAdvice>,
    ) {
        val adviceBySymbol = buyAdvices.associateBy(BuyAdvice::symbol)
        val candidates = ranked.take(30).mapIndexed { index, item ->
            val advice = adviceBySymbol[item.result.symbol]
            ResearchCandidate(
                id = run.id + ":" + item.result.symbol,
                runId = run.id,
                symbol = item.result.symbol,
                name = item.result.name,
                score = item.result.score.total,
                risk = item.result.risk,
                reason = buildString {
                    append(index + 1)
                    append(" 名 · ")
                    append(advice?.action?.label() ?: "观察")
                    advice?.takeIf { it.action == BuyAdviceAction.BUY }?.let {
                        append(" · 入场 ")
                        append(it.entryLow)
                        append("–")
                        append(it.entryHigh)
                        append(" · ")
                        append(it.quantity)
                        append(" 股")
                    }
                    append("：")
                    append(item.result.summary)
                },
                createdAt = clock(),
            )
        }
        local.replaceCandidates(run.id, candidates)
        val portfolioItems = buyAdvices.filter { it.action == BuyAdviceAction.BUY }.take(10).map {
            mapOf(
                "symbol" to it.symbol,
                "name" to it.name,
                "score" to it.score.toString(),
                "action" to it.action.name,
                "quantity" to it.quantity.toString(),
                "planned_amount" to it.plannedAmount.toString(),
                "entry_low" to it.entryLow.toString(),
                "entry_high" to it.entryHigh.toString(),
                "stop_loss" to it.stopLoss.toString(),
                "take_profit" to it.takeProfit.toString(),
                "weight" to if (run.totalBudget > 0.0) (it.plannedAmount / run.totalBudget).toString() else "0",
            )
        }
        if (portfolioItems.isNotEmpty()) {
            local.saveSimulationPortfolio(
                SimulationPortfolio(
                    id = UUID.randomUUID().toString(),
                    runId = run.id,
                    name = "本地模拟组合",
                    holdingsJson = json.encodeToString(portfolioItems),
                    score = buyAdvices.filter { it.action == BuyAdviceAction.BUY }.take(10).map(BuyAdvice::score).average(),
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
        val providers = local.aiProviders.first()
        val routing = AiAgentRouter.route(
            runtime = LocalInferenceDetector.runtimeSnapshot(context, AiTaskType.RESEARCH_EXPLANATION),
            agents = settings.aiAgents.first(),
            providers = providers,
            providerStates = aiClient.providerRuntimeStates(),
            explicitProviderId = run.aiProviderId,
        )
        val providerId = routing.provider?.id ?: return null
        if (routing.target == com.ashareai.app.standalone.data.ai.AiExecutionTarget.LOCAL ||
            routing.target == com.ashareai.app.standalone.data.ai.AiExecutionTarget.CACHE ||
            routing.target == com.ashareai.app.standalone.data.ai.AiExecutionTarget.DETERMINISTIC
        ) return null
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
        aiClient.streamWithFallback(
            AiRequest(
                providerId = providerId,
                systemInstruction = "你是 A 股本地研究的解释助手。只解释已有确定性数据。",
                prompt = context,
            ),
            routing.fallbackProviders.map { it.id },
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
        buyAdvices: List<BuyAdvice>,
        exitAdvices: List<ExitResearch>,
    ): String = buildString {
        appendLine("# ${run.automaticReportSlot?.let { "自动研究报告 $it" } ?: "本地研究报告"}")
        appendLine()
        appendLine("## 运行快照")
        appendLine("- 触发：${if (run.triggerSource == ResearchTriggerSource.AUTO) "自动日研" else "手动研究"}${run.automaticReportSlot?.let { " · 报告 $it" }.orEmpty()}")
        appendLine("- 范围：${run.scope.name} · 样本：${run.totalCount} · 配置版本：${run.configVersion}")
        appendLine("- 预算：${run.totalBudget.money()} 元 · 单股上限：${run.perSymbolBudget.money()} 元 · 最高股价：${run.maxStockPrice?.money() ?: "不限"}")
        appendLine("- 结论只来自本地确定性规则；AI 解释不能修改动作、价格、数量或风险门槛。")
        appendLine()
        appendLine("## 冻结大盘环境")
        appendLine(marketContext.reportSummary())
        appendLine()
        appendLine("## 买入与观察建议")
        appendLine("| 动作 | 证券 | 分数 | 入场区间 | 数量/预算 | 止损/止盈 | 依据 |")
        appendLine("| --- | --- | ---: | --- | --- | --- | --- |")
        buyAdvices.take(30).forEach { advice ->
            appendLine(
                "| ${advice.action.label()} | ${advice.name} ${advice.symbol} | ${advice.score} | " +
                    "${advice.entryLow ?: "--"}–${advice.entryHigh ?: "--"} | ${advice.quantity} 股 / ${advice.plannedAmount} 元 | " +
                    "${advice.stopLoss ?: "--"} / ${advice.takeProfit ?: "--"} | ${advice.reasons.joinToString("；")} |",
            )
        }
        appendLine()
        appendLine("## 持仓卖出与持有建议")
        if (exitAdvices.isEmpty()) {
            appendLine("当前没有持仓，未生成卖出建议。")
        } else {
            appendLine("| 证券 | 动作 | 现价 | 止损线 | 浮盈退出参考 | 依据 |")
            appendLine("| --- | --- | ---: | ---: | ---: | --- |")
            exitAdvices.forEach { advice ->
                appendLine("| ${advice.name} ${advice.symbol} | ${advice.state} | ${advice.currentPrice ?: "--"} | ${advice.stopLoss.money()} | ${advice.profitExit.money()} | ${advice.explanation} |")
            }
        }
        appendLine()
        appendLine("## 确定性评分明细")
        ranked.take(30).forEachIndexed { index, item -> appendLine("${index + 1}. ${item.result.summary}") }
        appendLine()
        appendLine("> 所有买卖建议仅用于研究、回测和模拟，不会自动交易，也不构成投资建议。")
    }

    private suspend fun buildExitAdvices(ranked: List<AnalysedSecurity>): List<ExitResearch> {
        val analysedBySymbol = ranked.associateBy { it.result.symbol }
        val engine = ExitResearchEngine()
        return local.holdingsNow().map { holding ->
            val analysed = analysedBySymbol[holding.symbol]
            if (analysed != null) {
                engine.analyse(holding, analysed.result.quote, analysed.candles)
            } else {
                engine.analyse(
                    holding = holding,
                    quote = market.refreshQuote(holding.symbol),
                    candles = market.dailyCandles(holding.symbol, limit = 100),
                )
            }
        }
    }

    private data class AnalysedSecurity(
        val result: ResearchResult,
        val candles: List<com.ashareai.app.standalone.domain.DailyCandle>,
    )
}

private fun BuyAdviceAction.label(): String = when (this) {
    BuyAdviceAction.BUY -> "买入"
    BuyAdviceAction.WATCH -> "观察"
    BuyAdviceAction.NO_BUY -> "暂不买入"
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
