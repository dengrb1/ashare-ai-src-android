package com.ashareai.app.standalone.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_backtests")
data class LocalBacktestEntity(
    @PrimaryKey val id: String,
    val startDate: String,
    val endDate: String,
    val initialCash: Double,
    val benchmark: String,
    val reportId: String?,
    val feeRate: Double,
    val status: String,  // PENDING | RUNNING | SUCCEEDED | FAILED
    val metricsJson: String?,  // BacktestMetrics 的 JSON
    val errorMessage: String?,
    val createdAt: Long,
    val completedAt: Long?,
)

@Entity(tableName = "local_backtest_trades")
data class LocalBacktestTradeEntity(
    @PrimaryKey val id: String,
    val backtestId: String,
    val symbol: String,
    val name: String,
    val action: String,  // BUY | SELL
    val date: String,
    val price: Double,
    val quantity: Int,
    val amount: Double,
    val fee: Double,
    val reason: String,
)
