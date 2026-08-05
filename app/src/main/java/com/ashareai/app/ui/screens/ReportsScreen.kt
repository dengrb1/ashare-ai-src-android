package com.ashareai.app.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.*
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 研究报告页：日报正文（WebView 沙箱）+ 逐股详情 + 生成买入方案。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    appViewModel: AppViewModel,
    navController: NavHostController,
    initialDate: String? = null,
    initialRunId: String? = null,
) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initialDate ?: todayTradingDate()) }
    var runId by remember { mutableStateOf(initialRunId) }
    var report by remember { mutableStateOf<Report?>(null) }
    var content by remember { mutableStateOf<String?>(null) }
    var symbols by remember { mutableStateOf<List<ReportSymbol>>(emptyList()) }
    var tradePlans by remember { mutableStateOf<List<TradePlan>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var selectedSymbol by remember { mutableStateOf<ReportSymbol?>(null) }
    var sortOptionName by rememberSaveable { mutableStateOf(StockSortOption.SCORE_DESC.name) }
    val sortOption = StockSortOption.valueOf(sortOptionName)

    suspend fun load() {
        loading = true
        error = null
        try {
            val r = ApiClient.api.report(date, runId = runId)
            report = r
            val reportId = r.report_id
            if (reportId != null) {
                coroutineScope {
                    val contentDeferred = async {
                        runCatching {
                            val c = ApiClient.api.reportContent(reportId)
                            c.content ?: c.body
                        }.getOrNull()
                    }
                    val symbolsDeferred = async {
                        runCatching { ApiClient.api.reportSymbols(reportId) }.getOrDefault(emptyList())
                    }
                    val plansDeferred = async {
                        runCatching { ApiClient.api.reportTradePlans(reportId) }.getOrDefault(emptyList())
                    }
                    content = contentDeferred.await()
                    symbols = symbolsDeferred.await()
                    tradePlans = plansDeferred.await()
                }
            }
        } catch (e: Exception) {
            error = e.toUserMessage()
            report = null
            content = null
            symbols = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(date) { load() }

    // 有生成中的方案时轮询
    LaunchedEffect(tradePlans.any { isActiveStatus(it.status) }) {
        while (tradePlans.any { isActiveStatus(it.status) }) {
            delay(2500)
            report?.report_id?.let { id ->
                try {
                    tradePlans = ApiClient.api.reportTradePlans(id)
                } catch (_: Exception) {
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

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }

        if (loading) {
            LoadingBox()
        } else if (report?.report_id == null) {
            EmptyPlaceholder("该交易日暂无报告")
        } else {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("日报正文") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("研究个股(${symbols.size})") })
            }
            when (selectedTab) {
                0 -> ReportContentView(content)
                1 -> SymbolListView(
                    symbols = symbols,
                    tradePlans = tradePlans,
                    sortOption = sortOption,
                    onSortOptionChange = { sortOptionName = it.name },
                    onSelect = { selectedSymbol = it },
                    onSubmitPlan = { symbol ->
                        report?.report_id?.let { reportId ->
                            scope.launch {
                                try {
                                    ApiClient.api.submitTradePlan(
                                        reportId, newIdempotencyKey(),
                                        TradePlanRequest(symbols = listOf(symbol)),
                                    )
                                    tradePlans = ApiClient.api.reportTradePlans(reportId)
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                }
                            }
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ReportContentView(html: String?) {
    if (html.isNullOrBlank()) {
        EmptyPlaceholder("报告正文为空")
        return
    }
    // 与 Web 端 sandbox iframe 等价的隔离：禁 JS、禁文件访问、只渲染静态 HTML
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.blockNetworkLoads = false
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
        },
        modifier = Modifier.fillMaxSize(),
    )
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
                        ) { Text("生成买入方案") }
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
