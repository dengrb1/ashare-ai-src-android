package com.ashareai.app.standalone.backtest

/**
 * 本地确定性回测引擎：使用本地缓存 K 线与研究快照进行历史模拟。
 * 遵守 T+1、涨跌停、停复牌、申报单位和后复权规则。
 */
data class BacktestRequest(
    val id: String,
    val startDate: String,
    val endDate: String,
    val initialCash: Double,
    val benchmark: String,
    val reportId: String?,
    val feeModel: FeeModel,
)

data class FeeModel(
    val buyFeeRate: Double,
    val sellFeeRate: Double,
)

enum class BacktestStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
}

data class BacktestResult(
    val id: String,
    val trades: List<BacktestTrade>,
    val dailyReturns: List<DailyReturn>,
    val metrics: BacktestMetrics,
    val status: BacktestStatus,
    val errorMessage: String?,
)

data class BacktestTrade(
    val id: String,
    val backtestId: String,
    val symbol: String,
    val name: String,
    val action: TradeAction,
    val date: String,
    val price: Double,
    val quantity: Int,
    val amount: Double,
    val fee: Double,
    val reason: String,
)

enum class TradeAction {
    BUY,
    SELL,
}

data class DailyReturn(
    val date: String,
    val cash: Double,
    val holdingValue: Double,
    val totalValue: Double,
    val returnRate: Double,
)

data class BacktestMetrics(
    val totalReturn: Double,
    val annualizedReturn: Double,
    val maxDrawdown: Double,
    val sharpeRatio: Double,
    val winRate: Double,
    val tradeCount: Int,
    val finalCash: Double,
    val finalHoldingValue: Double,
) {
    companion object {
        fun empty() = BacktestMetrics(
            totalReturn = 0.0,
            annualizedReturn = 0.0,
            maxDrawdown = 0.0,
            sharpeRatio = 0.0,
            winRate = 0.0,
            tradeCount = 0,
            finalCash = 0.0,
            finalHoldingValue = 0.0,
        )
    }
}
