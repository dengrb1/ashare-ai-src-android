package com.ashareai.app.standalone.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ashareai.app.standalone.data.ai.AiProviderDraft
import com.ashareai.app.standalone.data.settings.AutomaticResearchReportConfig
import com.ashareai.app.ui.components.LiquidGlassBottomBar
import com.ashareai.app.ui.components.LiquidGlassTab
import com.ashareai.app.ui.theme.LocalGlassEnabled
import com.ashareai.app.ui.theme.LocalPowerSaveMode
import com.ashareai.app.workspace.SharedDataStore
import com.ashareai.app.standalone.data.archive.ArchiveMergePreview
import com.ashareai.app.standalone.data.archive.ArchiveMergeResolution
import com.ashareai.app.standalone.domain.AlertKind
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.MarketFreshness
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.NotificationPriority
import com.ashareai.app.standalone.domain.ResearchRunState
import com.ashareai.app.standalone.domain.ResearchScope
import com.ashareai.app.standalone.island.FocusCapabilities
import com.ashareai.app.standalone.island.FocusNotification
import com.mikepenz.markdown.m3.Markdown
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

data class DevicePermissionState(
    val notificationsGranted: Boolean,
    val batteryUnrestricted: Boolean,
)

private data class AutomaticReportDraft(
    val slot: String,
    val enabled: Boolean,
    val scope: ResearchScope,
    val symbols: String,
    val totalBudget: String,
    val perSymbolBudget: String,
    val maxStockPrice: String,
    val marketLimit: String,
    val configVersion: Int,
)

private fun AutomaticResearchReportConfig.toDraft() = AutomaticReportDraft(
    slot = slot,
    enabled = enabled,
    scope = scope,
    symbols = symbols.joinToString(", "),
    totalBudget = totalBudget.toString(),
    perSymbolBudget = perSymbolBudget.toString(),
    maxStockPrice = maxStockPrice?.toString().orEmpty(),
    marketLimit = marketLimit.toString(),
    configVersion = configVersion,
)

private fun AutomaticReportDraft.toConfig() = AutomaticResearchReportConfig(
    slot = slot,
    enabled = enabled,
    scope = scope,
    symbols = symbols.split(",", " ", "\n", "、").map(String::trim)
        .filter { it.length == 6 && it.all(Char::isDigit) }.distinct(),
    totalBudget = totalBudget.toDoubleOrNull() ?: 0.0,
    perSymbolBudget = perSymbolBudget.toDoubleOrNull() ?: 0.0,
    maxStockPrice = maxStockPrice.toDoubleOrNull(),
    marketLimit = marketLimit.toIntOrNull() ?: 0,
    configVersion = configVersion,
)

@Composable
fun StandaloneAppRoot(
    viewModel: StandaloneViewModel,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
    onSwitchToConnected: () -> Unit = {},
    permissionState: DevicePermissionState,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    val incomingRoute by pendingRoute.collectAsState()
    val message by viewModel.message.collectAsState()
    val unread by viewModel.unreadNotifications.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val isPowerSaveMode by viewModel.isPowerSaveMode.collectAsState()
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
            GlassTopBar(route = route)
        },
        bottomBar = {
            LiquidGlassBottomBar(
                tabs = bottomDestinations.map {
                    LiquidGlassTab(it.route, it.label, it.icon, if (it.route == "notifications") unread else 0)
                },
                selectedKey = route,
                powerSaveMode = isPowerSaveMode,
                onSelect = { destination ->
                    route = destination
                    if (destination == "market") viewModel.loadCatalog()
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (!settings.fullAnimationsEnabled || isPowerSaveMode) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else (fadeIn(animationSpec = androidx.compose.animation.core.tween(180)) +
                    slideInHorizontally(
                        initialOffsetX = { it / 18 },
                        animationSpec = androidx.compose.animation.core.tween(220),
                    )).togetherWith(
                    fadeOut(animationSpec = androidx.compose.animation.core.tween(120)) +
                        slideOutHorizontally(
                            targetOffsetX = { -it / 24 },
                            animationSpec = androidx.compose.animation.core.tween(160),
                        )
                )
            },
            label = "standalone_route_transition",
        ) { targetRoute ->
        when (targetRoute) {
            "home" -> HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                permissionState = permissionState,
                onRequestNotifications = onRequestNotifications,
                onOpenBatterySettings = onOpenBatterySettings,
                navigate = { route = it },
            )
            "market" -> MarketScreen(viewModel, Modifier.padding(padding)) { route = it }
            "assets" -> AssetsScreen(viewModel, Modifier.padding(padding))
            "alerts" -> AlertsScreen(viewModel, Modifier.padding(padding))
            "research" -> ResearchScreen(viewModel, Modifier.padding(padding))
            "reports" -> ReportsScreen(viewModel, Modifier.padding(padding)) { symbol ->
                viewModel.refreshMarketSymbol(symbol)
                route = "market"
            }
            "candidates" -> CandidatesScreen(viewModel, Modifier.padding(padding))
            "portfolio" -> PortfolioScreen(viewModel, Modifier.padding(padding))
            "exit" -> ExitResearchScreen(viewModel, Modifier.padding(padding))
            "notifications" -> NotificationsScreen(viewModel, Modifier.padding(padding))
            "chat" -> ChatScreen(viewModel, Modifier.padding(padding))
            "ai_agents" -> AiAgentConfigScreen(
                providers = viewModel.aiProviders.collectAsState().value,
                agents = viewModel.aiAgents.collectAsState().value,
                onSaveAgent = viewModel::saveAiAgent,
                onDeleteAgent = viewModel::removeAiAgent,
                onNavigateToProviders = { route = "ai_providers" },
                modifier = Modifier.padding(padding),
            )
            "ai_providers" -> {
                val scope = rememberCoroutineScope()
                AiProviderConfigScreen(
                    providers = viewModel.aiProviders.collectAsState().value,
                    onSave = { draft -> viewModel.saveAiProviderSuspend(draft) },
                    onDelete = { id -> viewModel.deleteAiProvider(id) },
                    onTest = { id -> viewModel.testAiProviderSuspend(id) },
                    onNavigateBack = { route = "settings" },
                    modifier = Modifier.padding(padding),
                )
            }
            "settings" -> SettingsScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                onSwitchToConnected = onSwitchToConnected,
                permissionState = permissionState,
                onRequestNotifications = onRequestNotifications,
                onOpenBatterySettings = onOpenBatterySettings,
                onOpenAppSettings = onOpenAppSettings,
                onNavigateToAiAgents = { route = "ai_agents" },
                navigate = { route = it },
            )
            else -> HomeScreen(
                viewModel = viewModel,
                modifier = Modifier.padding(padding),
                permissionState = permissionState,
                onRequestNotifications = onRequestNotifications,
                onOpenBatterySettings = onOpenBatterySettings,
                navigate = { route = it },
            )
        }
        }
    }
}

