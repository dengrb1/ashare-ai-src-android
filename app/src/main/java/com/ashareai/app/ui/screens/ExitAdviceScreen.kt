package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.ExitAdvice
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 卖出建议：15 秒轮询刷新，展示 AI 分档退出方案。 */
@Composable
fun ExitAdviceScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ExitAdvice>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        try {
            items = ApiClient.api.exitAdvice(limit = 50)
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
        while (true) {
            delay(15_000)
            load()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "卖出建议")

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }

        if (loading) {
            LoadingBox()
        } else if (items.isEmpty()) {
            EmptyPlaceholder("暂无卖出建议\n可在自选持仓页对持仓发起「退出研究」")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.advice_id }) { advice ->
                    ExitAdviceCard(advice)
                }
            }
        }
    }
}

private fun actionLabel(action: String?): Pair<String, androidx.compose.ui.graphics.Color> = when (action?.uppercase()) {
    "SELL" -> "建议卖出" to androidx.compose.ui.graphics.Color(0xFFE53935)
    "REDUCE" -> "建议减仓" to androidx.compose.ui.graphics.Color(0xFFFFA000)
    "HOLD" -> "建议持有" to androidx.compose.ui.graphics.Color(0xFF00A86B)
    else -> (action ?: "分析中") to androidx.compose.ui.graphics.Color(0xFF9E9E9E)
}

@Composable
private fun ExitAdviceCard(advice: ExitAdvice) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(advice.symbol, style = MaterialTheme.typography.titleSmall)
                Text(
                    advice.decision_at.fmtTime(full = true),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val (label, color) = actionLabel(advice.action)
            TagPill(label, color)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("当前价", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(advice.current_price.fmt2(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            }
            Column {
                Text("浮动盈亏", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ChangeText(advice.unrealized_profit, advice.unrealized_profit.fmtSigned(), MaterialTheme.typography.bodySmall)
            }
            Column {
                Text("触发类型", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(triggerLabel(advice.trigger_type), style = MaterialTheme.typography.bodySmall)
            }
            Column {
                Text("状态", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(statusLabel(advice.status), style = MaterialTheme.typography.bodySmall)
            }
        }

        // 分档卖出阶梯（result JSON 里的 tiers/levels/ladder）
        val tiers = extractTiers(advice.result)
        if (tiers.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text("分档卖出方案", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            tiers.forEach { tier ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("目标价 ${tier.first}", style = MaterialTheme.typography.bodySmall)
                    Text(tier.second, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        advice.model_name?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                "模型 $it${if (advice.cache_hit) " · 缓存命中" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun triggerLabel(type: String?): String = when (type?.uppercase()) {
    "PRICE" -> "价格触发"
    "PROFIT_AMOUNT" -> "浮盈触发"
    "MANUAL" -> "手动发起"
    "STOP_LOSS" -> "止损触发"
    else -> type ?: "--"
}

/** 从 result JSON 里尽力解析出（目标价 → 描述）列表，未知结构时返回空。 */
private fun extractTiers(result: kotlinx.serialization.json.JsonElement?): List<Pair<String, String>> {
    val obj = result as? JsonObject ?: return emptyList()
    val arr = (obj["tiers"] ?: obj["levels"] ?: obj["ladder"] ?: obj["sell_levels"]) as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val tier = el as? JsonObject ?: return@mapNotNull null
        val price = (tier["target_price"] ?: tier["price"])?.let { (it as? JsonPrimitive)?.content } ?: return@mapNotNull null
        val qty = (tier["quantity"] ?: tier["shares"])?.let { (it as? JsonPrimitive)?.content }
        val note = (tier["rationale"] ?: tier["reason"] ?: tier["note"])?.let { (it as? JsonPrimitive)?.content }
        price to listOfNotNull(qty?.let { "数量 $it" }, note).joinToString(" · ").ifBlank { "—" }
    }
}
