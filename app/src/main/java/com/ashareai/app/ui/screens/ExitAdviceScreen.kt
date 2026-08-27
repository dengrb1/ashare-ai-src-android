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
import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.data.model.TradeAdviceMonitorRequest
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.LocalMarketViewModel
import com.ashareai.app.ui.ExitAdviceViewModel
import com.ashareai.app.ui.ScreenState
import com.ashareai.app.ui.fmtTime
import com.ashareai.app.ui.components.*
import kotlinx.serialization.json.JsonPrimitive

/** 观察标的的模拟建议与风险提醒中心。 */
@Composable
fun ExitAdviceScreen(appViewModel: AppViewModel) {
    val exitAdviceViewModel: ExitAdviceViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val exitAdviceState by exitAdviceViewModel.state.collectAsState()
    val marketViewModel = LocalMarketViewModel.current
    val quotes by marketViewModel.quotes.collectAsState()
    val content = when (val state = exitAdviceState) {
        is ScreenState.Content -> state.value
        is ScreenState.Error -> state.previous
        ScreenState.Loading, ScreenState.Empty -> null
    }
    val symbols = content?.symbols.orEmpty()
    val monitors = content?.monitors.orEmpty()
    val loading = exitAdviceState is ScreenState.Loading
    val error = (exitAdviceState as? ScreenState.Error)?.message
    // The connected app owns the single foreground market polling loop. This
    // page loads on entry and refreshes explicitly so it cannot keep running in
    // the background while the activity is stopped.
    LaunchedEffect(Unit) { exitAdviceViewModel.load() }
    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "模拟建议")
        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { exitAdviceViewModel.retry() } } }
        if (loading) {
            LoadingBox()
        } else if (symbols.isEmpty()) {
            EmptyPlaceholder("暂无观察标的\n请先添加观察标的")
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TradeAdviceIntro() }
                items(symbols, key = { it }) { symbol ->
                    val monitor = monitors.firstOrNull { it.symbol == symbol }
                    TradeAdviceCard(symbol, quotes[symbol]?.name, monitor) { enabled, buy, sell ->
                        exitAdviceViewModel.save(
                            TradeAdviceMonitorRequest(symbol, enabled, buy, sell),
                        )
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
            "入场、退出与止损为模拟建议；交易日 09:30 后生成，命中目标时每 5 分钟重复提醒，仅供研究参考。",
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
                TargetTile("AI 入场", monitor.ai_buy_price, MaterialTheme.colorScheme.primary)
                TargetTile("AI 退出", monitor.ai_sell_price, MaterialTheme.colorScheme.onSurface)
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
            OutlinedTextField(buy, { buy = it }, label = { Text("自定义入场价") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(sell, { sell = it }, label = { Text("自定义退出价") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { onSave(enabled, buy.toDoubleOrNull(), sell.toDoubleOrNull()) }, modifier = Modifier.fillMaxWidth()) { Text("保存自定义价格") }
    }
}

/** 目标价小卡片：AI 入场 / AI 退出 / 止损。 */
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
        "SELL_TARGET_HIT" in alerts -> "退出目标命中"
        "BUY_TARGET_HIT" in alerts -> "入场目标命中"
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
