package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.data.model.TradeAdviceMonitorRequest
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.fmtTime
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.util.UUID

/** 自选股买入、卖出与止损的模拟提醒中心。 */
@Composable
fun ExitAdviceScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val quotes by appViewModel.quotes.collectAsState()
    var symbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var monitors by remember { mutableStateOf<List<TradeAdviceMonitor>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try {
            symbols = ApiClient.api.assets().watchlist
            monitors = ApiClient.api.tradeAdviceMonitors()
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally { loading = false }
    }
    LaunchedEffect(Unit) { load(); while (true) { delay(15_000); load() } }
    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "交易建议")
        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }
        if (loading) {
            LoadingBox()
        } else if (symbols.isEmpty()) {
            EmptyPlaceholder("暂无自选股\n请先添加自选股")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TradeAdviceIntro() }
                items(symbols, key = { it }) { symbol ->
                    val monitor = monitors.firstOrNull { it.symbol == symbol }
                    TradeAdviceCard(symbol, quotes[symbol]?.name, monitor) { enabled, buy, sell ->
                        scope.launch {
                            try {
                                val saved = ApiClient.api.saveTradeAdviceMonitor(UUID.randomUUID().toString(), TradeAdviceMonitorRequest(symbol, enabled, buy, sell))
                                monitors = (monitors.filterNot { it.symbol == symbol } + saved).sortedBy { it.symbol }
                            } catch (e: Exception) { error = e.toUserMessage() }
                        }
                    }
                }
            }
        }
    }
}

/** 顶部说明，对齐首页收盘横幅的弱化样式。 */
@Composable
private fun TradeAdviceIntro() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "买入、卖出与止损为模拟建议；交易日 09:30 后生成，命中目标时每 5 分钟重复提醒，不会自动交易。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun TradeAdviceCard(symbol: String, name: String?, monitor: TradeAdviceMonitor?, onSave: (Boolean, Double?, Double?) -> Unit) {
    var buy by remember(monitor?.symbol, monitor?.manual_buy_price) { mutableStateOf(monitor?.manual_buy_price?.toString().orEmpty()) }
    var sell by remember(monitor?.symbol, monitor?.manual_sell_price) { mutableStateOf(monitor?.manual_sell_price?.toString().orEmpty()) }
    val enabled = monitor?.enabled == true
    val displayName = name?.takeIf { it.isNotBlank() }
    AppCard {
        // 标题行：股票名称 + 代码、提醒状态 + 开关
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        displayName?.let { "$it $symbol" } ?: symbol,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    if (enabled) {
                        Spacer(Modifier.width(8.dp))
                        StatusChip(alertLabel(monitor), alertColor(monitor))
                    }
                }
                Text(
                    if (enabled) {
                        monitor.generated_at?.let { "当日建议 ${it.fmtTime(full = true)}" } ?: "下一个交易日 09:30 后生成建议"
                    } else {
                        "未开启自动建议"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = { onSave(it, buy.toDoubleOrNull(), sell.toDoubleOrNull()) })
        }
        if (enabled) {
            // AI 目标价三块
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TargetTile("AI 买入", monitor.ai_buy_price, MaterialTheme.colorScheme.primary)
                TargetTile("AI 卖出", monitor.ai_sell_price, MaterialTheme.colorScheme.onSurface)
                TargetTile("止损", monitor.stop_loss_price, MaterialTheme.colorScheme.error)
            }
            // AI 说明
            val summary = monitor.rationale["summary"]?.let { value ->
                if (value is JsonPrimitive) value.content else value.toString()
            } ?: "监控中"
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            Text("AI 说明", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 自定义价格 + 保存
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(buy, { buy = it }, label = { Text("自定义买入价") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(sell, { sell = it }, label = { Text("自定义卖出价") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onSave(enabled, buy.toDoubleOrNull(), sell.toDoubleOrNull()) }, modifier = Modifier.fillMaxWidth()) { Text("保存自定义价格") }
    }
}

/** 目标价小卡片：AI 买入 / AI 卖出 / 止损。 */
@Composable
private fun RowScope.TargetTile(label: String, price: Double?, color: Color) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.weight(1f),
    ) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                price?.let { "¥ %.2f".format(it) } ?: "—",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

private fun alertLabel(monitor: TradeAdviceMonitor?): String {
    if (monitor?.enabled != true) return "未开启"
    val alerts = monitor.last_alert_types
    return when {
        "STOP_LOSS_TRIGGERED" in alerts -> "止损触发"
        "SELL_TARGET_HIT" in alerts -> "卖出目标命中"
        "BUY_TARGET_HIT" in alerts -> "买入目标命中"
        else -> "监控中"
    }
}

@Composable
private fun alertColor(monitor: TradeAdviceMonitor?): Color {
    val alerts = monitor?.last_alert_types ?: emptyList()
    return when {
        "STOP_LOSS_TRIGGERED" in alerts -> MaterialTheme.colorScheme.error
        "SELL_TARGET_HIT" in alerts || "BUY_TARGET_HIT" in alerts -> Color(0xFFFFA000)
        else -> statusColor("ACTIVE")
    }
}
