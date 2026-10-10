package com.ashareai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.data.model.PaperPosition
import com.ashareai.app.data.model.Quote
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.navigation.Routes
import com.ashareai.app.ui.theme.changeColor

/** Fusion 研究概览：模拟组合 + 自选行情 + 连接基础摘要。 */
@Composable
fun DashboardScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val dashboardViewModel: com.ashareai.app.ui.DashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.state.collectAsState()
    val marketViewModel = LocalMarketViewModel.current
    val assets by marketViewModel.assets.collectAsState()
    val quotes by marketViewModel.quotes.collectAsState()
    val session by marketViewModel.marketSession.collectAsState()
    val unread by marketViewModel.unreadCount.collectAsState()
    val marketState by marketViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (assets == null) marketViewModel.loadWorkspace()
        if (marketViewModel.marketIndices.value.quotes.isEmpty()) marketViewModel.refreshMarketIndices()
        dashboardViewModel.load()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(
            title = "研究概览",
            unread = unread,
            onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (marketState is ScreenState.Error) {
                item {
                    ErrorBanner((marketState as ScreenState.Error).message) { marketViewModel.retry() }
                }
            }
            session?.let { s ->
                if (s.state?.uppercase() != "OPEN") {
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "当前市场${if (s.state?.uppercase() == "BREAK") "午间休市" else "已收盘"}，行情为最近快照",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            item {
                PnlSummaryCard(
                    positions = assets?.positions ?: emptyList(),
                    totalAssets = assets?.total_assets,
                    quotes = quotes,
                    onClick = { navController.navigate(Routes.ASSETS) },
                )
            }

            item {
                val connection by appViewModel.connection.collectAsState()
                connection?.let { probe ->
                    AppCard {
                        Text("Fusion 研究工作台", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${com.ashareai.app.ui.ConnectionViewModel.label(probe.classification)} · 研究只读",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        probe.message?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow("API", probe.infrastructure.api.dashboardLabel())
                        KeyValueRow("数据库", probe.infrastructure.database.dashboardLabel())
                        KeyValueRow("行情桥", probe.infrastructure.quoteBridge.dashboardLabel())
                        KeyValueRow("新闻桥", probe.infrastructure.newsBridge.dashboardLabel())
                        KeyValueRow("模型网关", probe.infrastructure.modelGateway.dashboardLabel())
                    }
                }
            }

            item {
                AppCard {
                    Text("市场状态", style = MaterialTheme.typography.titleSmall)
                    val marketState = session?.state?.uppercase() ?: "UNKNOWN"
                    KeyValueRow("交易状态", when (marketState) {
                        "OPEN" -> "交易中"
                        "BREAK" -> "午间休市"
                        "CLOSED" -> "已收盘"
                        else -> "未知"
                    })
                    if (dashboardState is ScreenState.Content) {
                        val indices = dashboardState.contentOrNull()?.let { marketViewModel.marketIndices.value.quotes }
                        KeyValueRow("指数摘要", if (indices.isNullOrEmpty()) "按需加载" else indices.take(3).joinToString(" · ") { quote -> "${quote.name ?: quote.symbol} ${quote.change_percent.fmtPercent()}" })
                    }
                }
            }

            item {
                AppCard {
                    Text("最近研究运行", style = MaterialTheme.typography.titleSmall)
                    when (val state = dashboardState) {
                        ScreenState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                        ScreenState.Empty -> Text("暂无研究记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is ScreenState.Error -> ErrorBanner(state.message) { dashboardViewModel.retry() }
                        is ScreenState.Content -> {
                            val run = state.value.latestRun
                            if (run == null) {
                                Text("暂无研究记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                KeyValueRow("状态", statusLabel(run.status))
                                KeyValueRow("研究日", run.trading_date ?: run.requested_date ?: "--")
                                run.phase?.let { KeyValueRow("阶段", it) }
                                run.progress?.let { KeyValueRow("进度", "$it%") }
                                state.value.latestReport?.let { report ->
                                    KeyValueRow("最近报告", report.trading_date ?: "已生成")
                                }
                            }
                        }
                    }
                }
            }

            item {
                AppCard {
                    Text("研究工作台", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "确定性评分负责裁决，AI 只解释研究证据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickEntry("研究运行", Modifier.weight(1f)) { navController.navigate(Routes.RESEARCH) }
                        QuickEntry("AI 诊断", Modifier.weight(1f)) { navController.navigate(Routes.AI_CHAT) }
                    }
                }
            }

            item {
                SectionTitle("观察行情", trailing = {
                    TextButton(onClick = { navController.navigate(Routes.MARKET) }) { Text("全部行情") }
                })
            }

            val watchSymbols = (assets?.watchlist ?: emptyList()).take(6)
            if (watchSymbols.isEmpty()) {
                item { EmptyPlaceholder("暂无观察标的，去行情页添加") }
            } else {
                items(watchSymbols) { symbol ->
                    val quote = quotes[symbol]
                    QuoteRow(
                        symbol = symbol,
                        quote = quote,
                        onClick = { navController.navigate(Routes.stockDetail(symbol)) },
                    )
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item {
                SectionTitle("研究入口")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickEntry("资产观察", Modifier.weight(1f)) { navController.navigate(Routes.ASSETS) }
                    QuickEntry("模拟建议", Modifier.weight(1f)) { navController.navigate(Routes.EXIT_ADVICE) }
                    QuickEntry("研究运行", Modifier.weight(1f)) { navController.navigate(Routes.RESEARCH) }
                }
            }
        }
    }
}

private fun com.ashareai.app.data.InfrastructureAvailability.dashboardLabel(): String = when (this) {
    com.ashareai.app.data.InfrastructureAvailability.Available -> "可用"
    com.ashareai.app.data.InfrastructureAvailability.Unavailable -> "不可用"
    com.ashareai.app.data.InfrastructureAvailability.Unknown -> "未知"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSimple(
    title: String,
    unread: Int = 0,
    onNotifications: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    CompactTopBar(
        title = title,
        navigation = onBack?.let { back ->
            {
                IconButton(onClick = back) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            }
        },
        actions = {
            if (onNotifications != null) {
                BadgedBox(
                    badge = {
                        if (unread > 0) {
                            Badge { Text(if (unread > 99) "99+" else "$unread") }
                        }
                    },
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    IconButton(onClick = onNotifications) {
                        Icon(Icons.Outlined.Notifications, contentDescription = "通知")
                    }
                }
            }
        },
    )
}

@Composable
private fun PnlSummaryCard(
    positions: List<PaperPosition>,
    totalAssets: Double?,
    quotes: Map<String, Quote>,
    onClick: () -> Unit,
) {
    var cost = 0.0
    var marketValue = 0.0
    positions.forEach { p ->
        val price = quotes[p.symbol]?.price ?: p.cost
        cost += p.cost * p.quantity
        marketValue += price * p.quantity
    }
    val pnl = marketValue - cost
    val pnlPct = if (cost > 0) pnl / cost * 100 else null
    val riskCount = positions.count { position ->
        val price = quotes[position.symbol]?.price
        price != null && price < position.cost * 0.92
    }

    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Text("模拟持仓盈亏", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            ChangeText(
                value = pnl,
                text = pnl.fmtSigned(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            ChangeText(value = pnl, text = pnlPct.fmtPercent(), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            MiniStat("持仓市值", marketValue.fmtAmount())
            MiniStat("持仓成本", cost.fmtAmount())
            MiniStat("账户总资金", totalAssets.fmtAmount())
            MiniStat("持仓数", "${positions.size}")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (riskCount == 0) "风险状态：暂无跌破参考止损线" else "风险状态：${riskCount} 个持仓低于参考止损线",
            style = MaterialTheme.typography.labelMedium,
            color = if (riskCount == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
fun QuoteRow(
    symbol: String,
    quote: Quote?,
    onClick: () -> Unit,
    onWatchlistToggle: (() -> Unit)? = null,
) {
    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    quote?.name ?: symbol.substringBefore('.'),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    quote?.price.fmt2(),
                    style = MaterialTheme.typography.titleSmall,
                    color = changeColor(quote?.change_percent),
                )
                ChangeText(
                    value = quote?.change_percent,
                    text = quote?.change_percent.fmtPercent(),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            onWatchlistToggle?.let { toggle ->
                IconButton(onClick = toggle) {
                    Icon(Icons.Outlined.Star, contentDescription = "取消自选", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun QuickEntry(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
