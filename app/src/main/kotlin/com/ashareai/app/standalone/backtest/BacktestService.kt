package com.ashareai.app.standalone.backtest

import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.market.MarketRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * 回测服务：管理回测任务的生命周期和持久化。
 */
class BacktestService(
    private val engine: LocalBacktestEngine,
    private val local: LocalRepository,
) {
    /**
     * 提交回测任务并执行。
     */
    suspend fun submitBacktest(
        startDate: String,
        endDate: String,
        initialCash: Double,
        benchmark: String,
        reportId: String?,
        feeRate: Double = 0.0003,
    ): String {
        val id = UUID.randomUUID().toString()
        val request = BacktestRequest(
            id = id,
            startDate = startDate,
            endDate = endDate,
            initialCash = initialCash,
            benchmark = benchmark,
            reportId = reportId,
            feeModel = FeeModel(buyFeeRate = feeRate, sellFeeRate = feeRate),
        )

        // 创建初始记录
        local.saveBacktest(
            id = id,
            startDate = startDate,
            endDate = endDate,
            initialCash = initialCash,
            benchmark = benchmark,
            reportId = reportId,
            feeRate = feeRate,
            status = BacktestStatus.RUNNING,
            metricsJson = null,
            errorMessage = null,
        )

        // 执行回测
        val result = try {
            engine.runBacktest(request)
        } catch (e: Exception) {
            BacktestResult(
                id = id,
                trades = emptyList(),
                dailyReturns = emptyList(),
                metrics = BacktestMetrics.empty(),
                status = BacktestStatus.FAILED,
                errorMessage = e.message ?: "未知错误",
            )
        }

        // 更新结果
        local.updateBacktestResult(
            id = id,
            status = result.status,
            metricsJson = if (result.status == BacktestStatus.SUCCEEDED) {
                serializeMetrics(result.metrics)
            } else null,
            errorMessage = result.errorMessage,
        )

        // 保存交易记录
        if (result.status == BacktestStatus.SUCCEEDED) {
            for (trade in result.trades) {
                local.saveBacktestTrade(trade)
            }
        }

        return id
    }

    /**
     * 查询回测状态。
     */
    suspend fun getBacktestStatus(id: String): BacktestStatus? {
        return local.getBacktestStatus(id)
    }

    /**
     * 获取回测结果。
     */
    suspend fun getBacktestResult(id: String): BacktestResult? {
        val status = local.getBacktestStatus(id) ?: return null
        val metricsJson = local.getBacktestMetricsJson(id)
        val errorMessage = local.getBacktestErrorMessage(id)
        val trades = local.getBacktestTrades(id)

        val metrics = if (metricsJson != null) {
            deserializeMetrics(metricsJson)
        } else {
            BacktestMetrics.empty()
        }

        return BacktestResult(
            id = id,
            trades = trades,
            dailyReturns = emptyList(),  // 不返回每日收益（数据量大）
            metrics = metrics,
            status = status,
            errorMessage = errorMessage,
        )
    }

    /**
     * 列出所有回测。
     */
    fun listBacktests(limit: Int = 20): Flow<List<BacktestSummary>> {
        return local.listBacktests(limit)
    }

    /**
     * 删除回测。
     */
    suspend fun deleteBacktest(id: String) {
        local.deleteBacktest(id)
    }

    private fun serializeMetrics(metrics: BacktestMetrics): String {
        return """
            {
                "totalReturn": ${metrics.totalReturn},
                "annualizedReturn": ${metrics.annualizedReturn},
                "maxDrawdown": ${metrics.maxDrawdown},
                "sharpeRatio": ${metrics.sharpeRatio},
                "winRate": ${metrics.winRate},
                "tradeCount": ${metrics.tradeCount},
                "finalCash": ${metrics.finalCash},
                "finalHoldingValue": ${metrics.finalHoldingValue}
            }
        """.trimIndent()
    }

    private fun deserializeMetrics(json: String): BacktestMetrics {
        // 简单的手动解析（生产环境应使用 kotlinx.serialization）
        val totalReturn = json.substringAfter("\"totalReturn\": ").substringBefore(",").toDouble()
        val annualizedReturn = json.substringAfter("\"annualizedReturn\": ").substringBefore(",").toDouble()
        val maxDrawdown = json.substringAfter("\"maxDrawdown\": ").substringBefore(",").toDouble()
        val sharpeRatio = json.substringAfter("\"sharpeRatio\": ").substringBefore(",").toDouble()
        val winRate = json.substringAfter("\"winRate\": ").substringBefore(",").toDouble()
        val tradeCount = json.substringAfter("\"tradeCount\": ").substringBefore(",").toInt()
        val finalCash = json.substringAfter("\"finalCash\": ").substringBefore(",").toDouble()
        val finalHoldingValue = json.substringAfter("\"finalHoldingValue\": ").substringBefore("}").toDouble()

        return BacktestMetrics(
            totalReturn = totalReturn,
            annualizedReturn = annualizedReturn,
            maxDrawdown = maxDrawdown,
            sharpeRatio = sharpeRatio,
            winRate = winRate,
            tradeCount = tradeCount,
            finalCash = finalCash,
            finalHoldingValue = finalHoldingValue,
        )
    }
}

data class BacktestSummary(
    val id: String,
    val startDate: String,
    val endDate: String,
    val status: BacktestStatus,
    val totalReturn: Double?,
    val createdAt: Long,
)
