package com.ashareai.app.ui.screens

import com.ashareai.app.ui.components.GlassFilterChip as FilterChip
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ashareai.app.data.KlinePeriod
import com.ashareai.app.data.KlineRange
import com.ashareai.app.data.model.AssetStateRequest
import com.ashareai.app.data.model.Quote
import com.ashareai.app.data.sameSymbol
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.KlineViewModel
import com.ashareai.app.ui.ScreenState
import com.ashareai.app.ui.components.CandlestickChart
import com.ashareai.app.ui.components.KlineAdjustment
import com.ashareai.app.ui.components.KlineInterval
import com.ashareai.app.ui.components.SubChart
import com.ashareai.app.ui.components.EmptyPlaceholder
import com.ashareai.app.ui.fmt2
import com.ashareai.app.ui.fmtAmount
import com.ashareai.app.ui.fmtPercent
import com.ashareai.app.ui.fmtSigned
import com.ashareai.app.ui.fmtTime
import com.ashareai.app.ui.fmtVolume
import com.ashareai.app.ui.theme.BackdropLiquidGlassSurface
import com.ashareai.app.ui.theme.changeColor

@Composable
fun StockDetailScreen(appViewModel: AppViewModel, navController: NavHostController, symbol: String) {
    val klineViewModel: KlineViewModel = viewModel()
    val klineState by klineViewModel.state.collectAsState()
    val requestCount by klineViewModel.requestCount.collectAsState()
    val marketViewModel = com.ashareai.app.ui.LocalMarketViewModel.current
    val quotes by marketViewModel.quotes.collectAsState()
    val assets by marketViewModel.assets.collectAsState()
    var fetchedQuote by remember(symbol) { mutableStateOf<Quote?>(null) }
    val quote = quotes[symbol] ?: fetchedQuote
    var interval by rememberSaveable(symbol) { mutableStateOf(KlineInterval.DAY) }
    var range by rememberSaveable(symbol) { mutableStateOf(KlineRange.MONTH_3) }
    var adjustment by rememberSaveable(symbol) { mutableStateOf(KlineAdjustment.BACKWARD) }
    var subChart by rememberSaveable(symbol) { mutableStateOf(SubChart.VOLUME) }
    var savingWatchlist by remember(symbol) { mutableStateOf(false) }
    var watchlistError by remember(symbol) { mutableStateOf<String?>(null) }
    val content = (klineState as? ScreenState.Content)?.value
    val bars = content?.bars.orEmpty()
    val loading = klineState is ScreenState.Loading
    val error = (klineState as? ScreenState.Error)?.message
    val inWatchlist = assets?.watchlist.orEmpty().any { sameSymbol(it, symbol) }

    LaunchedEffect(symbol, interval, range, adjustment) { klineViewModel.load(symbol, interval.period, range, adjustment.apiValue) }
    LaunchedEffect(symbol) { marketViewModel.refreshQuote(symbol) { refreshed, _ -> fetchedQuote = refreshed } }

    Column(Modifier.fillMaxSize()) {
        DetailTopBar(quote, inWatchlist, { navController.popBackStack() },
            { marketViewModel.refreshQuote(symbol) { refreshed, _ -> fetchedQuote = refreshed } },
            {
                val current = assets
                if (current != null) {
                    val next = if (inWatchlist) current.watchlist.filterNot { sameSymbol(it, symbol) } else current.watchlist + symbol
                    savingWatchlist = true
                    marketViewModel.saveAssets(AssetStateRequest(next, current.positions, current.total_assets, current.exit_monitor_enabled, current.default_profit_trigger, current.stop_loss_monitor_enabled, current.buy_monitor_enabled, current.market_refresh_interval_seconds)) { message ->
                        savingWatchlist = false
                        watchlistError = message
                    }
                }
            }, savingWatchlist)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QuoteHeader(quote, watchlistError)
                KlineToolbar(interval, adjustment, subChart, { selected -> interval = selected; range = selected.defaultRange }, { adjustment = it }, { subChart = it })
                Surface(Modifier.fillMaxWidth().height(430.dp).testTag("stock.detail.chart.surface"), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    when {
                        loading && bars.isEmpty() -> LoadingBox(Modifier.fillMaxSize(), "正在加载 ${adjustment.label} K 线…")
                        error != null && bars.isEmpty() -> ErrorBanner(error) { klineViewModel.load(symbol, interval.period, range, adjustment.apiValue) }
                        bars.isEmpty() -> EmptyPlaceholder("暂无 K 线数据", Modifier.fillMaxSize())
                        else -> CandlestickChart(bars, interval.period, subChart, if (range != KlineRange.YEAR_1) { { range = KlineRange.entries[(KlineRange.entries.indexOf(range) + 1).coerceAtMost(KlineRange.entries.lastIndex)] } } else null, Modifier.fillMaxSize().padding(12.dp))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${bars.size} 根 · ${requestCount} 段 · ${adjustment.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error != null && bars.isNotEmpty()) Text(error, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Text("数据仅用于研究、回测和模拟，不构成交易指令。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailTopBar(quote: Quote?, inWatchlist: Boolean, onBack: () -> Unit, onRefresh: () -> Unit, onToggleWatchlist: () -> Unit, savingWatchlist: Boolean) {
    BackdropLiquidGlassSurface(Modifier.fillMaxWidth().statusBarsPadding(), MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.testTag("stock.detail.back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) { Text(quote?.name ?: "行情详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text(quote?.symbol ?: "--", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onRefresh, Modifier.testTag("stock.detail.refresh")) { Icon(Icons.Outlined.Refresh, "刷新行情") }
            IconButton(onClick = onToggleWatchlist, enabled = !savingWatchlist, modifier = Modifier.testTag("stock.detail.watchlist")) { Icon(if (inWatchlist) Icons.Outlined.Star else Icons.Outlined.StarBorder, if (inWatchlist) "取消自选" else "加入自选") }
        }
    }
}

@Composable
private fun QuoteHeader(quote: Quote?, error: String?) {
    Surface(Modifier.fillMaxWidth().testTag("stock.detail.quote"), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(quote?.price.fmt2(), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = changeColor(quote?.change_percent))
                Spacer(Modifier.width(10.dp)); Text(quote?.change.fmtSigned(), style = MaterialTheme.typography.titleMedium, color = changeColor(quote?.change_percent)); Spacer(Modifier.width(6.dp)); Text(quote?.change_percent.fmtPercent(), style = MaterialTheme.typography.titleMedium, color = changeColor(quote?.change_percent))
            }
            StatGrid(listOf("今开" to quote?.open.fmt2(), "最高" to quote?.high.fmt2(), "最低" to quote?.low.fmt2(), "昨收" to quote?.previous_close.fmt2(), "成交量" to quote?.volume.fmtVolume(), "成交额" to quote?.amount.fmtAmount(), "来源" to (quote?.status?.source ?: "--"), "采集" to quote?.status?.collected_at.fmtTime()))
            val status = quote?.status
            if (status?.stale == true || status?.delayed == true || status?.message != null) Text(status.message ?: if (status.stale) "数据已过期，请刷新后再参考" else "当前为延迟行情", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun StatGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { items.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { (label, value) -> Column(Modifier.weight(1f)) { Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium) } }; repeat(4 - row.size) { Spacer(Modifier.weight(1f)) } } } }
}

@Composable
private fun KlineToolbar(interval: KlineInterval, adjustment: KlineAdjustment, subChart: SubChart, onInterval: (KlineInterval) -> Unit, onAdjustment: (KlineAdjustment) -> Unit, onSubChart: (SubChart) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BackdropLiquidGlassSurface(Modifier.fillMaxWidth(), MaterialTheme.shapes.medium) { Row(Modifier.horizontalScroll(rememberScrollState()).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { KlineInterval.entries.forEach { option -> FilterChip(option == interval, { onInterval(option) }, label = { Text(option.label) }, modifier = Modifier.testTag("stock.detail.interval.${option.name}")) } } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) { KlineAdjustment.entries.forEach { option -> FilterChip(option == adjustment, { onAdjustment(option) }, label = { Text(option.label) }, modifier = Modifier.testTag("stock.detail.adjustment.${option.name}")) } }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { SubChart.entries.forEach { option -> FilterChip(option == subChart, { onSubChart(option) }, label = { Text(option.label) }, modifier = Modifier.testTag("stock.detail.indicator.${option.name}")) } }
        }
    }
}

private val KlineInterval.defaultRange: KlineRange get() = when (this) { KlineInterval.INTRADAY -> KlineRange.DAY_1; KlineInterval.FIVE_DAYS -> KlineRange.DAY_5; KlineInterval.DAY -> KlineRange.MONTH_3; KlineInterval.WEEK, KlineInterval.MONTH -> KlineRange.YEAR_1 }

@Composable private fun LoadingBox(modifier: Modifier, text: String) { Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { CircularProgressIndicator(); Spacer(Modifier.height(10.dp)); Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable private fun ErrorBanner(message: String, onRetry: () -> Unit) { Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) { Text(message, color = MaterialTheme.colorScheme.error); Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = onRetry, modifier = Modifier.testTag("stock.detail.retry")) { Text("重新加载") } } }
