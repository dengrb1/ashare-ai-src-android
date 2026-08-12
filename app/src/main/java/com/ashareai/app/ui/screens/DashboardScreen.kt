package com.ashareai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.model.PaperPosition
import com.ashareai.app.data.model.Quote
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.navigation.Routes
import com.ashareai.app.ui.theme.changeColor

/** 首页仪表盘：持仓盈亏总览 + 自选行情速览 + 收盘提示。 */
@Composable
fun DashboardScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val assets by appViewModel.assets.collectAsState()
    val quotes by appViewModel.quotes.collectAsState()
    val session by appViewModel.marketSession.collectAsState()
    val unread by appViewModel.unreadCount.collectAsState()

    LaunchedEffect(Unit) {
        if (assets == null) appViewModel.loadAssets()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(
            title = "霁衡智研",
            unread = unread,
            onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                SectionTitle("自选速览", trailing = {
                    TextButton(onClick = { navController.navigate(Routes.MARKET) }) { Text("全部行情") }
                })
            }

            val watchSymbols = (assets?.watchlist ?: emptyList()).take(6)
            if (watchSymbols.isEmpty()) {
                item { EmptyPlaceholder("暂无自选股，去行情页添加") }
            } else {
                items(watchSymbols) { symbol ->
                    val quote = quotes[symbol]
                    QuoteRow(symbol = symbol, quote = quote) {
                        navController.navigate(Routes.stockDetail(symbol))
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item {
                SectionTitle("快捷入口")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickEntry("自选持仓", Modifier.weight(1f)) { navController.navigate(Routes.ASSETS) }
                    QuickEntry("卖出建议", Modifier.weight(1f)) { navController.navigate(Routes.EXIT_ADVICE) }
                    QuickEntry("每日研究", Modifier.weight(1f)) { navController.navigate(Routes.RESEARCH) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBarSimple(
    title: String,
    unread: Int = 0,
    onNotifications: (() -> Unit)? = null,
) {
    CompactTopBar(
        title = title,
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
fun QuoteRow(symbol: String, quote: Quote?, onClick: () -> Unit) {
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
