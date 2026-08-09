package com.ashareai.app.standalone.data.ai

import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.ResearchResult

object AiPayloadBuilder {
    fun researchPrompt(
        result: ResearchResult,
        candles: List<DailyCandle>,
        holding: Holding?,
        includePortfolio: Boolean,
    ): String = buildString {
        append("请只解释以下本地确定性研究，不得改写风险门槛或编造基本面、事件数据。\n")
        append("股票：")
        append(result.symbol)
        append(" ")
        append(result.name)
        append("\n本地摘要：")
        append(result.summary)
        append("\n报价：")
        append(quoteLine(result.quote))
        append("\n近")
        append(candles.takeLast(20).size)
        append("日收盘：")
        append(candles.takeLast(20).joinToString(",") { it.close.toString() })
        if (includePortfolio && holding != null) {
            append("\n已获用户单独授权的持仓信息：成本 ")
            append(holding.averageCost)
            append("，数量 ")
            append(holding.quantity)
        }
    }

    fun chatPrompt(
        userText: String,
        result: ResearchResult?,
        candles: List<DailyCandle>,
        holding: Holding?,
        includePortfolio: Boolean,
    ): String {
        if (result == null) return userText
        return researchPrompt(result, candles, holding, includePortfolio) + "\n用户问题：" + userText
    }

    private fun quoteLine(quote: MarketQuote?): String = when {
        quote == null -> "不可用"
        quote.lastPrice == null -> "不可用"
        else -> quote.lastPrice.toString() + "（" + quote.freshness.name + "）"
    }
}
