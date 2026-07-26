package com.ashareai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.data.model.Portfolio
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/** 模拟组合：权重条形展示 + 持仓明细。 */
@Composable
fun PortfolioScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(todayTradingDate()) }
    var portfolio by remember { mutableStateOf<Portfolio?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            portfolio = ApiClient.api.portfolio(date)
        } catch (e: Exception) {
            error = e.toUserMessage()
            portfolio = null
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "模拟组合")

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = date, onValueChange = { date = it },
                label = { Text("交易日") }, singleLine = true, modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { scope.launch { load() } }) { Text("查询") }
        }

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }

        if (loading) {
            LoadingBox()
        } else {
            val p = portfolio
            if (p == null || (p.positions.isEmpty() && p.message == null)) {
                EmptyPlaceholder("该交易日暂无组合数据")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        AppCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("组合状态", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(statusLabel(p.status), style = MaterialTheme.typography.titleSmall)
                                }
                                Column {
                                    Text("持仓数", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${p.positions.size}", style = MaterialTheme.typography.titleSmall)
                                }
                                Column {
                                    Text("预计换手", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(p.expected_turnover?.let { "${(it * 100).fmt2()}%" } ?: "--", style = MaterialTheme.typography.titleSmall)
                                }
                                Column {
                                    Text("现金权重", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(p.cash_weight?.let { "${(it * 100).fmt2()}%" } ?: "--", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            if (p.observation_only || p.research_only) {
                                Spacer(Modifier.height(8.dp))
                                TagPill(
                                    if (p.observation_only) "观察模式" else "仅研究",
                                    androidx.compose.ui.graphics.Color(0xFFFFA000),
                                )
                            }
                            p.message?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    items(p.positions) { pos ->
                        PortfolioPositionCard(pos)
                    }
                }
            }
        }
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content

private fun JsonObject.num(key: String): Double? =
    (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()

@Composable
private fun PortfolioPositionCard(pos: JsonObject) {
    val symbol = pos.str("symbol") ?: "--"
    val name = pos.str("name")
    val weight = pos.num("target_weight") ?: pos.num("weight")
    val industry = pos.str("industry_name") ?: pos.str("industry_code")
    val action = pos.str("action") ?: pos.str("rebalance_action")

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name ?: symbol, style = MaterialTheme.typography.titleSmall)
                Text(symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    weight?.let { "${(it * 100).fmt2()}%" } ?: "--",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text("目标权重", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        weight?.let { w ->
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(w.toFloat().coerceIn(0f, 1f))
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        if (industry != null || action != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                industry?.let { TagPill(it, MaterialTheme.colorScheme.secondary) }
                action?.let { TagPill(it) }
            }
        }
    }
}
