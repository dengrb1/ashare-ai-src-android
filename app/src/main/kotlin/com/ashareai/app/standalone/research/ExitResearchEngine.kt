package com.ashareai.app.standalone.research

import com.ashareai.app.standalone.alerts.AlertEvaluator
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote

data class ExitResearch(
    val symbol: String,
    val name: String,
    val currentPrice: Double?,
    val stopLoss: Double,
    val profitExit: Double,
    val state: String,
    val explanation: String,
)

class ExitResearchEngine(
    private val alerts: AlertEvaluator = AlertEvaluator(),
) {
    fun analyse(
        holding: Holding,
        quote: MarketQuote?,
        candles: List<DailyCandle>,
    ): ExitResearch {
        val atr = TechnicalIndicators.atr(candles, 20)
        val stopLoss = alerts.stopLossPrice(holding.averageCost, atr)
        val profitExit = atr?.let { holding.averageCost + it * 3 } ?: holding.averageCost * 1.15
        val price = quote?.lastPrice
        val state = when {
            price == null -> "数据不足"
            price <= stopLoss -> "止损优先"
            price >= profitExit -> "浮盈退出观察"
            price > holding.averageCost -> "浮盈持有观察"
            else -> "成本下方观察"
        }
        val explanation = buildString {
            append("止损线 ")
            append(stopLoss)
            append("（ATR20×2，限制成本下方 5%–10%，无 K 线时为 8%）；")
            append("浮盈退出参考 ")
            append(profitExit)
            if (quote?.freshness != MarketFreshness.FRESH) append("；报价不是新鲜实时数据")
        }
        return ExitResearch(
            symbol = holding.symbol,
            name = holding.name,
            currentPrice = price,
            stopLoss = stopLoss,
            profitExit = profitExit,
            state = state,
            explanation = explanation,
        )
    }
}