private data class BottomDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val bottomDestinations = listOf(
    BottomDestination("home", "主页", Icons.Outlined.Home),
    BottomDestination("market", "行情", Icons.Outlined.CandlestickChart),
    BottomDestination("research", "研究", Icons.Outlined.QueryStats),
    BottomDestination("notifications", "通知", Icons.Outlined.Notifications),
    BottomDestination("settings", "设置", Icons.Outlined.Settings),
)

@Composable
private fun GlassTopBar(route: String) {
    val dark = isSystemInDarkTheme()
    val glassEnabled = LocalGlassEnabled.current && !LocalPowerSaveMode.current
    val outlineColor = MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        // 毛玻璃背景层
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(
                    if (glassEnabled) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.88f else 0.85f),
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (dark) 0.16f else 0.22f),
                                    MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.85f else 0.82f),
                                ),
                            ),
                        )
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                    }
                )
                .then(
                    if (glassEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.graphicsLayer {
                            renderEffect = BlurEffect(18f, 18f, TileMode.Clamp)
                        }
                    } else {
                        Modifier
                    }
                )
                .drawWithContent {
                    drawContent()
                    if (glassEnabled) {
                    // 顶部高光边框
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (dark) 0.12f else 0.38f),
                                Color.Transparent,
                            ),
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx(),
                    )
                    // 底部分隔线
                    drawLine(
                        color = outlineColor.copy(alpha = 0.5f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                    } else {
                        drawLine(outlineColor, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                    }
                }
        )

        // 内容层
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = routeTitle(route),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                text = "霁衡智研",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    viewModel: StandaloneViewModel,
    modifier: Modifier,
    permissionState: DevicePermissionState,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    navigate: (String) -> Unit,
) {
    val settings by viewModel.settings.collectAsState()
    val holdingsWithQuotes by viewModel.holdingsWithQuotes.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val runs by viewModel.researchRuns.collectAsState()
    ScreenColumn(modifier) {
        if (!permissionState.notificationsGranted || !permissionState.batteryUnrestricted) {
            PermissionCard(
                state = permissionState,
                onRequestNotifications = onRequestNotifications,
                onOpenBatterySettings = onOpenBatterySettings,
            )
        }
        if (settings.firstRun) {
            InfoCard(
                title = "欢迎使用霁衡智研",
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
            EmptyState("尚未添加持仓", "添加持仓后才会启动后台行情监控。")
        }
        holdingsWithQuotes.forEach { (holding, quote) ->
            ContentCard(modifier = Modifier.fillMaxWidth()) {
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
        listOf(
            "行情" to "market",
            "提醒" to "alerts",
            "研究" to "research",
            "报告" to "reports",
            "候选" to "candidates",
            "组合" to "portfolio",
            "卖出研究" to "exit",
            "AI 问答" to "chat",
        ).chunked(2).forEach { destinations ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                destinations.forEach { (label, destination) ->
                    OutlinedButton(
                        onClick = { navigate(destination) },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) { Text(label) }
                }
                if (destinations.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        SectionTitle("研究状态")
        if (runs.isEmpty()) EmptyState("暂无研究任务", "启动研究后可在这里查看最新进度。")
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
    val holdings by viewModel.holdings.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    var subChart by rememberSaveable { mutableStateOf(StandaloneSubChart.VOLUME) }
    var range by rememberSaveable { mutableStateOf(StandaloneKlineRange.MONTH_3) }
    val displayedCandles = remember(state.candles, range) { selectKlineRange(state.candles, range) }
    val personalSecurities = remember(holdings, watchlist) {
        (holdings.map { it.symbol to it.name } + watchlist.map { it.symbol to it.name }).distinctBy { it.first }
    }
    val searchResults = remember(state.catalog, state.query) {
        val query = state.query.trim()
        state.catalog.filter {
            query.isBlank() || it.symbol.contains(query) || it.name.contains(query, ignoreCase = true)
        }.take(16)
    }
    LaunchedEffect(Unit) {
        if (state.quote == null) viewModel.refreshMarketSymbol()
        if (state.indexQuotes.isEmpty()) viewModel.loadMarketIndices()
        if (state.catalog.isEmpty()) viewModel.loadCatalog(500)
    }
    ScreenColumn(modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateMarketQuery,
                label = { Text("搜索股票") },
                placeholder = { Text("代码或名称") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.refreshMarketSymbol() }),
            )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = {
                        viewModel.refreshMarketSymbol()
                        viewModel.loadMarketIndices(forceRefresh = true)
                    },
                    enabled = !state.loading,
                ) {
                    if (state.loading) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Search, contentDescription = "查看股票")
                    }
                }
            }
        }
        if (personalSecurities.isNotEmpty()) {
            Text("我的股票", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                personalSecurities.forEach { (symbol, name) ->
                    FilterChip(
                        selected = state.quote?.symbol == symbol,
                        onClick = { viewModel.refreshMarketSymbol(symbol) },
                        label = { Text("$name $symbol", maxLines = 1) },
                    )
                }
            }
        }
        if (state.query.isNotBlank() && searchResults.none { it.symbol == state.quote?.symbol }) {
            Text("搜索结果", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            searchResults.take(6).forEach { security ->
                MarketCatalogRow(
                    name = security.name,
                    symbol = security.symbol,
                    exchange = security.exchange,
                    selected = false,
                    onClick = { viewModel.refreshMarketSymbol(security.symbol) },
                )
            }
        }
        MarketIndexStrip(
            quotes = state.indexQuotes,
            loading = state.indicesLoading,
        )
        state.message?.let {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.quote?.let { quote ->
            MarketQuotePanel(
                quote = quote,
                latestCandle = state.candles.lastOrNull(),
                onAddWatchlist = { viewModel.addWatchlist(quote.symbol, quote.name) },
                onAddHolding = { navigate("assets") },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("日 K 线", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("前复权 · ${range.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                if (displayedCandles.isEmpty()) "无数据" else "${displayedCandles.size} 根",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        GlassKlineRangeSelector(selected = range, onSelected = { range = it })
        GlassSubChartSelector(selected = subChart, onSelected = { subChart = it })
        Surface(
            modifier = Modifier.fillMaxWidth().height(370.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            StandaloneCandlestickChart(
                candles = displayedCandles,
                subChart = subChart,
                modifier = Modifier.fillMaxSize().padding(12.dp),
            )
        }
        Text(
            if (displayedCandles.isEmpty()) {
                "尚无 K 线缓存"
            } else {
                "双指缩放或拖动查看历史；长按显示十字光标 · ${displayedCandles.first().tradingDate} 至 ${displayedCandles.last().tradingDate}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.query.isBlank()) SectionTitle("证券目录")
        if (state.catalog.isEmpty()) {
            EmptyState(
                title = "证券目录尚未加载",
                description = "无网络时会使用内置常用证券。",
            )
            OutlinedButton(onClick = { viewModel.loadCatalog() }, modifier = Modifier.fillMaxWidth()) {
                Text("加载目录")
            }
        }
        if (state.query.isBlank()) {
            searchResults.forEach { security ->
                MarketCatalogRow(
                    name = security.name,
                    symbol = security.symbol,
                    exchange = security.exchange,
                    selected = state.quote?.symbol == security.symbol,
                    onClick = { viewModel.refreshMarketSymbol(security.symbol) },
                )
            }
        }
    }
}

@Composable
private fun GlassKlineRangeSelector(
    selected: StandaloneKlineRange,
    onSelected: (StandaloneKlineRange) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (isSystemInDarkTheme()) 0.18f else 0.72f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            ),
        ),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            StandaloneKlineRange.entries.forEach { range ->
                FilterChip(
                    selected = selected == range,
                    onClick = { onSelected(range) },
                    label = { Text(range.label, maxLines = 1) },
                    modifier = Modifier.wrapContentWidth(),
                )
            }
        }
    }
}

@Composable
private fun LiquidGlassSegmentedControl(content: @Composable RowScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (dark) 0.72f else 0.64f),
        border = BorderStroke(
            1.dp,
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) 0.18f else 0.72f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                ),
            ),
        ),
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            content = content,
        )
    }
}

