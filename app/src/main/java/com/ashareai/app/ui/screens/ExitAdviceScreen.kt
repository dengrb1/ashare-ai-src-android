package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.data.model.TradeAdviceMonitorRequest
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

/** Unified watchlist buy, sell and stop-loss suggestion centre. */
@Composable
fun ExitAdviceScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var symbols by remember { mutableStateOf<List<String>>(emptyList()) }
    var monitors by remember { mutableStateOf<List<TradeAdviceMonitor>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    suspend fun load() {
        try { symbols = ApiClient.api.assets().watchlist; monitors = ApiClient.api.tradeAdviceMonitors(); error = null }
        catch (e: Exception) { error = e.toUserMessage() }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { load(); while (true) { delay(15_000); load() } }
    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "交易建议")
        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }
        if (loading) LoadingBox() else if (symbols.isEmpty()) EmptyPlaceholder("暂无自选股\n请先添加自选股") else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("买入、卖出与止损仅作模拟提醒；交易日 09:30 后生成，命中时每 5 分钟重复提醒。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(symbols, key = { it }) { symbol ->
                val monitor = monitors.firstOrNull { it.symbol == symbol }
                TradeAdviceCard(symbol, monitor) { enabled, buy, sell ->
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

@Composable
private fun TradeAdviceCard(symbol: String, monitor: TradeAdviceMonitor?, onSave: (Boolean, Double?, Double?) -> Unit) {
    var buy by remember(monitor?.symbol, monitor?.manual_buy_price) { mutableStateOf(monitor?.manual_buy_price?.toString().orEmpty()) }
    var sell by remember(monitor?.symbol, monitor?.manual_sell_price) { mutableStateOf(monitor?.manual_sell_price?.toString().orEmpty()) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(symbol, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Switch(checked = monitor?.enabled == true, onCheckedChange = { onSave(it, buy.toDoubleOrNull(), sell.toDoubleOrNull()) }) }
        if (monitor?.enabled == true) {
            Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PriceColumn("AI 买入", monitor.ai_buy_price); PriceColumn("AI 卖出", monitor.ai_sell_price); PriceColumn("止损", monitor.stop_loss_price)
            }
            Text(monitor.rationale["summary"]?.toString() ?: "监控中", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp)); OutlinedTextField(buy, { buy = it }, label = { Text("自定义买入价") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp)); OutlinedTextField(sell, { sell = it }, label = { Text("自定义卖出价") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp)); Button(onClick = { onSave(monitor?.enabled == true, buy.toDoubleOrNull(), sell.toDoubleOrNull()) }) { Text("保存自定义价格") }
    }
}

@Composable private fun PriceColumn(label: String, price: Double?) { Column { Text(label, style = MaterialTheme.typography.labelSmall); Text(price?.let { "¥ %.2f".format(it) } ?: "—", style = MaterialTheme.typography.bodyMedium) } }
