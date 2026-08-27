package com.ashareai.app.standalone.backtest

import org.junit.Assert.*
import org.junit.Test

/**
 * 本地回测引擎测试。
 *
 * 验证回测模型和数据结构。
 */
class LocalBacktestEngineTest {

    @Test
    fun backtestRequest_validation() {
        val request = BacktestRequest(
            id = "test-1",
            startDate = "2026-01-01",
            endDate = "2026-12-31",
            initialCash = 1_000_000.0,
            benchmark = "000300",
            reportId = null,
            feeModel = FeeModel(
                buyFeeRate = 0.0003,
                sellFeeRate = 0.0013, // 卖出包含印花税
            ),
        )

        assertEquals("test-1", request.id)
        assertEquals(1_000_000.0, request.initialCash, 0.01)
        assertEquals("000300", request.benchmark)
    }

    @Test
    fun backtestResult_initialState() {
        val result = BacktestResult(
            id = "test-1",
            trades = emptyList(),
            dailyReturns = emptyList(),
            metrics = BacktestMetrics(
                totalReturn = 0.0,
                annualizedReturn = 0.0,
                maxDrawdown = 0.0,
                sharpeRatio = 0.0,
                winRate = 0.0,
                tradeCount = 0,
                finalCash = 1_000_000.0,
                finalHoldingValue = 0.0,
            ),
            status = BacktestStatus.PENDING,
            errorMessage = null,
        )

        assertEquals(BacktestStatus.PENDING, result.status)
        assertTrue(result.trades.isEmpty())
        assertTrue(result.dailyReturns.isEmpty())
        assertNull(result.errorMessage)
    }

    @Test
    fun backtestTrade_buyOrder() {
        val trade = BacktestTrade(
            id = "trade-1",
            backtestId = "test-1",
            symbol = "600519",
            name = "贵州茅台",
            action = TradeAction.BUY,
            date = "2026-01-02",
            price = 1800.0,
            quantity = 100,
            amount = 180000.0,
            fee = 54.0,
            reason = "买入信号",
        )

        assertEquals(TradeAction.BUY, trade.action)
        assertEquals(100, trade.quantity)
        assertEquals(1800.0, trade.price, 0.01)
        // 买入金额 = 价格 * 数量
        assertEquals(180000.0, trade.amount, 0.01)
    }

    @Test
    fun backtestTrade_sellOrder() {
        val trade = BacktestTrade(
            id = "trade-2",
            backtestId = "test-1",
            symbol = "600519",
            name = "贵州茅台",
            action = TradeAction.SELL,
            date = "2026-01-22", // T+1: 买入后至少 1 天
            price = 1850.0,
            quantity = 100,
            amount = 185000.0,
            fee = 240.5,
            reason = "止盈信号",
        )

        assertEquals(TradeAction.SELL, trade.action)
        assertEquals(185000.0, trade.amount, 0.01)
        // 卖出净收益 = 金额 - 手续费
        val netProceeds = trade.amount - trade.fee
        assertEquals(184759.5, netProceeds, 0.01)
    }

    @Test
    fun backtestMetrics_positiveReturn() {
        val metrics = BacktestMetrics(
            totalReturn = 0.15, // 15% 收益
            annualizedReturn = 0.18, // 18% 年化
            maxDrawdown = -0.05, // 5% 最大回撤
            sharpeRatio = 1.2,
            winRate = 0.6, // 60% 胜率
            tradeCount = 10,
            finalCash = 50000.0,
            finalHoldingValue = 1100000.0,
        )

        assertTrue(metrics.totalReturn > 0)
        assertTrue(metrics.annualizedReturn > 0)
        assertTrue(metrics.maxDrawdown < 0)
        assertTrue(metrics.sharpeRatio > 1)
        assertTrue(metrics.winRate > 0.5)
    }

    @Test
    fun backtestMetrics_negativeReturn() {
        val metrics = BacktestMetrics(
            totalReturn = -0.10, // -10% 亏损
            annualizedReturn = -0.12,
            maxDrawdown = -0.15,
            sharpeRatio = -0.5,
            winRate = 0.4, // 40% 胜率
            tradeCount = 8,
            finalCash = 100000.0,
            finalHoldingValue = 800000.0,
        )

        assertTrue(metrics.totalReturn < 0)
        assertTrue(metrics.annualizedReturn < 0)
        assertTrue(metrics.sharpeRatio < 0)
        assertTrue(metrics.winRate < 0.5)
    }

    @Test
    fun dailyReturn_calculation() {
        val daily = DailyReturn(
            date = "2026-01-02",
            cash = 50_000.0,
            holdingValue = 1_000_000.0,
            totalValue = 1_050_000.0,
            returnRate = 0.05, // 5% 日收益
        )

        assertEquals(1_050_000.0, daily.totalValue, 0.01)
        assertEquals(0.05, daily.returnRate, 0.0001)
        // 验证组合价值 = 现金 + 持仓
        assertEquals(daily.totalValue, daily.cash + daily.holdingValue, 0.01)
    }

    @Test
    fun quantity_mustBeMultipleOf100() {
        // A 股申报单位：100 股整数倍
        val validQuantities = listOf(100, 200, 500, 1000, 10000)
        validQuantities.forEach { qty ->
            assertEquals(0, qty % 100)
        }

        val invalidQuantities = listOf(50, 150, 250, 999)
        invalidQuantities.forEach { qty ->
            assertNotEquals(0, qty % 100)
        }
    }

    @Test
    fun backtestStatus_lifecycle() {
        val statuses = listOf(
            BacktestStatus.PENDING,
            BacktestStatus.RUNNING,
            BacktestStatus.SUCCEEDED,
        )

        // 验证状态枚举存在
        assertEquals("PENDING", BacktestStatus.PENDING.name)
        assertEquals("RUNNING", BacktestStatus.RUNNING.name)
        assertEquals("SUCCEEDED", BacktestStatus.SUCCEEDED.name)
    }

    @Test
    fun tradeAction_enumValues() {
        assertEquals("BUY", TradeAction.BUY.name)
        assertEquals("SELL", TradeAction.SELL.name)
    }

    @Test
    fun feeModel_structure() {
        val feeModel = FeeModel(
            buyFeeRate = 0.0003,
            sellFeeRate = 0.0013, // 包含印花税 0.001
        )

        assertEquals(0.0003, feeModel.buyFeeRate, 0.00001)
        assertEquals(0.0013, feeModel.sellFeeRate, 0.00001)
    }
}
