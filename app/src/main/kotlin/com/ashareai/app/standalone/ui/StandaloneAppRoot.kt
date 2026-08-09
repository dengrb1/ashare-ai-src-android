package com.ashareai.app.standalone.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.standalone.data.ai.AiProviderDraft
import com.ashareai.app.standalone.domain.AlertKind
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.NotificationPriority
import com.ashareai.app.standalone.domain.ResearchRunState
import com.ashareai.app.standalone.domain.ResearchScope
import java.text.DateFormat
import java.util.Date
import kotlin.math.max
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun StandaloneAppRoot(
    viewModel: StandaloneViewModel,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
) {
    val incomingRoute by pendingRoute.collectAsState()
    val message by viewModel.message.collectAsState()
    val unread by viewModel.unreadNotifications.collectAsState()
    val snackbarHost = remember { SnackbarHostState() }
    var route by rememberSaveable { mutableStateOf("home") }
    LaunchedEffect(incomingRoute) {
        if (incomingRoute in routes) {
            route = incomingRoute.orEmpty()
            onRouteConsumed()
        }
    }
    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Text(
                    text = routeTitle(route),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "超级岛 A股（独立版） · 本地优先",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = route == "home",
                    onClick = { route = "home" },
                    icon = { Icon(Icons.Outlined.Home, null) },
                    label = { Text("主页") },
                )
                NavigationBarItem(
                    selected = route == "market",
                    onClick = { route = "market"; viewModel.loadCatalog() },
                    icon = { Icon(Icons.Outlined.Search, null) },
                    label = { Text("行情") },
                )
                NavigationBarItem(
                    selected = route == "research",
                    onClick = { route = "research" },
                    icon = { Icon(Icons.Outlined.QueryStats, null) },
                    label = { Text("研究") },
                )
                NavigationBarItem(
                    selected = route == "notifications",
                    onClick = { route = "notifications" },
                    icon = { Icon(Icons.Outlined.Notifications, null) },
                    label = { Text(if (unread > 0) "通知 " + unread else "通知") },
                )
                NavigationBarItem(
                    selected = route == "settings",
                    onClick = { route = "settings" },
                    icon = { Icon(Icons.Outlined.Settings, null) },
                    label = { Text("设置") },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when (route) {
            "home" -> HomeScreen(viewModel, Modifier.padding(padding)) { route = it }
            "market" -> MarketScreen(viewModel, Modifier.padding(padding)) { route = it }
            "assets" -> AssetsScreen(viewModel, Modifier.padding(padding))
            "alerts" -> AlertsScreen(viewModel, Modifier.padding(padding))
            "research" -> ResearchScreen(viewModel, Modifier.padding(padding))
            "reports" -> ReportsScreen(viewModel, Modifier.padding(padding))
            "candidates" -> CandidatesScreen(viewModel, Modifier.padding(padding))
            "portfolio" -> PortfolioScreen(viewModel, Modifier.padding(padding))
            "exit" -> ExitResearchScreen(viewModel, Modifier.padding(padding))
            "notifications" -> NotificationsScreen(viewModel, Modifier.padding(padding))
            "chat" -> ChatScreen(viewModel, Modifier.padding(padding))
            "settings" -> SettingsScreen(viewModel, Modifier.padding(padding))
            else -> HomeScreen(viewModel, Modifier.padding(padding)) { route = it }
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: StandaloneViewModel,
    modifier: Modifier,
    navigate: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val holdingsWithQuotes by viewModel.holdingsWithQuotes.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val runs by viewModel.researchRuns.collectAsState()
    ScreenColumn(modifier) {
        if (settings.firstRun) {
            InfoCard(
                title = "欢迎使用本地独立版",
                text = "没有登录和服务器地址。持仓、自选、提醒、报告与 AI 会话都保存于本机；公开行情会标记来源和更新时间。",
            ) {
                Button(onClick = viewModel::completeFirstRun) { Text("我知道了") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { navigate("settings") }) { Text("完成设置引导") }
            }
        }
        InfoCard(
            title = "持仓监控",
            text = if (settings.monitoringEnabled) {
                "交易时段每 " + settings.monitoringIntervalSeconds + " 秒检查实际持仓；非交易时段无网络轮询。"
            } else {
                "已关闭。"
            },
        ) {
            Button(onClick = viewModel::refreshHoldingQuotes) { Text("手动刷新持仓") }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { navigate("assets") }) { Text("编辑持仓") }
        }
        SectionTitle("持仓与缓存报价")
        if (holdingsWithQuotes.isEmpty()) {
            Text("尚未添加持仓。添加后才会启动后台行情监控。")
        }
        holdingsWithQuotes.forEach { (holding, quote) ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(holding.name + " · " + holding.symbol, fontWeight = FontWeight.SemiBold)
                    val price = quote?.lastPrice?.toString() ?: "暂无报价"
                    Text("成本 " + holding.averageCost + " · 数量 " + holding.quantity + " · 现价 " + price)
                    quote?.let {
                        Text(
                            "来源 " + it.provider + " · " + freshnessLabel(it.freshness) + " · " + formatTime(it.fetchedAt),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
        SectionTitle("快速入口")
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "行情" to "market",
                "提醒" to "alerts",
                "研究" to "research",
                "报告" to "reports",
                "候选" to "candidates",
                "组合" to "portfolio",
                "卖出研究" to "exit",
                "AI 问答" to "chat",
            ).forEach { (label, destination) ->
                OutlinedButton(onClick = { navigate(destination) }) { Text(label) }
            }
        }
        SectionTitle("研究状态")
        if (runs.isEmpty()) Text("暂无研究任务")
        runs.take(3).forEach {
            Text(it.state.name + " · " + it.completedCount + " / " + it.totalCount + " · " + formatTime(it.updatedAt))
        }
        if (reports.isNotEmpty()) {
            SectionTitle("最近报告")
            Text(reports.first().title + " · " + formatTime(reports.first().createdAt))
        }
    }
}

@Composable
private fun MarketScreen(
    viewModel: StandaloneViewModel,
    modifier: Modifier,
    navigate: (String) -> Unit,
) {
    val state by viewModel.marketState.collectAsState()
    ScreenColumn(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateMarketQuery,
                label = { Text("证券代码") },
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { viewModel.refreshMarketSymbol() }, enabled = !state.loading) {
                Text(if (state.loading) "读取中" else "刷新")
            }
        }
        state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state.quote?.let { quote ->
            InfoCard(
                title = quote.name + " · " + quote.symbol,
                text = "现价 " + (quote.lastPrice?.toString() ?: "不可用") +
                    " · 涨跌 " + (quote.changePercent?.toString() ?: "-") + "%" +
                    "\n来源 " + quote.provider + " · " + freshnessLabel(quote.freshness) +
                    " · " + formatTime(quote.fetchedAt),
            ) {
                Button(onClick = { viewModel.addWatchlist(quote.symbol, quote.name) }) { Text("加入自选") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { navigate("assets") }) { Text("添加为持仓") }
            }
        }
        SectionTitle("日 K 线")
        CandlePreview(state.candles)
        Text(
            if (state.candles.isEmpty()) "尚无 K 线缓存" else "最近 " + state.candles.size + " 根，数据采集于 " + formatTime(state.candles.last().fetchedAt),
            style = MaterialTheme.typography.labelMedium,
        )
        SectionTitle("证券目录")
        if (state.catalog.isEmpty()) {
            Text("打开本页时按需读取证券目录；无网络时使用内置常用证券。")
            OutlinedButton(onClick = { viewModel.loadCatalog() }) { Text("加载目录") }
        }
        state.catalog.take(100).forEach { security ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.refreshMarketSymbol(security.symbol) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(security.name)
                Text(security.symbol + " · " + security.exchange)
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AssetsScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val holdings by viewModel.holdings.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    var symbol by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    var cost by rememberSaveable { mutableStateOf("") }
    var watchSymbol by rememberSaveable { mutableStateOf("") }
    var watchName by rememberSaveable { mutableStateOf("") }
    ScreenColumn(modifier) {
        SectionTitle("新增/更新持仓")
        OutlinedTextField(symbol, { symbol = it }, label = { Text("证券代码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(quantity, { quantity = it }, label = { Text("数量") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(cost, { cost = it }, label = { Text("平均成本") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = {
                viewModel.saveHolding(symbol.trim(), name.trim(), quantity.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0)
                symbol = ""; name = ""; quantity = ""; cost = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存持仓并生成 ATR 止损线") }
        SectionTitle("当前持仓")
        holdings.forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(it.name + " · " + it.symbol, fontWeight = FontWeight.SemiBold)
                    Text("数量 " + it.quantity + " · 成本 " + it.averageCost)
                }
                TextButton(onClick = { viewModel.removeHolding(it.symbol) }) { Text("移除") }
            }
            HorizontalDivider()
        }
        SectionTitle("新增自选")
        OutlinedTextField(watchSymbol, { watchSymbol = it }, label = { Text("证券代码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(watchName, { watchName = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = {
                viewModel.addWatchlist(watchSymbol.trim(), watchName.trim())
                watchSymbol = ""; watchName = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存自选") }
        watchlist.forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(it.name + " · " + it.symbol)
                TextButton(onClick = { viewModel.removeWatchlist(it.symbol) }) { Text("移除") }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun AlertsScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val alerts by viewModel.alerts.collectAsState()
    var symbol by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var lower by rememberSaveable { mutableStateOf("") }
    var upper by rememberSaveable { mutableStateOf("") }
    var expiryDays by rememberSaveable { mutableStateOf("30") }
    var kind by rememberSaveable { mutableStateOf(AlertKind.MANUAL_PRICE) }
    ScreenColumn(modifier) {
        InfoCard(
            title = "本地提醒语义",
            text = "止损线默认采用 ATR20×2，并限制在成本下方 5%–10%；无 K 线时回退 8%。每条规则均有冷却，买入区间可设置有效期。",
        )
        SectionTitle("新建提醒")
        OutlinedTextField(symbol, { symbol = it }, label = { Text("证券代码") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(name, { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AlertKind.entries.forEach {
                FilterChip(selected = kind == it, onClick = { kind = it }, label = { Text(alertKindLabel(it)) })
            }
        }
        OutlinedTextField(lower, { lower = it }, label = { Text("下限/止损线") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(upper, { upper = it }, label = { Text("上限/止盈线") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(expiryDays, { expiryDays = it }, label = { Text("有效期天数（买入区间）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = {
                val expires = if (kind == AlertKind.BUY_ZONE) {
                    System.currentTimeMillis() + (expiryDays.toLongOrNull() ?: 30L) * 86_400_000L
                } else {
                    null
                }
                viewModel.saveManualAlert(symbol, name, kind, lower.toDoubleOrNull(), upper.toDoubleOrNull(), expires)
                symbol = ""; name = ""; lower = ""; upper = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存本地提醒") }
        SectionTitle("已保存提醒")
        alerts.forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(it.name + " · " + alertKindLabel(it.kind), fontWeight = FontWeight.SemiBold)
                    Text("下限 " + (it.lowerBound?.toString() ?: "-") + " · 上限 " + (it.upperBound?.toString() ?: "-"))
                    Text("冷却 " + it.cooldownMinutes + " 分钟" + (it.expiresAt?.let { " · 到期 " + formatTime(it) } ?: ""))
                }
                TextButton(onClick = { viewModel.removeAlert(it.id) }) { Text("移除") }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun ResearchScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val settings by viewModel.settings.collectAsState()
    val runs by viewModel.researchRuns.collectAsState()
    val providers by viewModel.aiProviders.collectAsState()
    var scope by rememberSaveable { mutableStateOf(ResearchScope.HOLDINGS) }
    var customSymbols by rememberSaveable { mutableStateOf("") }
    var marketLimit by rememberSaveable { mutableStateOf(settings.marketScanLimit.toString()) }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    var includePortfolio by rememberSaveable { mutableStateOf(false) }
    val estimate = viewModel.researchEstimate(
        scope,
        customSymbols,
        marketLimit.toIntOrNull() ?: settings.marketScanLimit,
        providerId != null,
    )
    ScreenColumn(modifier) {
        InfoCard(
            title = "本地研究",
            text = "趋势、均线、MACD、RSI、ATR、波动率、成交量和行情新鲜度由本地规则计算。基本面或事件数据缺失时会明确标记，不会伪造分数。",
        )
        SectionTitle("研究范围")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ResearchScope.entries.forEach {
                FilterChip(selected = scope == it, onClick = { scope = it }, label = { Text(researchScopeLabel(it)) })
            }
        }
        if (scope == ResearchScope.CUSTOM) {
            OutlinedTextField(
                customSymbols,
                { customSymbols = it },
                label = { Text("指定代码，逗号/空格分隔") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (scope == ResearchScope.MARKET) {
            OutlinedTextField(
                marketLimit,
                { marketLimit = it.filter(Char::isDigit).take(3) },
                label = { Text("全市场候选数量（默认 100，最高 500）") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        SectionTitle("AI 解释（可选）")
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = providerId == null, onClick = { providerId = null }, label = { Text("仅本地规则") })
            providers.filter { it.enabled }.forEach {
                FilterChip(selected = providerId == it.id, onClick = { providerId = it.id }, label = { Text(it.name) })
            }
        }
        SwitchRow(
            label = "本次授权发送持仓信息",
            checked = includePortfolio,
            enabled = settings.portfolioDataAllowedForAi && providerId != null,
            onChange = { includePortfolio = it },
        )
        if (!settings.portfolioDataAllowedForAi) {
            Text("全局持仓数据授权未开启，成本与数量不会发送给 AI。", style = MaterialTheme.typography.labelMedium)
        }
        InfoCard(
            title = "启动前估算",
            text = "股票 " + estimate.symbols + " · 批次 " + estimate.batches +
                " · 约 " + estimate.estimatedNetworkRequests + " 次行情请求 · 约 " +
                estimate.estimatedMinutes + " 分钟 · AI 解释范围 " +
                if (estimate.aiSymbols == 0) "0（关闭）" else "前 10 名摘要",
        )
        Button(
            onClick = {
                viewModel.startResearch(
                    scope = scope,
                    customSymbols = customSymbols,
                    marketLimit = marketLimit.toIntOrNull() ?: settings.marketScanLimit,
                    aiProviderId = providerId,
                    includePortfolioData = includePortfolio,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("启动本地研究") }
        SectionTitle("研究运行")
        runs.forEach {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(researchScopeLabel(it.scope) + " · " + it.state.name, fontWeight = FontWeight.SemiBold)
                    Text(it.completedCount.toString() + " / " + it.totalCount + " · " + formatTime(it.updatedAt))
                    it.errorMessage?.let { error -> Text(error, color = MaterialTheme.colorScheme.error) }
                    if (it.state in setOf(ResearchRunState.QUEUED, ResearchRunState.RUNNING, ResearchRunState.CANCELLING)) {
                        TextButton(onClick = { viewModel.cancelResearch(it.id) }) { Text("取消") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val reports by viewModel.reports.collectAsState()
    ScreenColumn(modifier) {
        if (reports.isEmpty()) Text("尚无本地报告。研究完成后会生成确定性模板报告。")
        reports.forEach {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(it.title + " · " + formatTime(it.createdAt), fontWeight = FontWeight.Bold)
                    Text(it.deterministicBody)
                    it.aiExplanation?.let { explanation ->
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text("AI 解释", fontWeight = FontWeight.SemiBold)
                        Text(explanation)
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidatesScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val candidates by viewModel.candidates.collectAsState()
    ScreenColumn(modifier) {
        Text("候选完全按本地确定性评分排序，不代表交易指令。")
        candidates.forEachIndexed { index, candidate ->
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text((index + 1).toString() + ". " + candidate.name + " · " + candidate.symbol, fontWeight = FontWeight.SemiBold)
                    Text(candidate.reason, style = MaterialTheme.typography.bodySmall)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(candidate.score.toString())
                    Text(candidate.risk, style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun PortfolioScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val portfolios by viewModel.portfolios.collectAsState()
    ScreenColumn(modifier) {
        Text("模拟组合仅用于研究观察，不会向券商或服务器提交订单。")
        portfolios.forEach {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(it.name + " · 评分 " + it.score, fontWeight = FontWeight.Bold)
                    Text(it.holdingsJson)
                    Text(formatTime(it.createdAt), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun ExitResearchScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val entries by viewModel.exitResearch.collectAsState()
    ScreenColumn(modifier) {
        InfoCard(
            title = "卖出研究",
            text = "由本地 ATR、成本和可用行情生成，只作风险研究，不代表自动下单。AI 只能解释，不能改写止损或浮盈门槛。",
        )
        Button(onClick = viewModel::refreshExitResearch, modifier = Modifier.fillMaxWidth()) {
            Text("刷新卖出研究")
        }
        if (entries.isEmpty()) Text("请先添加持仓并刷新。")
        entries.forEach {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(it.name + " · " + it.symbol + " · " + it.state, fontWeight = FontWeight.Bold)
                    Text(
                        "现价 " + (it.currentPrice?.toString() ?: "不可用") +
                            " · 止损 " + it.stopLoss +
                            " · 浮盈参考 " + it.profitExit,
                    )
                    Text(it.explanation, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun NotificationsScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val notifications by viewModel.notifications.collectAsState()
    ScreenColumn(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("本地通知中心", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            TextButton(onClick = viewModel::markAllNotificationsRead) { Text("全部已读") }
        }
        if (notifications.isEmpty()) Text("暂无通知")
        notifications.forEach {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { viewModel.markNotificationRead(it.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (it.isRead) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.secondaryContainer,
                ),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(it.title, fontWeight = FontWeight.SemiBold)
                    Text(it.body)
                    Text(
                        notificationPriorityLabel(it.priority) + " · " + formatTime(it.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val providers by viewModel.aiProviders.collectAsState()
    val sessions by viewModel.chatSessions.collectAsState()
    val messages by viewModel.activeChatMessages.collectAsState()
    val settings by viewModel.settings.collectAsState()
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    var includePortfolio by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable { mutableStateOf("") }
    ScreenColumn(modifier) {
        InfoCard(
            title = "应用内 AI 问答",
            text = "默认仅发送所选股票的报价、K 线和本地研究摘要。成本与数量必须同时打开全局和本次授权才会发送。",
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = providerId == null, onClick = { providerId = null }, label = { Text("未选择") })
            providers.filter { it.enabled }.forEach {
                FilterChip(selected = providerId == it.id, onClick = { providerId = it.id }, label = { Text(it.name) })
            }
        }
        SwitchRow(
            label = "本次包含持仓成本与数量",
            checked = includePortfolio,
            enabled = settings.portfolioDataAllowedForAi && providerId != null,
            onChange = { includePortfolio = it },
        )
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = viewModel::createChatSession) { Text("新会话") }
            sessions.forEach {
                FilterChip(selected = false, onClick = { viewModel.selectChatSession(it.id) }, label = { Text(it.title) })
            }
        }
        messages.forEach {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (it.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(if (it.role == "user") "你" else "AI", fontWeight = FontWeight.Bold)
                    Text(it.body)
                    if (it.includedPortfolio) Text("本条已获持仓信息授权", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        OutlinedTextField(
            input,
            { input = it },
            label = { Text("输入问题（默认使用行情页所选代码）") },
            modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        )
        Button(
            onClick = {
                viewModel.sendChat(input, providerId, includePortfolio)
                input = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("发送") }
    }
}

@Composable
private fun SettingsScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val providers by viewModel.aiProviders.collectAsState()
    val aiTestResult by viewModel.aiTestResult.collectAsState()
    var intervalText by rememberSaveable { mutableStateOf(settings.monitoringIntervalSeconds.toString()) }
    var providerName by rememberSaveable { mutableStateOf("") }
    var providerUrl by rememberSaveable { mutableStateOf("https://api.openai.com") }
    var providerKey by rememberSaveable { mutableStateOf("") }
    var providerModel by rememberSaveable { mutableStateOf("gpt-4.1-mini") }
    var organization by rememberSaveable { mutableStateOf("") }
    var project by rememberSaveable { mutableStateOf("") }
    var exportPassphrase by rememberSaveable { mutableStateOf("") }
    var importPassphrase by rememberSaveable { mutableStateOf("") }
    var archiveStatus by rememberSaveable { mutableStateOf<String?>(null) }
    val createArchive = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                archiveStatus = runCatching {
                    val data = viewModel.exportArchive(exportPassphrase.toCharArray())
                    context.contentResolver.openOutputStream(uri)?.use { it.write(data) }
                        ?: error("无法写入所选文件")
                    exportPassphrase = ""
                    "已导出加密 .ashare-local 档案"
                }.getOrElse { it.message ?: "导出失败" }
            }
        }
    }
    val openArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                archiveStatus = runCatching {
                    val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                    val result = viewModel.importArchive(data, importPassphrase.toCharArray())
                    importPassphrase = ""
                    result
                }.getOrElse { it.message ?: "导入失败" }
            }
        }
    }
    ScreenColumn(modifier) {
        SectionTitle("通知与后台")
        SwitchRow("持仓监控", settings.monitoringEnabled, onChange = viewModel::setMonitoringEnabled)
        SwitchRow("提醒开关", settings.alertsEnabled, onChange = viewModel::setAlertsEnabled)
        SwitchRow("超级岛 v3 载荷", settings.islandEnabled, onChange = viewModel::setIslandEnabled)
        OutlinedTextField(
            intervalText,
            { intervalText = it.filter(Char::isDigit).take(3) },
            label = { Text("交易时段监控频率（15–300 秒）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { viewModel.setMonitoringInterval(intervalText.toIntOrNull() ?: 30) }) {
            Text("保存频率")
        }
        SwitchRow("每日 15:05 研究", settings.dailyResearchEnabled, onChange = viewModel::setDailyResearchEnabled)
        SwitchRow("日报 A", settings.dailyReportAEnabled, onChange = viewModel::setDailyReportAEnabled)
        SwitchRow("日报 B", settings.dailyReportBEnabled, onChange = viewModel::setDailyReportBEnabled)
        InfoCard(
            title = "资源状态",
            text = "无任务及非交易时段不请求行情。前台服务超时后会停止并降级为约 15 分钟 WorkManager 检查。目标：监控 CPU < 1.5%，PSS < 120 MiB；系统策略可能仍会终止进程。",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::clearMarketCache) { Text("清除缓存") }
            OutlinedButton(onClick = viewModel::testMarketProvider) { Text("测试行情源") }
            OutlinedButton(onClick = viewModel::showIslandTest) { Text("测试超级岛") }
        }
        OutlinedButton(
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName),
                    ),
                )
            },
        ) { Text("打开小米自启动/电量/通知设置") }
        Text(
            "请在系统设置中允许通知、开启自启动、设为无限制电量并锁定后台；这些设置不能保证绝对保活。",
            style = MaterialTheme.typography.labelMedium,
        )

        SectionTitle("AI Provider（本机 Keystore 加密）")
        OutlinedTextField(providerName, { providerName = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(providerUrl, { providerUrl = it }, label = { Text("Base URL") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(providerKey, { providerKey = it }, label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(providerModel, { providerModel = it }, label = { Text("模型") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(organization, { organization = it }, label = { Text("OpenAI Organization（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(project, { project = it }, label = { Text("OpenAI Project（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = {
                viewModel.saveAiProvider(
                    AiProviderDraft(
                        name = providerName,
                        baseUrl = providerUrl,
                        apiKey = providerKey,
                        model = providerModel,
                        organization = organization,
                        project = project,
                    ),
                )
                providerName = ""; providerKey = ""; organization = ""; project = ""
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存 AI Provider") }
        providers.forEach {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(it.name + " · " + it.model, fontWeight = FontWeight.SemiBold)
                    Text(it.baseUrl, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { viewModel.testAiProvider(it.id) }) { Text("测试") }
                TextButton(onClick = { viewModel.removeAiProvider(it.id) }) { Text("删除") }
            }
        }
        aiTestResult?.let { Text("连接测试：" + it) }
        SwitchRow(
            "允许 AI 使用持仓成本与数量（仍需每次单独授权）",
            settings.portfolioDataAllowedForAi,
            onChange = viewModel::setPortfolioDataAllowedForAi,
        )

        SectionTitle("加密本机档案")
        Text("档案不含 API Key、行情/K 线缓存或临时文件。导入仅在本机执行。")
        OutlinedTextField(exportPassphrase, { exportPassphrase = it }, label = { Text("导出档案口令") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Button(
            onClick = { if (exportPassphrase.isNotBlank()) createArchive.launch("ashare-local-backup.ashare-local") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("导出 .ashare-local") }
        OutlinedTextField(importPassphrase, { importPassphrase = it }, label = { Text("导入档案口令") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedButton(
            onClick = { if (importPassphrase.isNotBlank()) openArchive.launch(arrayOf("application/octet-stream", "*/*")) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("导入 .ashare-local") }
        archiveStatus?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun ScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun InfoCard(
    title: String,
    text: String,
    actions: @Composable (() -> Unit)? = null,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(text)
            if (actions != null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, content = { actions() })
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun CandlePreview(candles: List<DailyCandle>) {
    if (candles.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
            Text("无可绘制 K 线")
        }
        return
    }
    val subset = candles.takeLast(80)
    Canvas(modifier = Modifier.fillMaxWidth().height(180.dp)) {
        val high = subset.maxOf(DailyCandle::high)
        val low = subset.minOf(DailyCandle::low)
        val range = max(high - low, 0.001)
        val step = size.width / subset.size
        subset.forEachIndexed { index, candle ->
            val x = step * (index + 0.5f)
            fun y(value: Double): Float = ((high - value) / range * size.height.toDouble()).toFloat()
            val color = if (candle.close >= candle.open) Color(0xFFD32F2F) else Color(0xFF2E7D32)
            drawLine(color, Offset(x, y(candle.high)), Offset(x, y(candle.low)), 1.2f, StrokeCap.Round)
            val top = minOf(y(candle.open), y(candle.close))
            val bottom = maxOf(y(candle.open), y(candle.close))
            drawLine(color, Offset(x, top), Offset(x, max(bottom, top + 1f)), step * 0.55f, StrokeCap.Butt)
        }
    }
}

private fun routeTitle(route: String): String = when (route) {
    "home" -> "本地总览"
    "market" -> "行情与 K 线"
    "assets" -> "持仓与自选"
    "alerts" -> "提醒规则"
    "research" -> "本地研究"
    "reports" -> "研究报告"
    "candidates" -> "候选池"
    "portfolio" -> "模拟组合"
    "exit" -> "卖出研究"
    "notifications" -> "通知中心"
    "chat" -> "AI 问答"
    "settings" -> "设置与本机档案"
    else -> "本地总览"
}

private val routes = setOf(
    "home",
    "market",
    "assets",
    "alerts",
    "research",
    "reports",
    "candidates",
    "portfolio",
    "exit",
    "notifications",
    "chat",
    "settings",
)

private fun formatTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

private fun freshnessLabel(freshness: MarketFreshness): String = when (freshness) {
    MarketFreshness.FRESH -> "新鲜"
    MarketFreshness.STALE -> "陈旧缓存"
    MarketFreshness.UNAVAILABLE -> "不可用"
}

private fun alertKindLabel(kind: AlertKind): String = when (kind) {
    AlertKind.STOP_LOSS -> "止损"
    AlertKind.PROFIT_EXIT -> "浮盈退出"
    AlertKind.BUY_ZONE -> "买入区间"
    AlertKind.MANUAL_PRICE -> "手动价位"
}

private fun researchScopeLabel(scope: ResearchScope): String = when (scope) {
    ResearchScope.HOLDINGS -> "持仓"
    ResearchScope.WATCHLIST -> "自选"
    ResearchScope.CUSTOM -> "指定股票"
    ResearchScope.MARKET -> "全市场候选"
}

private fun notificationPriorityLabel(priority: NotificationPriority): String = when (priority) {
    NotificationPriority.NORMAL -> "普通"
    NotificationPriority.WARNING -> "预警"
    NotificationPriority.PROGRESS -> "研究进度"
}
