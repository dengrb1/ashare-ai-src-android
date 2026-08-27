package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.ResearchResult
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class BuyAdviceAction {
    BUY,
    WATCH,
    NO_BUY,
}

data class BuyAdvice(
    val symbol: String,
    val name: String,
    val action: BuyAdviceAction,
    val score: Double,
    val currentPrice: Double?,
    val entryLow: Double?,
    val entryHigh: Double?,
    val quantity: Int,
    val plannedAmount: Double,
    val stopLoss: Double?,
    val takeProfit: Double?,
    val reasons: List<String>,
)

object ResearchAdviceEngine {
    const val BUY_SCORE_THRESHOLD = 70.0
    private const val WATCH_SCORE_THRESHOLD = 55.0
    private const val BOARD_LOT = 100

    fun buildBuyAdvices(
        ranked: List<Pair<ResearchResult, List<DailyCandle>>>,
        totalBudget: Double,
        perSymbolBudget: Double,
        maxStockPrice: Double?,
    ): List<BuyAdvice> {
        var remaining = totalBudget.coerceAtLeast(0.0)
        return ranked.map { (result, candles) ->
            val advice = buildBuyAdvice(
                result = result,
                candles = candles,
                availableBudget = remaining,
                perSymbolBudget = perSymbolBudget,
                maxStockPrice = maxStockPrice,
            )
            if (advice.action == BuyAdviceAction.BUY) remaining = (remaining - advice.plannedAmount).coerceAtLeast(0.0)
            advice
        }
    }

    fun buildBuyAdvice(
        result: ResearchResult,
        candles: List<DailyCandle>,
        availableBudget: Double,
        perSymbolBudget: Double,
        maxStockPrice: Double?,
    ): BuyAdvice {
        val price = result.quote?.lastPrice?.takeIf { it.isFinite() && it > 0.0 }
        val atr = result.score.atr?.takeIf { it.isFinite() && it > 0.0 }
        val blocking = buildList {
            if (result.quote?.freshness != MarketFreshness.FRESH) add("缺少新鲜报价")
            if (candles.size < 60) add("K 线少于 60 个交易日")
            if (result.score.total < BUY_SCORE_THRESHOLD) add("最终分低于 ${BUY_SCORE_THRESHOLD.toInt()}")
            if (result.risk != "中") add("风险等级为${result.risk}")
            if (price == null) add("当前价格不可用")
            if (maxStockPrice != null && price != null && price > maxStockPrice) add("价格高于配置上限")
            if (atr == null) add("ATR 不可用")
        }
        val entryLow = if (price != null && atr != null) max(price * 0.98, price - atr * 0.5).money() else null
        val entryHigh = if (price != null && atr != null) min(price * 1.01, price + atr * 0.2).money() else null
        val spendLimit = min(availableBudget, perSymbolBudget).coerceAtLeast(0.0)
        val quantity = entryHigh?.let { floor(spendLimit / it / BOARD_LOT).toInt() * BOARD_LOT } ?: 0
        val reasons = blocking.toMutableList()
        if (blocking.isEmpty() && quantity < BOARD_LOT) reasons += "预算不足 1 手"
        val action = when {
            reasons.isEmpty() -> BuyAdviceAction.BUY
            result.score.total >= WATCH_SCORE_THRESHOLD && price != null -> BuyAdviceAction.WATCH
            else -> BuyAdviceAction.NO_BUY
        }
        val plannedAmount = if (action == BuyAdviceAction.BUY && entryHigh != null) (quantity * entryHigh).money() else 0.0
        return BuyAdvice(
            symbol = result.symbol,
            name = result.name,
            action = action,
            score = result.score.total,
            currentPrice = price?.money(),
            entryLow = entryLow,
            entryHigh = entryHigh,
            quantity = if (action == BuyAdviceAction.BUY) quantity else 0,
            plannedAmount = plannedAmount,
            stopLoss = if (price != null && atr != null) max(price * 0.90, price - atr * 2).money() else null,
            takeProfit = if (price != null && atr != null) max(price * 1.08, price + atr * 3).money() else null,
            reasons = reasons.ifEmpty { listOf("满足确定性评分、风险、数据与预算门槛") },
        )
    }
}

internal fun Double.money(): Double = kotlin.math.round(this * 100) / 100.0
