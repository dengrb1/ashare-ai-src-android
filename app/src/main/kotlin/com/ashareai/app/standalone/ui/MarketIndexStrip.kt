package com.ashareai.app.standalone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.standalone.domain.MarketQuote

@Composable
internal fun MarketIndexStrip(
    quotes: List<MarketQuote>,
    loading: Boolean,
) {
    if (quotes.isEmpty() && !loading) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("大盘指数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(
                "实时展示；研究评分使用冻结指数环境",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (quotes.isEmpty()) {
            Text("正在加载大盘指数…", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                quotes.forEach { quote ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(1f / quotes.size.coerceAtLeast(1)),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(indexDisplayName(quote.symbol), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            Text(
                                quote.lastPrice?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "--",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                quote.changePercent?.let { signedIndexNumber(it) + "%" } ?: "--",
                                style = MaterialTheme.typography.labelSmall,
                                color = indexChangeColor(quote.changePercent),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun indexDisplayName(symbol: String): String = when (symbol) {
    "000300" -> "沪深300"
    "000905" -> "中证500"
    "000852" -> "中证1000"
    else -> symbol
}

private fun signedIndexNumber(value: Double): String = String.format(
    java.util.Locale.US,
    if (value >= 0.0) "+%.2f" else "%.2f",
    value,
)

@Composable
private fun indexChangeColor(value: Double?) = when {
    value == null -> MaterialTheme.colorScheme.onSurfaceVariant
    value > 0.0 -> androidx.compose.ui.graphics.Color(0xFFC62828)
    value < 0.0 -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