@Composable
private fun MarketQuotePanel(
    quote: MarketQuote,
    latestCandle: DailyCandle?,
    onAddWatchlist: () -> Unit,
    onAddHolding: () -> Unit,
) {
    val change = if (quote.lastPrice != null && quote.previousClose != null) {
        quote.lastPrice - quote.previousClose
    } else {
        null
    }
    val changeColor = stockChangeColor(quote.changePercent)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(quote.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${quote.symbol} · ${quote.provider}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (quote.freshness) {
                        MarketFreshness.FRESH -> MaterialTheme.colorScheme.primaryContainer
                        MarketFreshness.STALE -> MaterialTheme.colorScheme.secondaryContainer
                        MarketFreshness.UNAVAILABLE -> MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Text(
                        freshnessLabel(quote.freshness),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    quote.lastPrice?.formatMarketNumber() ?: "--",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = changeColor,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    change?.let { signedMarketNumber(it) } ?: "--",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    quote.changePercent?.let { signedMarketNumber(it) + "%" } ?: "--",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = changeColor,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarketDetailStat("今开", latestCandle?.open?.formatMarketNumber() ?: "--")
                MarketDetailStat("最高", latestCandle?.high?.formatMarketNumber() ?: "--")
                MarketDetailStat("最低", latestCandle?.low?.formatMarketNumber() ?: "--")
                MarketDetailStat("昨收", quote.previousClose?.formatMarketNumber() ?: "--")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MarketDetailStat("成交量", formatMarketVolume(quote.volume))
                MarketDetailStat("K线日期", latestCandle?.tradingDate?.toString() ?: "--")
                MarketDetailStat("采集时间", formatTime(quote.fetchedAt))
            }
            AdaptiveActionRow {
                Button(onClick = onAddWatchlist) { Text("加入自选") }
                OutlinedButton(onClick = onAddHolding) { Text("添加为持仓") }
            }
        }
    }
}

