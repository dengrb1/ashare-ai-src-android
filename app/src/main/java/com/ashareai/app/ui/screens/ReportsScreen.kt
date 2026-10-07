package com.ashareai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import com.ashareai.app.data.model.*
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** 研究报告页：结构化日报 + 逐股详情 + 生成模拟方案。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    appViewModel: AppViewModel,
    navController: NavHostController,
    initialDate: String? = null,
    initialRunId: String? = null,
) {
    val reportsViewModel: ReportsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val reportsState by reportsViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var date by remember { mutableStateOf(initialDate ?: todayTradingDate()) }
    var runId by remember { mutableStateOf(initialRunId) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedSymbol by remember { mutableStateOf<ReportSymbol?>(null) }
    var sortOptionName by rememberSaveable { mutableStateOf(StockSortOption.SCORE_DESC.name) }
    val sortOption = StockSortOption.valueOf(sortOptionName)

    val reportContent = when (val state = reportsState) {
        is ScreenState.Content -> state.value
        is ScreenState.Error -> state.previous
        ScreenState.Loading, ScreenState.Empty -> null
    }
    val report = reportContent?.report
    val symbols = reportContent?.symbols.orEmpty()
    val tradePlans = reportContent?.tradePlans.orEmpty()
    val loading = reportsState is ScreenState.Loading
    val serverError = (reportsState as? ScreenState.Error)?.message

    LaunchedEffect(date) { reportsViewModel.load(date, runId) }

    // 有生成中的方案时轮询
    val hasActiveTradePlan = tradePlans.any { isActiveStatus(it.status) }
    LaunchedEffect(lifecycleOwner, hasActiveTradePlan) {
        if (!hasActiveTradePlan) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(2500)
                report?.report_id?.let { id ->
                    reportsViewModel.refreshTradePlans(id)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CompactTopBar(
            title = "研究报告",
            navigation = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
        )

        // 日期选择
        DateSelectorField(
            value = date,
            onValueChange = { date = it; runId = null },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        (serverError ?: error)?.let { message ->
            Box(Modifier.padding(16.dp)) { ErrorBanner(message) { error = null; reportsViewModel.retry(date, runId) } }
        }

        if (loading) {
            LoadingBox()
        } else if (report?.report_id == null) {
            EmptyPlaceholder("该交易日暂无报告")
        } else {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("日报正文") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("研究个股(${symbols.size})") })
            }
            report.market_index_snapshot?.let { snapshot ->
                MarketIndexSnapshotSummary(snapshot, Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            when (selectedTab) {
                0 -> StructuredReportView(report.result, symbols)
                1 -> SymbolListView(
                    symbols = symbols,
                    tradePlans = tradePlans,
                    sortOption = sortOption,
                    onSortOptionChange = { sortOptionName = it.name },
                    onSelect = { selectedSymbol = it },
                    onSubmitPlan = { symbol ->
                        report.report_id.let { reportId ->
                            reportsViewModel.submitTradePlan(reportId, symbol) { message -> error = message }
                        }
                    },
                )
            }
        }
    }

    selectedSymbol?.let { sym ->
        SymbolDetailSheet(symbol = sym, onDismiss = { selectedSymbol = null })
    }
}

@Composable
private fun StructuredReportView(result: JsonObject, symbols: List<ReportSymbol>) {
    val status = result.stringValue("run_status")
    val decisionAt = result.stringValue("decision_at")
    val buyDate = result.stringValue("buy_execution_date")
    val sellDate = result.stringValue("t1_earliest_sell_date")
    val risks = result.arrayValue("risks")
    val eligible = result.arrayValue("formal_eligible_symbols")
    val quality = result.objectValue("quality_summary")
    val reason = result.stringValue("risk_reason_message")

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (result.isEmpty()) {
            EmptyPlaceholder(
                if (symbols.isEmpty()) "该报告没有可展示的结构化内容" else "报告摘要暂不可用，请切换到研究个股查看结果",
            )
            return@Column
        }
        Text("研究结论", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        KeyValueRow("运行状态", reportStatusLabel(status))
        KeyValueRow("决策时间", formatReportValue(decisionAt))
        KeyValueRow("买入执行日", formatReportValue(buyDate))
        KeyValueRow("最早 T+1 卖出日", formatReportValue(sellDate))
        KeyValueRow("正式候选", "${eligible.size} 只")
        KeyValueRow("研究个股", "${symbols.size} 只")
        reason?.takeIf(String::isNotBlank)?.let {
            Text("风险说明", style = MaterialTheme.typography.titleSmall)
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        if (risks.isNotEmpty()) {
            Text("风险提示", style = MaterialTheme.typography.titleSmall)
            risks.forEach { value -> Text("• ${value.displayValue()}", style = MaterialTheme.typography.bodySmall) }
        }
        if (quality.isNotEmpty()) {
            Text("数据质量", style = MaterialTheme.typography.titleSmall)
            quality.forEach { (key, value) -> KeyValueRow(reportFieldLabel(key), value.displayValue()) }
        }
        Text(
            "报告采用后端冻结的结构化研究结果；逐股票摘要和门禁原因请切换到“研究个股”。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.arrayValue(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
private fun JsonObject.objectValue(key: String): JsonObject = this[key] as? JsonObject ?: JsonObject(emptyMap())

private fun JsonElement.displayValue(): String = when (this) {
    is JsonPrimitive -> contentOrNull ?: toString()
    is JsonArray -> joinToString(", ") { it.displayValue() }
    is JsonObject -> entries.joinToString(" · ") { "${reportFieldLabel(it.key)}: ${it.value.displayValue()}" }
}

private fun formatReportValue(value: String?): String = value?.takeIf(String::isNotBlank) ?: "--"

private fun reportStatusLabel(value: String?): String = when (value) {
    "SUCCEEDED" -> "已完成"
    "FUSED" -> "融合观察"
    "OBSERVE_ONLY" -> "仅观察"
    else -> formatReportValue(value)
}

private fun reportFieldLabel(key: String): String = when (key) {
    "symbol_count" -> "样本数"
    "fundamental_placeholder_count" -> "基本面占位"
    "sentiment_placeholder_count" -> "情绪占位"
    "industry_placeholder_count" -> "行业占位"
    else -> key
}

@Composable
private fun SymbolListView(
    symbols: List<ReportSymbol>,
    tradePlans: List<TradePlan>,
    sortOption: StockSortOption,
    onSortOptionChange: (StockSortOption) -> Unit,
    onSelect: (ReportSymbol) -> Unit,
    onSubmitPlan: (String) -> Unit,
) {
    if (symbols.isEmpty()) {
        EmptyPlaceholder("暂无个股研究数据")
        return
    }
    val sortedSymbols = symbols.sortedForStockDisplay(
        option = sortOption,
        scoreOf = { it.score?.total_score },
        rankOf = { it.rank },
        nameOf = { it.name },
        symbolOf = { it.symbol },
    )
    Column(Modifier.fillMaxSize()) {
        StockSortSelector(
            selected = sortOption,
            onSelected = onSortOptionChange,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(sortedSymbols, key = { it.symbol }) { sym ->
            val plan = tradePlans.firstOrNull { sym.symbol in it.symbols }
            AppCard(modifier = Modifier.clickable { onSelect(sym) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${sym.name ?: sym.symbol}", style = MaterialTheme.typography.titleSmall)
                        Text(sym.symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            sym.score?.total_score.fmt2(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text("综合分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    when (sym.research_status?.uppercase()) {
                        "FORMAL" -> TagPill("正式", androidx.compose.ui.graphics.Color(0xFF00A86B))
                        "FORMAL_WITH_LIMITATIONS" -> TagPill("数据受限", androidx.compose.ui.graphics.Color(0xFFFFA000))
                        "RISK_BLOCKED" -> TagPill("风险禁买", MaterialTheme.colorScheme.error)
                    }
                    sym.rank?.let { TagPill("排名 #$it") }
                    sym.industry_name?.let { TagPill(it, MaterialTheme.colorScheme.secondary) }
                    if (plan != null) {
                        TagPill(
                            if (isActiveStatus(plan.status)) "方案生成中" else "已有方案",
                            MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (sym.advice_eligible && plan == null) {
                    Spacer(Modifier.height(6.dp))
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { onSubmitPlan(sym.symbol) },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { Text("生成模拟方案") }
                    }
                }
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SymbolDetailSheet(symbol: ReportSymbol, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "${symbol.name ?: symbol.symbol}  ${symbol.symbol}",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            symbol.score?.let { s ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    ScoreStat("综合", s.total_score)
                    ScoreStat("基本面", s.fundamental_score)
                    ScoreStat("技术", s.technical_score)
                    ScoreStat("情绪", s.sentiment_score)
                    ScoreStat("质量", s.quality_confidence_score)
                }
                Spacer(Modifier.height(12.dp))
                KeyValueRow("基础分", s.base_total_score.fmt2())
                KeyValueRow("分红加分", s.dividend_bonus.fmt2())
                KeyValueRow("事件风险乘数", s.event_risk_multiplier.fmt2())
                KeyValueRow("大盘状态", marketRegimeLabel(s.market_regime))
                KeyValueRow("大盘评分调整", signedFmt(s.market_score_adjustment))
                KeyValueRow("大盘风险乘数", s.market_risk_multiplier.fmt2())
                KeyValueRow("公式版本", s.formula_version ?: "--")
            }
            symbol.plain_language_summary?.let {
                Spacer(Modifier.height(12.dp))
                Text("省流摘要", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            symbol.component_summaries?.let { cs ->
                Spacer(Modifier.height(12.dp))
                cs.fundamental?.let { SummaryBlock("基本面", it) }
                cs.technical?.let { SummaryBlock("技术面", it) }
                cs.sentiment?.let { SummaryBlock("情绪面", it) }
            }
            if (symbol.exclusion_reasons.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("剔除原因", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                symbol.exclusion_reasons.forEach {
                    Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MarketIndexSnapshotSummary(snapshot: MarketIndexSnapshot, modifier: Modifier = Modifier) {
    AppCard(modifier) {
        Text("冻结大盘指数环境", style = MaterialTheme.typography.titleSmall)
        Text(
            "${marketRegimeLabel(snapshot.regime)} · 综合5日 ${snapshot.composite_return_5d?.times(100).fmt2()}% · 调整 ${signedFmt(snapshot.score_adjustment)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        snapshot.indices.forEach { index ->
            KeyValueRow(
                index.name,
                "1日 ${index.return_1d?.times(100).fmt2()}% · 5日 ${index.return_5d?.times(100).fmt2()}% · 20日 ${index.return_20d?.times(100).fmt2()}%",
            )
        }
        Text(
            "指数环境来自本次研究冻结基准，已参与最终分。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun marketRegimeLabel(value: String?): String = when (value) {
    "RISK_ON" -> "风险偏好改善"
    "RISK_OFF" -> "风险偏好收缩"
    "NEUTRAL" -> "大盘中性"
    else -> "大盘数据不足"
}

private fun signedFmt(value: Double?): String = value?.let { if (it >= 0) "+${it.fmt2()}" else it.fmt2() } ?: "--"

@Composable
private fun ScoreStat(label: String, value: Double?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.fmt2(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryBlock(title: String, body: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(2.dp))
        Text(body, style = MaterialTheme.typography.bodySmall)
    }
}
