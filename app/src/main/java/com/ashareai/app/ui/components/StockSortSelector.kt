package com.ashareai.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** 可用于研究个股和候选池的客户端排序选项。 */
enum class StockSortOption(val label: String) {
    SCORE_DESC("综合评分：高到低"),
    SCORE_ASC("综合评分：低到高"),
    RANK_ASC("原始排名：靠前优先"),
    NAME_ASC("名称：A-Z"),
}

/**
 * 按客户端选择的规则排序股票。评分缺失的记录始终排在有评分的记录之后，
 * 并使用原始排名和股票代码保证排序稳定。
 */
fun <T> List<T>.sortedForStockDisplay(
    option: StockSortOption,
    scoreOf: (T) -> Double?,
    rankOf: (T) -> Int?,
    nameOf: (T) -> String?,
    symbolOf: (T) -> String,
): List<T> = when (option) {
    StockSortOption.SCORE_DESC -> sortedWith(
        compareByDescending<T> { scoreOf(it) ?: Double.NEGATIVE_INFINITY }
            .thenBy { rankOf(it) ?: Int.MAX_VALUE }
            .thenBy(symbolOf),
    )
    StockSortOption.SCORE_ASC -> sortedWith(
        compareBy<T> { scoreOf(it) == null }
            .thenBy { scoreOf(it) ?: 0.0 }
            .thenBy { rankOf(it) ?: Int.MAX_VALUE }
            .thenBy(symbolOf),
    )
    StockSortOption.RANK_ASC -> sortedWith(
        compareBy<T> { rankOf(it) == null }
            .thenBy { rankOf(it) ?: Int.MAX_VALUE }
            .thenByDescending { scoreOf(it) ?: Double.NEGATIVE_INFINITY }
            .thenBy(symbolOf),
    )
    StockSortOption.NAME_ASC -> sortedWith(
        compareBy<T> { (nameOf(it) ?: symbolOf(it)).lowercase() }
            .thenBy(symbolOf),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockSortSelector(
    selected: StockSortOption,
    onSelected: (StockSortOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { androidx.compose.material3.Text("排序方式") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            StockSortOption.entries.forEach { option ->
                DropdownMenuItem(
                    text = { androidx.compose.material3.Text(option.label) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    },
                )
            }
        }
    }
}