@Composable
private fun MarketDetailStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GlassSubChartSelector(
    selected: StandaloneSubChart,
    onSelected: (StandaloneSubChart) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 5.dp,
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(46.dp).padding(4.dp)) {
            StandaloneSubChart.entries.forEach { option ->
                val active = selected == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (active) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f) else Color.Transparent,
                        )
                        .selectable(
                            selected = active,
                            onClick = { onSelected(option) },
                            role = Role.Tab,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarketCatalogRow(
    name: String,
    symbol: String,
    exchange: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text(symbol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(exchange, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ListItemSurface(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            content = content,
        )
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
        if (holdings.isEmpty()) EmptyState("暂无持仓", "填写证券代码、数量和成本后保存。")
        holdings.forEach {
            ListItemSurface {
                Column(Modifier.weight(1f)) {
                    Text(it.name + " · " + it.symbol, fontWeight = FontWeight.SemiBold)
                    Text(
                        "数量 " + it.quantity + " · 成本 " + it.averageCost,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { viewModel.removeHolding(it.symbol) }) { Text("移除") }
            }
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
        if (watchlist.isEmpty()) EmptyState("暂无自选", "添加证券后可在行情页快速查看。")
        watchlist.forEach {
            ListItemSurface {
                Column(Modifier.weight(1f)) {
                    Text(it.name, fontWeight = FontWeight.SemiBold)
                    Text(it.symbol, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { viewModel.removeWatchlist(it.symbol) }) { Text("移除") }
            }
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
        if (alerts.isEmpty()) EmptyState("暂无提醒", "创建价格区间、止损或止盈提醒。")
        alerts.forEach {
            ListItemSurface {
                Column(Modifier.weight(1f)) {
                    Text(it.name + " · " + alertKindLabel(it.kind), fontWeight = FontWeight.SemiBold)
                    Text(
                        "下限 " + (it.lowerBound?.toString() ?: "-") + " · 上限 " + (it.upperBound?.toString() ?: "-"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "冷却 " + it.cooldownMinutes + " 分钟" + (it.expiresAt?.let { " · 到期 " + formatTime(it) } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { viewModel.removeAlert(it.id) }) { Text("移除") }
            }
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
    var totalBudget by rememberSaveable { mutableStateOf("1000000") }
    var perSymbolBudget by rememberSaveable { mutableStateOf("80000") }
    var maxStockPrice by rememberSaveable { mutableStateOf("") }
    var providerId by rememberSaveable { mutableStateOf<String?>(null) }
    var includePortfolio by rememberSaveable { mutableStateOf(false) }
    var automaticDrafts by remember(settings.automaticReports) {
        mutableStateOf(settings.automaticReports.map { it.toDraft() })
    }
    fun updateAutomatic(slot: String, transform: (AutomaticReportDraft) -> AutomaticReportDraft) {
        automaticDrafts = automaticDrafts.map { if (it.slot == slot) transform(it) else it }
    }
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
        SectionTitle("自动每日报告")
        InfoCard(
            title = if (automaticDrafts.any { it.enabled }) {
                "已开启 ${automaticDrafts.count { it.enabled }} 份报告"
            } else {
                "自动报告已暂停"
            },
            text = "Asia/Shanghai 交易日 15:05 依次运行报告 A、B；配置与大盘指数环境会冻结到各自运行记录。",
        )
        automaticDrafts.forEach { draft ->
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("报告 ${draft.slot}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (draft.enabled) "每天运行" else "暂停",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = draft.enabled,
                            onCheckedChange = { enabled -> updateAutomatic(draft.slot) { it.copy(enabled = enabled) } },
                        )
                    }
                    LiquidGlassSegmentedControl {
                        listOf(
                            ResearchScope.MARKET to "全市场",
                            ResearchScope.WATCHLIST to "自选+持仓",
                            ResearchScope.CUSTOM to "指定股票",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = draft.scope == value,
                                onClick = { updateAutomatic(draft.slot) { it.copy(scope = value) } },
                                label = { Text(label) },
                            )
                        }
                    }
                    if (draft.scope == ResearchScope.CUSTOM) {
                        OutlinedTextField(
                            value = draft.symbols,
                            onValueChange = { value -> updateAutomatic(draft.slot) { it.copy(symbols = value.take(2_000)) } },
                            label = { Text("股票代码") },
                            supportingText = { Text("逗号、空格或换行分隔") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 3,
                        )
                    }
                    if (draft.scope == ResearchScope.MARKET) {
                        OutlinedTextField(
                            value = draft.marketLimit,
                            onValueChange = { value -> updateAutomatic(draft.slot) { it.copy(marketLimit = value.filter(Char::isDigit).take(3)) } },
                            label = { Text("扫描数量（1–500）") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                    OutlinedTextField(
                        value = draft.totalBudget,
                        onValueChange = { value -> updateAutomatic(draft.slot) { it.copy(totalBudget = value.take(18)) } },
                        label = { Text("总预算（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.perSymbolBudget,
                        onValueChange = { value -> updateAutomatic(draft.slot) { it.copy(perSymbolBudget = value.take(18)) } },
                        label = { Text("单股最高投入（元）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = draft.maxStockPrice,
                        onValueChange = { value -> updateAutomatic(draft.slot) { it.copy(maxStockPrice = value.take(18)) } },
                        label = { Text("最高可接受股价（可选）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
        }
        Button(
            onClick = { viewModel.saveAutomaticReports(automaticDrafts.map { it.toConfig() }) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存自动报告 A/B") }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
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
        SectionTitle("资金与价格约束")
        OutlinedTextField(
            value = totalBudget,
            onValueChange = { totalBudget = it.take(18) },
            label = { Text("总预算（元）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = perSymbolBudget,
            onValueChange = { perSymbolBudget = it.take(18) },
            label = { Text("单股最高投入（元）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = maxStockPrice,
            onValueChange = { maxStockPrice = it.take(18) },
            label = { Text("最高可接受股价（可选）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
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
                    totalBudget = totalBudget.toDoubleOrNull() ?: 0.0,
                    perSymbolBudget = perSymbolBudget.toDoubleOrNull() ?: 0.0,
                    maxStockPrice = maxStockPrice.takeIf(String::isNotBlank)?.toDoubleOrNull() ?: if (maxStockPrice.isBlank()) null else 0.0,
                    aiProviderId = providerId,
                    includePortfolioData = includePortfolio,
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("启动本地研究") }
        SectionTitle("研究运行")
        if (runs.isEmpty()) EmptyState("暂无研究运行", "选择范围并启动本地研究。")
        runs.forEach {
            ContentCard(modifier = Modifier.fillMaxWidth()) {
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
private fun ReportsScreen(
    viewModel: StandaloneViewModel,
    modifier: Modifier,
    openKline: (String) -> Unit,
) {
    val reports by viewModel.reports.collectAsState()
    val runs by viewModel.researchRuns.collectAsState()
    val candidates by viewModel.candidates.collectAsState()
    val portfolios by viewModel.portfolios.collectAsState()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(reports) {
        if (reports.none { it.id == selectedId }) selectedId = reports.firstOrNull()?.id
    }
    val selected = reports.firstOrNull { it.id == selectedId }
    val run = selected?.let { report -> runs.firstOrNull { it.id == report.runId } }
    val runCandidates = selected?.let { report -> candidates.filter { it.runId == report.runId } }.orEmpty()
    val portfolio = selected?.let { report -> portfolios.firstOrNull { it.runId == report.runId } }
    ScreenColumn(modifier) {
        if (reports.isEmpty()) EmptyState("尚无本地报告", "研究完成后会生成确定性模板报告。")
        if (reports.isNotEmpty()) {
            LiquidGlassSegmentedControl {
                reports.take(12).forEach { report ->
                    val reportRun = runs.firstOrNull { it.id == report.runId }
                    FilterChip(
                        selected = report.id == selectedId,
                        onClick = { selectedId = report.id },
                        label = {
                            Text(
                                (reportRun?.automaticReportSlot?.let { "报告 $it · " }.orEmpty()) + formatTime(report.createdAt),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }
        }
        selected?.let { report ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                InfoCard(
                    title = run?.let { researchScopeLabel(it.scope) } ?: "研究报告",
                    text = "${run?.completedCount ?: 0} / ${run?.totalCount ?: 0} 只 · ${run?.triggerSource?.name ?: "MANUAL"}",
                )
                InfoCard(
                    title = "${runCandidates.count { it.reason.contains("名 · 买入") }} 只买入",
                    text = portfolio?.let { "模拟组合分 ${it.score}" } ?: "未形成组合",
                )
            }
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(report.title + " · " + formatTime(report.createdAt), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Markdown(report.deterministicBody)
                }
            }
            if (runCandidates.isNotEmpty()) {
                SectionTitle("股票工作台")
                runCandidates.take(30).forEach { candidate ->
                    ListItemSurface {
                        Column(Modifier.weight(1f)) {
                            Text("${candidate.name} · ${candidate.symbol}", fontWeight = FontWeight.SemiBold)
                            Text(candidate.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { openKline(candidate.symbol) }) { Text("K线") }
                    }
                }
            }
            report.aiExplanation?.let { explanation ->
                ContentCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("AI 中文解释", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Markdown(explanation)
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidatesScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val candidates by viewModel.candidates.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val latestRunId = reports.firstOrNull()?.runId
    val visible = candidates.filter { latestRunId == null || it.runId == latestRunId }
    ScreenColumn(modifier) {
        Text("候选完全按本地确定性评分排序，不代表交易指令。")
        if (visible.isEmpty()) EmptyState("暂无候选", "完成一次研究后会在这里显示评分结果。")
        visible.forEachIndexed { index, candidate ->
            ListItemSurface {
                Column(Modifier.weight(1f)) {
                    Text((index + 1).toString() + ". " + candidate.name + " · " + candidate.symbol, fontWeight = FontWeight.SemiBold)
                    Text(candidate.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(candidate.score.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    Text(candidate.risk, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PortfolioScreen(viewModel: StandaloneViewModel, modifier: Modifier) {
    val portfolios by viewModel.portfolios.collectAsState()
    val runs by viewModel.researchRuns.collectAsState()
    val portfolio = portfolios.firstOrNull()
    val run = portfolio?.let { selected -> runs.firstOrNull { it.id == selected.runId } }
    val positions = remember(portfolio?.holdingsJson) {
        portfolio?.holdingsJson?.let {
            runCatching { Json.decodeFromString<List<Map<String, String>>>(it) }.getOrDefault(emptyList())
        }.orEmpty()
    }
    ScreenColumn(modifier) {
        Text("模拟组合仅用于研究观察，不会向券商或服务器提交订单。")
        if (portfolio == null) EmptyState("暂无模拟组合", "满足买入门槛且预算至少覆盖 1 手后会显示在这里。")
        portfolio?.let { selected ->
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(selected.name + " · 评分 " + selected.score, fontWeight = FontWeight.Bold)
                    Text(
                        "${positions.size} 只 · 预算 ${run?.totalBudget ?: "--"} 元 · ${formatTime(selected.createdAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            positions.forEach { position ->
                ListItemSurface {
                    Column(Modifier.weight(1f)) {
                        Text("${position["name"]} · ${position["symbol"]}", fontWeight = FontWeight.SemiBold)
                        Text(
                            "入场 ${position["entry_low"]}–${position["entry_high"]} · 止损 ${position["stop_loss"]} · 止盈 ${position["take_profit"]}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${position["quantity"]} 股", fontWeight = FontWeight.SemiBold)
                        Text("${position["planned_amount"]} 元", style = MaterialTheme.typography.labelSmall)
                    }
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
        if (entries.isEmpty()) EmptyState("暂无卖出研究", "请先添加持仓，然后刷新卖出研究。")
        entries.forEach {
            ContentCard(modifier = Modifier.fillMaxWidth()) {
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
        if (notifications.isEmpty()) EmptyState("暂无通知", "价格提醒和研究进度会保存在本机。")
        notifications.forEach {
            ContentCard(
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
            ContentCard(
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
private fun SettingsScreen(
    viewModel: StandaloneViewModel,
    modifier: Modifier,
    onSwitchToConnected: () -> Unit,
    permissionState: DevicePermissionState,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onNavigateToAiAgents: () -> Unit,
    navigate: (String) -> Unit = {},
) {
    val context = viewModel.appContext
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val batteryLevel by viewModel.batteryLevel.collectAsState()
    val isCharging by viewModel.isCharging.collectAsState()
    val isPowerSaveMode by viewModel.isPowerSaveMode.collectAsState()
    val thermalStatus by viewModel.thermalStatus.collectAsState()
    val sharedDataStore = remember { SharedDataStore(context) }
    val sharedSettings by sharedDataStore.sharedSettings.collectAsState(initial = SharedDataStore.SharedSettings())
    val providers by viewModel.aiProviders.collectAsState()
    val aiTestResult by viewModel.aiTestResult.collectAsState()
    var intervalText by rememberSaveable { mutableStateOf(settings.monitoringIntervalSeconds.toString()) }
    var exportPassphrase by rememberSaveable { mutableStateOf("") }
    var importPassphrase by rememberSaveable { mutableStateOf("") }
    var archiveStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingArchiveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var archivePreview by remember { mutableStateOf<ArchiveMergePreview?>(null) }
    var archiveConflictChoice by rememberSaveable { mutableStateOf("KEEP_LOCAL") }
    var archiveBusy by rememberSaveable { mutableStateOf(false) }
    var focusCapabilities by remember { mutableStateOf<FocusCapabilities?>(null) }
    LaunchedEffect(Unit) {
        focusCapabilities = withContext(Dispatchers.IO) { FocusNotification.capabilities(context) }
    }
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
                archiveBusy = true
                archiveStatus = runCatching {
                    val data = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("无法读取所选文件")
                    val preview = viewModel.previewArchive(data, importPassphrase.toCharArray())
                    pendingArchiveBytes = data
                    archivePreview = preview
                    "已生成导入预览，请确认后合并"
                }.getOrElse { it.message ?: "导入失败" }
                archiveBusy = false
            }
        }
    }
    ScreenColumn(modifier) {
        ContentCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("当前工作区：独立版", fontWeight = FontWeight.SemiBold)
                Text(
                    "切换到连接版后登录服务端；本地数据、任务和 Keystore 密钥保持隔离。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = onSwitchToConnected,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CloudQueue, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("切换到连接版")
                }
            }
        }
        PermissionCard(
            state = permissionState,
            onRequestNotifications = onRequestNotifications,
            onOpenBatterySettings = onOpenBatterySettings,
        )
        SectionTitle("通知与后台")
        SwitchRow("持仓监控", settings.monitoringEnabled, onChange = viewModel::setMonitoringEnabled)
        SwitchRow("提醒开关", settings.alertsEnabled, onChange = viewModel::setAlertsEnabled)
        SwitchRow("超级岛 v3 载荷", settings.islandEnabled, onChange = viewModel::setIslandEnabled)
        ContentCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("实时动态通知", fontWeight = FontWeight.SemiBold)
                Text("焦点协议 ${focusCapabilities?.protocolVersion?.takeIf { it > 0 }?.let { "v$it" } ?: "未识别"}")
                Text("焦点权限 ${if (focusCapabilities?.focusPermissionGranted == true) "已开启" else "未开启或不可查询"}")
                Text("超级岛 App ID ${if (focusCapabilities?.appIdConfigured == true) "已配置" else "未配置"}")
                Text(
                    if (focusCapabilities?.superIslandReady == true) "本机条件就绪" else "未满足时自动显示普通通知",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
        InfoCard(
            title = "资源状态",
            text = "无任务及非交易时段不请求行情。前台服务超时后会停止并降级为约 15 分钟 WorkManager 检查。目标：监控 CPU < 1.5%，PSS < 120 MiB；系统策略可能仍会终止进程。",
        )
        ContentCard(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("当前调度", fontWeight = FontWeight.SemiBold)
                Text("电量 $batteryLevel% · ${if (isCharging) "充电中" else "未充电"}")
                Text("系统省电：${if (isPowerSaveMode) "开启，降低动画与轮询" else "未开启"}")
                Text(
                    "热状态：" + when {
                        thermalStatus >= 4 -> "严重，暂停非必要任务"
                        thermalStatus >= 3 -> "受限，降低后台频率"
                        thermalStatus >= 2 -> "温热"
                        else -> "正常"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        SectionTitle("外观与动效")
        ContentCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("主题", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                        FilterChip(
                            selected = settings.darkMode == value,
                            onClick = { viewModel.setDarkMode(value) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        SwitchRow("液态玻璃", settings.glassEnabled, onChange = viewModel::setGlassEnabled)
        SwitchRow("完整动画", settings.fullAnimationsEnabled, onChange = viewModel::setFullAnimationsEnabled)
        if (isPowerSaveMode) {
            Text(
                "系统省电模式已覆盖材质与动效设置，当前使用简化材质和即时切换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        ContentCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("工作区共享", style = MaterialTheme.typography.titleSmall)
                Text("在连接版和独立版之间同步外观偏好。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SwitchRow(
                    "共享液态玻璃",
                    sharedSettings.shareGlassEffect,
                    onChange = { enabled ->
                        scope.launch {
                            sharedDataStore.updateSharedSettings(sharedSettings.copy(shareGlassEffect = enabled))
                            if (enabled) sharedDataStore.syncGlassEffectEnabled(settings.glassEnabled)
                        }
                    },
                )
                SwitchRow(
                    "共享完整动画",
                    sharedSettings.shareFullAnimations,
                    onChange = { enabled ->
                        scope.launch {
                            sharedDataStore.updateSharedSettings(sharedSettings.copy(shareFullAnimations = enabled))
                            if (enabled) sharedDataStore.syncFullAnimationsEnabled(settings.fullAnimationsEnabled)
                        }
                    },
                )
                Button(
                    onClick = {
                        scope.launch {
                            sharedDataStore.syncThemeMode(settings.darkMode)
                            sharedDataStore.syncAppearance(settings.glassEnabled, settings.fullAnimationsEnabled)
                            archiveStatus = "外观偏好已同步到共享存储"
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("立即同步外观偏好") }
            }
        }
        AdaptiveActionRow {
            OutlinedButton(onClick = viewModel::clearMarketCache) { Text("清除缓存") }
            OutlinedButton(onClick = viewModel::testMarketProvider) { Text("测试行情源") }
            OutlinedButton(onClick = viewModel::showIslandTest) { Text("测试超级岛") }
        }
        OutlinedButton(
            onClick = onOpenAppSettings,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("打开应用系统设置") }
        Text(
            "小米/HyperOS 用户还需在系统设置中开启自启动；系统后台策略仍可能终止进程。",
            style = MaterialTheme.typography.labelMedium,
        )

        SectionTitle("AI Provider（本机 Keystore 加密）")

        // AI Provider 管理入口 - 新的独立页面
        OutlinedButton(
            onClick = { navigate("ai_providers") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.CloudQueue, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("AI Provider 供应商配置")
        }

        // AI Agent 配置入口
        OutlinedButton(
            onClick = onNavigateToAiAgents,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.SmartToy, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("AI Agent 多模型配置")
        }

        InfoCard(
            title = "AI 配置说明",
            text = "先配置 AI Provider 添加供应商，然后在 AI Agent 中为不同任务分配专门的模型。支持多供应商、健康检查、故障转移和自动缓存。",
        )

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
            enabled = !archiveBusy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("导入 .ashare-local") }
        archivePreview?.let { preview ->
            ContentCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("导入预览", fontWeight = FontWeight.SemiBold)
                    Text("新增 ${preview.additions.size}，更新 ${preview.updates.size}，冲突 ${preview.conflicts.size}，删除 ${preview.deletions.size}")
                    if (preview.conflicts.isNotEmpty()) {
                        Text("冲突处理", style = MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = archiveConflictChoice == "KEEP_LOCAL",
                                onClick = { archiveConflictChoice = "KEEP_LOCAL" },
                                label = { Text("保留本地") },
                            )
                            FilterChip(
                                selected = archiveConflictChoice == "KEEP_IMPORTED",
                                onClick = { archiveConflictChoice = "KEEP_IMPORTED" },
                                label = { Text("使用导入") },
                            )
                        }
                    }
                    Button(
                        onClick = {
                            val bytes = pendingArchiveBytes ?: return@Button
                            scope.launch {
                                archiveBusy = true
                                archiveStatus = runCatching {
                                    val resolution = if (archiveConflictChoice == "KEEP_IMPORTED") {
                                        ArchiveMergeResolution.KEEP_IMPORTED
                                    } else {
                                        ArchiveMergeResolution.KEEP_LOCAL
                                    }
                                    val resolutions = preview.conflicts.associate {
                                        "${it.collection}:${it.key}" to resolution
                                    }
                                    val result = viewModel.applyArchive(
                                        bytes = bytes,
                                        passphrase = importPassphrase.toCharArray(),
                                        preview = preview,
                                        resolutions = resolutions,
                                    )
                                    pendingArchiveBytes = null
                                    archivePreview = null
                                    importPassphrase = ""
                                    result
                                }.getOrElse { it.message ?: "合并失败，可使用相同档案重试" }
                                archiveBusy = false
                            }
                        },
                        enabled = !archiveBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (archiveBusy) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("确认合并")
                    }
                }
            }
        }
        archiveStatus?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable
private fun ScreenColumn(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 720.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContentCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = colors,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = { content() },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actions: @Composable (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actions != null) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = { actions() },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdaptiveActionRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = { content() },
    )
}

@Composable
private fun PermissionCard(
    state: DevicePermissionState,
    onRequestNotifications: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val allReady = state.notificationsGranted && state.batteryUnrestricted
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allReady) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
        border = BorderStroke(
            1.dp,
            if (allReady) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("权限与后台运行", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            PermissionRow(
                title = "通知提醒（必须）",
                description = "价格提醒、研究完成和超级岛降级通知需要此权限。",
                granted = state.notificationsGranted,
                actionLabel = "开启通知",
                onAction = onRequestNotifications,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionRow(
                title = "无限制电量（建议）",
                description = "减少系统在交易时段中断持仓监控的概率。",
                granted = state.batteryUnrestricted,
                actionLabel = "调整电量策略",
                onAction = onOpenBatterySettings,
                iconIsBattery = true,
            )
            Text(
                "联网、前台服务和开机恢复权限随安装授予，无需单独操作。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    iconIsBattery: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = when {
                    granted -> Icons.Outlined.CheckCircle
                    iconIsBattery -> Icons.Outlined.BatterySaver
                    else -> Icons.Outlined.ErrorOutline
                },
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(10.dp))
            Text(title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Text(
                if (granted) "已开启" else "未开启",
                style = MaterialTheme.typography.labelMedium,
                color = if (granted) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!granted) {
            OutlinedButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun SectionTitle(value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
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
    "ai_agents" -> "AI Agent 配置"
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
    "ai_agents",
    "ai_providers",
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

private fun Double.formatMarketNumber(): String = String.format(Locale.US, "%.2f", this)

private fun signedMarketNumber(value: Double): String =
    (if (value > 0) "+" else "") + value.formatMarketNumber()

private fun formatMarketVolume(value: Double?): String = when {
    value == null -> "--"
    value >= 100_000_000 -> String.format(Locale.US, "%.2f 亿", value / 100_000_000)
    value >= 10_000 -> String.format(Locale.US, "%.2f 万", value / 10_000)
    else -> String.format(Locale.US, "%.0f", value)
}

@Composable
private fun stockChangeColor(value: Double?): Color = when {
    value == null -> MaterialTheme.colorScheme.onSurfaceVariant
    value > 0 -> Color(0xFFE53935)
    value < 0 -> Color(0xFF00A86B)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
