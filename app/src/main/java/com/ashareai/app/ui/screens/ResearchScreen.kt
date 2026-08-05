package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.data.model.*
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal val researchScopes = listOf(
    "MARKET" to "全市场",
    "WATCHLIST" to "自选与持仓",
    "CUSTOM" to "指定股票",
)

/** 每日研究：手动研究、A/B 自动报告和运行状态集中在一个工作台。 */
@Composable
fun ResearchScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val coroutineScope = rememberCoroutineScope()
    val assets by appViewModel.assets.collectAsState()
    val quotes by appViewModel.quotes.collectAsState()
    val assetSymbols = remember(assets) {
        ((assets?.watchlist ?: emptyList()) + (assets?.positions?.map { it.symbol } ?: emptyList())).distinct()
    }

    var runs by remember { mutableStateOf<List<Run>>(emptyList()) }
    var settings by remember { mutableStateOf<ResearchSettings?>(null) }
    var loading by remember { mutableStateOf(true) }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var cancelTarget by remember { mutableStateOf<Run?>(null) }

    var date by remember { mutableStateOf(todayTradingDate()) }
    var researchScope by remember { mutableStateOf("MARKET") }
    var customSymbols by remember { mutableStateOf("") }
    var assetSearch by remember { mutableStateOf("") }
    var excludedSymbols by remember { mutableStateOf<Set<String>>(emptySet()) }
    var totalBudget by remember { mutableStateOf("1000000") }
    var perSymbolBudget by remember { mutableStateOf("80000") }
    var maxStockPrice by remember { mutableStateOf("") }
    var supremeMode by remember { mutableStateOf(false) }

    suspend fun refresh(loadSettings: Boolean = false) {
        try {
            if (loadSettings) {
                coroutineScope {
                    val runsDeferred = async { ApiClient.api.researchRuns(limit = 20, mine = true) }
                    val settingsDeferred = async { runCatching { ApiClient.api.researchSettings() }.getOrNull() }
                    runs = runsDeferred.await()
                    settingsDeferred.await()?.let { settings = it }
                }
            } else {
                runs = ApiClient.api.researchRuns(limit = 20, mine = true)
            }
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    suspend fun refreshActiveRuns() {
        val activeIds = runs.filter { isActiveStatus(it.status) }.map { it.run_id }
        if (activeIds.isEmpty()) return
        val updates = coroutineScope {
            activeIds.map { runId ->
                async { runCatching { ApiClient.api.researchRun(runId) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
        if (updates.isNotEmpty()) {
            val byId = updates.associateBy { it.run_id }
            runs = runs.map { byId[it.run_id] ?: it }
        }
    }

    LaunchedEffect(Unit) { refresh(loadSettings = true) }
    LaunchedEffect(runs.any { isActiveStatus(it.status) }) {
        var intervalMillis = 2_500L
        while (runs.any { isActiveStatus(it.status) }) {
            delay(intervalMillis)
            refreshActiveRuns()
            intervalMillis = 5_000L
        }
    }

    val parsedCustom = remember(customSymbols) { parseResearchSymbols(customSymbols) }
    val selectedAssets = remember(assetSymbols, excludedSymbols) { assetSymbols.filterNot(excludedSymbols::contains) }
    val visibleAssets = remember(assetSymbols, assetSearch, quotes) {
        val query = assetSearch.trim().uppercase()
        if (query.isBlank()) assetSymbols else assetSymbols.filter {
            it.contains(query) || quotes[it]?.name.orEmpty().uppercase().contains(query)
        }
    }
    val selectedSymbols = when (researchScope) {
        "WATCHLIST" -> selectedAssets
        "CUSTOM" -> parsedCustom
        else -> emptyList()
    }

    Column(Modifier.fillMaxSize()) {
        CompactTopBar(
            title = "每日研究",
            navigation = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { settingsOpen = true }, enabled = settings != null) {
                    Icon(Icons.Outlined.Settings, contentDescription = "自动研究设置")
                }
            },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AutomaticResearchSummary(settings = settings, onOpen = { settingsOpen = true })
            }
            item { SectionTitle("发起研究") }
            item {
                DateSelectorField(value = date, onValueChange = { date = it }, modifier = Modifier.fillMaxWidth())
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    researchScopes.forEach { (value, label) ->
                        FilterChip(
                            selected = researchScope == value,
                            onClick = { researchScope = value },
                            label = { Text(label) },
                        )
                    }
                }
            }
            if (researchScope == "CUSTOM") {
                item {
                    OutlinedTextField(
                        value = customSymbols,
                        onValueChange = { customSymbols = it.take(2_000) },
                        label = { Text("A股代码") },
                        supportingText = { Text("已识别 ${parsedCustom.size} 只，可用逗号、空格或换行分隔") },
                        placeholder = { Text("600519.SH, 000001.SZ") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (researchScope == "WATCHLIST") {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("已选 ${selectedAssets.size}/${assetSymbols.size} 只", style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { excludedSymbols = emptySet() }) { Text("全选") }
                            TextButton(onClick = { excludedSymbols = assetSymbols.toSet() }) { Text("清空") }
                        }
                        OutlinedTextField(
                            value = assetSearch,
                            onValueChange = { assetSearch = it.take(80) },
                            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            placeholder = { Text("搜索名称或代码") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (assetSymbols.isEmpty()) {
                            EmptyPlaceholder("自选与持仓为空，请先添加股票")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            ) {
                                items(visibleAssets, key = { it }) { symbol ->
                                    val checked = symbol !in excludedSymbols
                                    Row(
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Checkbox(
                                            checked = checked,
                                            onCheckedChange = {
                                                excludedSymbols = if (checked) excludedSymbols + symbol else excludedSymbols - symbol
                                            },
                                        )
                                        Column {
                                            Text(quotes[symbol]?.name ?: symbol, style = MaterialTheme.typography.bodyMedium)
                                            Text(symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ResearchBudgetFields(
                    totalBudget = totalBudget,
                    onTotalBudget = { totalBudget = it },
                    perSymbolBudget = perSymbolBudget,
                    onPerSymbolBudget = { perSymbolBudget = it },
                    maxStockPrice = maxStockPrice,
                    onMaxStockPrice = { maxStockPrice = it },
                )
            }
            item {
                Surface(
                    color = if (supremeMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("至高模式", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "仅提升数据采集并行度，服务端会按 CPU 和内存自动收敛；模型并发保持不变。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = supremeMode, onCheckedChange = { supremeMode = it })
                    }
                }
            }
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(10.dp)) {
                        Text("冻结快照由系统强制开启", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "研究范围、预算和数据来源会写入 Manifest；实时行情只用于预览。少于 ${settings?.portfolio_target_count ?: 15} 只仍生成个股报告，但不生成整体组合。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error?.let { message -> item { ErrorBanner(message) { coroutineScope.launch { refresh() } } } }
            item {
                Button(
                    enabled = !submitting,
                    onClick = {
                        val total = positiveNumber(totalBudget)
                        val perSymbol = positiveNumber(perSymbolBudget)
                        val maxPrice = optionalPositiveNumber(maxStockPrice)
                        error = validateResearchInput(
                            scope = researchScope,
                            availableAssetCount = assetSymbols.size,
                            selectedSymbols = selectedSymbols,
                            total = total,
                            perSymbol = perSymbol,
                            maxText = maxStockPrice,
                            maxPrice = maxPrice,
                        )
                        if (error != null) return@Button
                        submitting = true
                        coroutineScope.launch {
                            try {
                                ApiClient.api.submitResearch(
                                    newIdempotencyKey(),
                                    ResearchRequest(
                                        trading_date = date,
                                        scope = researchScope,
                                        symbols = selectedSymbols.takeIf { researchScope != "MARKET" },
                                        total_budget = total,
                                        per_symbol_budget = perSymbol,
                                        max_stock_price = maxPrice,
                                        supreme_mode = supremeMode,
                                    ),
                                )
                                refresh(loadSettings = false)
                            } catch (e: Exception) {
                                error = e.toUserMessage()
                            } finally {
                                submitting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (submitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (submitting) "正在提交" else "启动每日研究")
                }
            }
            item { SectionTitle("最近运行") }
            if (loading && runs.isEmpty()) {
                item { LoadingBox() }
            } else if (runs.isEmpty()) {
                item { EmptyPlaceholder("暂无研究记录") }
            } else {
                items(runs, key = { it.run_id }) { run ->
                    RunCard(
                        run = run,
                        onCancel = if (isActiveStatus(run.status)) ({ cancelTarget = run }) else null,
                        onOpenReport = if (run.report_id != null && run.trading_date != null) ({
                            navController.navigate("reports?date=${run.trading_date}&run_id=${run.run_id}")
                        }) else null,
                    )
                }
            }
        }
    }

    cancelTarget?.let { run ->
        AlertDialog(
            onDismissRequest = { cancelTarget = null },
            title = { Text("停止研究任务？") },
            text = { Text("当前阶段会安全结束后停止。运行 ${run.run_id.take(18)} 不会被立即强制中断。") },
            confirmButton = {
                TextButton(onClick = {
                    cancelTarget = null
                    coroutineScope.launch {
                        try {
                            ApiClient.api.cancelResearch(run.run_id)
                            refresh(loadSettings = false)
                        } catch (e: Exception) {
                            error = e.toUserMessage()
                        }
                    }
                }) { Text("确认停止", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { cancelTarget = null }) { Text("继续运行") } },
        )
    }

    if (settingsOpen && settings != null) {
        AutomaticResearchDialog(
            settings = requireNotNull(settings),
            onDismiss = { settingsOpen = false },
            onSaved = {
                settings = it
                settingsOpen = false
            },
        )
    }
}

@Composable
private fun AutomaticResearchSummary(settings: ResearchSettings?, onOpen: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("自动每日研究", style = MaterialTheme.typography.titleSmall)
                Text(
                    when {
                        settings == null -> "正在读取设置"
                        settings.auto_enabled -> "已启用 ${settings.automatic_reports.count { it.enabled }} 个报告 · 每日 ${settings.schedule_time} 检查"
                        else -> "当前未启用 · 每日 15:05 检查"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen, enabled = settings != null) { Text("设置") }
        }
    }
}

@Composable
private fun ResearchBudgetFields(
    totalBudget: String,
    onTotalBudget: (String) -> Unit,
    perSymbolBudget: String,
    onPerSymbolBudget: (String) -> Unit,
    maxStockPrice: String,
    onMaxStockPrice: (String) -> Unit,
) {
    val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(totalBudget, { onTotalBudget(it.take(18)) }, label = { Text("总资金预算（元）") }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(perSymbolBudget, { onPerSymbolBudget(it.take(18)) }, label = { Text("单股最高投入（元）") }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(maxStockPrice, { onMaxStockPrice(it.take(18)) }, label = { Text("最高可接受股价（可选）") }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
