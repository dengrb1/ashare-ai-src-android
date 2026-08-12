package com.ashareai.app.ui.screens

import com.ashareai.app.data.normalizeSymbol

internal fun parseResearchSymbols(value: String): List<String> = value
    .split(Regex("[,，;；\\s]+"))
    .mapNotNull(::normalizeSymbol)
    .distinct()
    .take(100)

internal fun positiveNumber(value: String): Double? =
    value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }

internal fun optionalPositiveNumber(value: String): Double? =
    value.takeIf { it.isNotBlank() }?.let(::positiveNumber)

internal fun validateResearchInput(
    scope: String,
    availableAssetCount: Int,
    selectedSymbols: List<String>,
    total: Double?,
    perSymbol: Double?,
    maxText: String,
    maxPrice: Double?,
): String? = when {
    scope != "MARKET" && selectedSymbols.isEmpty() ->
        if (scope == "WATCHLIST" && availableAssetCount == 0) "自选与持仓为空，请先添加股票" else "请至少选择一只有效 A 股"
    total == null || perSymbol == null -> "总预算和单股最高投入必须大于 0"
    perSymbol > total -> "单股最高投入不能超过总预算"
    maxText.isNotBlank() && maxPrice == null -> "最高可接受股价必须大于 0"
    else -> null
}
