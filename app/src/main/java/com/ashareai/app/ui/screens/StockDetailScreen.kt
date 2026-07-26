package com.ashareai.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.KlinePeriod
import com.ashareai.app.data.KlineRange
import com.ashareai.app.data.KlineRepository
import com.ashareai.app.data.model.AssetStateRequest
import com.ashareai.app.data.model.KlineBar
import com.ashareai.app.data.model.Quote
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.theme.changeColor
import kotlinx.coroutines.launch

/** 个股详情：报价 + K线（周期/副图切换）+ 明细字段。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockDetailScreen(appViewModel: AppViewModel, navController: NavHostController, symbol: String) {
    val quotes by appViewModel.quotes.collectAsState()
    val assets by appViewModel.assets.collectAsState()
    val scope = rememberCoroutineScope()
    var fetchedQuote by remember(symbol) { mutableStateOf<Quote?>(null) }
    val quote = quotes[symbol] ?: fetchedQuote

    var period by remember { mutableStateOf(KlinePeriod.DAY) }
    var range by remember { mutableStateOf(KlineRange.MONTH_3) }
    var subChart by remember { mutableStateOf(SubChart.VOLUME) }
    var bars by remember { mutableStateOf<List<KlineBar>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var requestCount by remember { mutableIntStateOf(0) }
    var retryKey by remember { mutableIntStateOf(0) }
    val repository = remember { KlineRepository(ApiClient.api) }

    val inWatchlist = symbol in (assets?.watchlist ?: emptyList())

    LaunchedEffect(symbol, period, range, retryKey) {
        loading = true
        error = null
        requestCount = 0
        try {
            val result = repository.load(symbol, period, range) { requestCount = it }
            bars = result.bars
            requestCount = result.requestCount
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(symbol) {
        // 进入详情立即拉一次最新报价
        try {
            fetchedQuote = ApiClient.api.quote(symbol, refresh = true)
            appViewModel.refreshQuotes()
        } catch (_: Exception) {
        }
    }

    Column(Modifier.fillMaxSize()) {
        CompactTopBar(
            title = listOfNotNull(quote?.name, symbol).distinct().joinToString("  "),
            navigation = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = {
                    val current = assets ?: return@IconButton
                    val newList = if (inWatchlist) current.watchlist - symbol else current.watchlist + symbol
                    appViewModel.saveAssets(
                        AssetStateRequest(
                            watchlist = newList,
                            positions = current.positions,
                            total_assets = current.total_assets,
                            exit_monitor_enabled = current.exit_monitor_enabled,
                            default_profit_trigger = current.default_profit_trigger,
                            stop_loss_monitor_enabled = current.stop_loss_monitor_enabled,
                            buy_monitor_enabled = current.buy_monitor_enabled,
                            market_refresh_interval_seconds = current.market_refresh_interval_seconds,
                        )
                    ) { }
                }) {
                    Icon(
                        if (inWatchlist) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                        contentDescription = if (inWatchlist) "取消自选" else "加入自选",
                        tint = if (inWatchlist) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    )
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 报价头
            AppCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        quote?.price.fmt2(),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = changeColor(quote?.change_percent),
                    )
                    Spacer(Modifier.width(10.dp))
                    ChangeText(quote?.change, quote?.change.fmtSigned(), MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.width(6.dp))
                    ChangeText(quote?.change_percent, quote?.change_percent.fmtPercent(), MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailStat("今开", quote?.open.fmt2())
                    DetailStat("最高", quote?.high.fmt2())
                    DetailStat("最低", quote?.low.fmt2())
                    DetailStat("昨收", quote?.previous_close.fmt2())
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailStat("成交量", quote?.volume.fmtVolume())
                    DetailStat("成交额", quote?.amount.fmtAmount())
                    DetailStat(
                        "数据源",
                        quote?.status?.source ?: "--",
                    )
                    DetailStat("采集", quote?.status?.collected_at.fmtTime())
                }
                if (quote?.status?.stale == true || quote?.status?.delayed == true) {
                    Spacer(Modifier.height(8.dp))
                    TagPill(if (quote.status.stale) "数据延迟（stale）" else "延迟行情", MaterialTheme.colorScheme.error)
                }
            }

            // K线工具区
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KlinePeriod.entries.forEach { item ->
                        FilterChip(
                            selected = period == item,
                            onClick = { period = item },
                            label = { Text(item.label) },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    KlineRange.entries.forEach { item ->
                        FilterChip(
                            selected = range == item,
                            onClick = { range = item },
                            label = { Text(item.label) },
                        )
                    }
                }
                if (loading) {
                    Column(Modifier.height(320.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingBox(Modifier.weight(1f))
                        Text("正在加载第 ${requestCount + 1} 段", style = MaterialTheme.typography.labelSmall)
                    }
                } else if (error != null) {
                    ErrorBanner(error!!) {
                        retryKey += 1
                    }
                } else {
                    CandlestickChart(
                        bars = bars.takeLast(240),
                        subChart = subChart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(340.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SubChart.entries.forEach { sc ->
                        FilterChip(
                            selected = subChart == sc,
                            onClick = { subChart = sc },
                            label = { Text(sc.label) },
                        )
                    }
                }
                Text(
                    "已加载 ${bars.size} 根 · ${requestCount} 段 · 后复权(hfq)" +
                        if (bars.size > 240) " · 图表显示最近 240 根" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DetailStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
