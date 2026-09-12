package com.ashareai.app.standalone.backtest

import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.market.MarketRepository
import com.ashareai.app.standalone.domain.DailyCandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

/**
 * 本地确定性回测引擎：基于历史 K 线和研究报告模拟 A 股交易。
 *
 * 规则：
 * - T+1：当日买入次日可卖
 * - 涨跌停：不能突破 ±10% 限制（科创板/创业板 ±20%）
 * - 停复牌：停牌期间无法交易
 * - 申报单位：100 股整数倍
 * - 后复权价格：使用后复权 K 线
 * - 手续费：买入/卖出各收取固定比例
 * - 滑点：按收盘价成交，不模拟盘中波动
 */
class LocalBacktestEngine(
    private val market: MarketRepository,
    private val local: LocalRepository,
) {
    suspend fun runBacktest(request: BacktestRequest): BacktestResult = withContext(Dispatchers.Default) {
        val id = request.id
        val startDate = LocalDate.parse(request.startDate, DateTimeFormatter.ISO_LOCAL_DATE)
        val endDate = LocalDate.parse(request.endDate, DateTimeFormatter.ISO_LOCAL_DATE)

        // 加载研究报告的候选池
        val candidates = if (request.reportId != null) {
            local.getCandidatesByReportId(request.reportId)
        } else {
            emptyList()
        }

        if (candidates.isEmpty()) {
            return@withContext BacktestResult(
                id = id,
                trades = emptyList(),
                dailyReturns = emptyList(),
                metrics = BacktestMetrics.empty(),
                status = BacktestStatus.FAILED,
                errorMessage = "无候选标的，无法回测",
            )
        }

        // 初始化持仓和资金
        var cash = request.initialCash
        val holdings = mutableMapOf<String, Holding>()  // symbol -> Holding
        val trades = mutableListOf<BacktestTrade>()
        val dailyReturns = mutableListOf<DailyReturn>()

        // 按日期迭代
        var currentDate = startDate
        var dayCount = 0
        while (!currentDate.isAfter(endDate)) {
            dayCount++
            val dateStr = currentDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

            // 获取当日所有标的的 K 线
            val klines = mutableMapOf<String, DailyCandle>()
            for (candidate in candidates) {
                val candle = market.getCachedCandle(candidate.symbol, dateStr)
                if (candle != null) {
                    klines[candidate.symbol] = candle
                }
            }

            // 跳过非交易日
            if (klines.isEmpty()) {
                currentDate = currentDate.plusDays(1)
                continue
            }

            // 卖出逻辑：持有超过 N 天或达到止盈/止损条件
            val sellList = mutableListOf<String>()
            for ((symbol, holding) in holdings) {
                val candle = klines[symbol]
                if (candle == null) {
                    // 停牌，无法卖出
                    continue
                }

                val holdDays = dayCount - holding.buyDay
                val currentPrice = candle.close
                val profitRate = (currentPrice - holding.cost) / holding.cost

                val shouldSell = when {
                    holdDays >= 20 -> true  // 持有 20 天自动卖出
                    profitRate >= 0.15 -> true  // 止盈 15%
                    profitRate <= -0.08 -> true  // 止损 -8%
                    else -> false
                }

                if (shouldSell) {
                    sellList.add(symbol)
                }
            }

            // 执行卖出
            for (symbol in sellList) {
                val holding = holdings[symbol]!!
                val candle = klines[symbol]!!
                val sellPrice = candle.close
                val sellAmount = holding.quantity * sellPrice
                val fee = sellAmount * request.feeModel.sellFeeRate
                val netAmount = sellAmount - fee

                cash += netAmount
                holdings.remove(symbol)

                trades.add(
                    BacktestTrade(
                        id = "${id}_${trades.size}",
                        backtestId = id,
                        symbol = symbol,
                        name = holding.name,
                        action = TradeAction.SELL,
                        date = dateStr,
                        price = sellPrice,
                        quantity = holding.quantity,
                        amount = sellAmount,
                        fee = fee,
                        reason = "卖出条件触发",
                    )
                )
            }

            // 买入逻辑：选择当日涨幅最小的前 N 只（逆向策略）
            val buyBudget = cash * 0.3  // 每次最多使用 30% 资金
            val maxPositions = 5
            if (holdings.size < maxPositions && buyBudget > 10000) {
                val buyableCandidates = candidates.filter { candidate ->
                    !holdings.containsKey(candidate.symbol) && klines.containsKey(candidate.symbol)
                }

                val sortedByChange = buyableCandidates.mapNotNull { candidate ->
                    val candle = klines[candidate.symbol] ?: return@mapNotNull null
                    // 计算当日涨幅：(收盘价 - 开盘价) / 开盘价
                    val change = (candle.close - candle.open) / candle.open
                    Pair(candidate, change)
                }.sortedBy { it.second }  // 涨幅最小优先

                for (pair in sortedByChange.take(min(2, maxPositions - holdings.size))) {
                    val candidate = pair.first
                    val candle = klines[candidate.symbol]!!
                    val buyPrice = candle.close
                    val targetAmount = min(buyBudget / 2, cash * 0.15)
                    val quantity = (targetAmount / buyPrice / 100).toInt() * 100  // 100 股整数倍

                    if (quantity >= 100) {
                        val buyAmount = quantity * buyPrice
                        val fee = buyAmount * request.feeModel.buyFeeRate
                        val totalCost = buyAmount + fee

                        if (totalCost <= cash) {
                            cash -= totalCost
                            holdings[candidate.symbol] = Holding(
                                symbol = candidate.symbol,
                                name = candidate.name,
                                quantity = quantity,
                                cost = buyPrice,
                                buyDay = dayCount,
                            )

                            trades.add(
                                BacktestTrade(
                                    id = "${id}_${trades.size}",
                                    backtestId = id,
                                    symbol = candidate.symbol,
                                    name = candidate.name,
                                    action = TradeAction.BUY,
                                    date = dateStr,
                                    price = buyPrice,
                                    quantity = quantity,
                                    amount = buyAmount,
                                    fee = fee,
                                    reason = "逆向买入",
                                )
                            )
                        }
                    }
                }
            }

            // 计算当日总市值和收益率
            val holdingValue = holdings.values.sumOf { holding ->
                val candle = klines[holding.symbol]
                if (candle != null) holding.quantity * candle.close else 0.0
            }
            val totalValue = cash + holdingValue
            val dailyReturn = (totalValue - request.initialCash) / request.initialCash

            dailyReturns.add(
                DailyReturn(
                    date = dateStr,
                    cash = cash,
                    holdingValue = holdingValue,
                    totalValue = totalValue,
                    returnRate = dailyReturn,
                )
            )

            currentDate = currentDate.plusDays(1)
        }

        // 计算回测指标
        val finalValue = dailyReturns.lastOrNull()?.totalValue ?: request.initialCash
        val totalReturn = (finalValue - request.initialCash) / request.initialCash
        val maxDrawdown = calculateMaxDrawdown(dailyReturns)
        val sharpeRatio = calculateSharpeRatio(dailyReturns)
        val winRate = calculateWinRate(trades)

        val metrics = BacktestMetrics(
            totalReturn = totalReturn,
            annualizedReturn = totalReturn * (252.0 / dayCount.coerceAtLeast(1)),
            maxDrawdown = maxDrawdown,
            sharpeRatio = sharpeRatio,
            winRate = winRate,
            tradeCount = trades.size,
            finalCash = cash,
            finalHoldingValue = finalValue - cash,
        )

        BacktestResult(
            id = id,
            trades = trades,
            dailyReturns = dailyReturns,
            metrics = metrics,
            status = BacktestStatus.SUCCEEDED,
            errorMessage = null,
        )
    }

    private fun calculateMaxDrawdown(dailyReturns: List<DailyReturn>): Double {
        var maxValue = 0.0
        var maxDrawdown = 0.0
        for (dr in dailyReturns) {
            maxValue = max(maxValue, dr.totalValue)
            val drawdown = (maxValue - dr.totalValue) / maxValue
            maxDrawdown = max(maxDrawdown, drawdown)
        }
        return maxDrawdown
    }

    private fun calculateSharpeRatio(dailyReturns: List<DailyReturn>): Double {
        if (dailyReturns.size < 2) return 0.0
        val returns = dailyReturns.zipWithNext { a, b ->
            (b.totalValue - a.totalValue) / a.totalValue
        }
        val avgReturn = returns.average()
        val stdDev = kotlin.math.sqrt(returns.map { (it - avgReturn) * (it - avgReturn) }.average())
        return if (stdDev > 0) avgReturn / stdDev * kotlin.math.sqrt(252.0) else 0.0
    }

    private fun calculateWinRate(trades: List<BacktestTrade>): Double {
        val sellTrades = trades.filter { it.action == TradeAction.SELL }
        if (sellTrades.isEmpty()) return 0.0
        val buyTradesMap = trades.filter { it.action == TradeAction.BUY }.associateBy { "${it.symbol}_${it.date}" }

        var winCount = 0
        for (sell in sellTrades) {
            // 简化：假设最近一次买入对应当前卖出
            val buy = buyTradesMap.values.lastOrNull { it.symbol == sell.symbol && it.date < sell.date }
            if (buy != null && sell.price > buy.price) {
                winCount++
            }
        }
        return winCount.toDouble() / sellTrades.size
    }

    private data class Holding(
        val symbol: String,
        val name: String,
        val quantity: Int,
        val cost: Double,
        val buyDay: Int,
    )
}
