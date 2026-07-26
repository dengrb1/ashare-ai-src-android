package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.ResearchRequest
import com.ashareai.app.data.model.Run
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 每日研究：发起研究任务 + 最近运行进度（活动任务 2.5s 轮询）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResearchScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val assets by appViewModel.assets.collectAsState()

    var runs by remember { mutableStateOf<List<Run>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSubmit by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }

    suspend fun refresh() {
        try {
            runs = ApiClient.api.researchRuns(limit = 20, mine = true)
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refresh() }

    // 有活动任务时轮询
    LaunchedEffect(runs.any { isActiveStatus(it.status) }) {
        while (runs.any { isActiveStatus(it.status) }) {
            delay(2500)
            refresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("每日研究", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                TextButton(onClick = { showSubmit = true }) { Text("发起研究") }
            },
        )

        error?.let { Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) { scope.launch { refresh() } } } }

        if (loading) {
            LoadingBox()
        } else if (runs.isEmpty()) {
            EmptyPlaceholder("暂无研究记录\n点击右上角发起研究")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(runs, key = { it.run_id }) { run ->
                    RunCard(
                        run = run,
                        onCancel = {
                            scope.launch {
                                try {
                                    ApiClient.api.cancelResearch(run.run_id)
                                    refresh()
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                }
                            }
                        },
                        onOpenReport = {
                            run.trading_date?.let { date ->
                                navController.navigate("reports?date=$date&run_id=${run.run_id}")
                            }
                        },
                    )
                }
            }
        }
    }

    if (showSubmit) {
        SubmitResearchDialog(
            watchlist = assets?.watchlist ?: emptyList(),
            submitting = submitting,
            onDismiss = { showSubmit = false },
            onSubmit = { request ->
                submitting = true
                scope.launch {
                    try {
                        ApiClient.api.submitResearch(newIdempotencyKey(), request)
                        showSubmit = false
                        refresh()
                    } catch (e: Exception) {
                        error = e.toUserMessage()
                        showSubmit = false
                    } finally {
                        submitting = false
                    }
                }
            },
        )
    }
}

@Composable
fun RunCard(run: Run, onCancel: (() -> Unit)? = null, onOpenReport: (() -> Unit)? = null) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${run.trading_date ?: "--"} · ${scopeLabel(run.research_scope)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    run.run_id.take(18),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(statusLabel(run.status), statusColor(run.status))
        }

        if (isActiveStatus(run.status)) {
            Spacer(Modifier.height(10.dp))
            val progress = (run.progress ?: 0) / 100f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${run.phase ?: "处理中"} · ${run.progress ?: 0}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        run.error_message?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(6.dp))
        Row {
            if (isActiveStatus(run.status) && onCancel != null) {
                TextButton(onClick = onCancel, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("取消", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            if (run.report_id != null && onOpenReport != null) {
                TextButton(onClick = onOpenReport, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("查看报告")
                }
            }
        }
    }
}

private fun scopeLabel(scope: String?): String = when (scope?.uppercase()) {
    "MARKET" -> "全市场"
    "WATCHLIST" -> "自选股"
    "CUSTOM" -> "自定义"
    else -> scope ?: "研究"
}

@Composable
private fun SubmitResearchDialog(
    watchlist: List<String>,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (ResearchRequest) -> Unit,
) {
    var scope by remember { mutableStateOf("WATCHLIST") }
    var customSymbols by remember { mutableStateOf("") }
    var totalBudget by remember { mutableStateOf("") }
    var perSymbolBudget by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发起研究") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("交易日：${todayTradingDate()}", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WATCHLIST" to "自选", "MARKET" to "全市场", "CUSTOM" to "自定义").forEach { (v, label) ->
                        FilterChip(selected = scope == v, onClick = { scope = v }, label = { Text(label) })
                    }
                }
                if (scope == "WATCHLIST") {
                    Text(
                        "将研究 ${watchlist.size} 只自选股",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (scope == "CUSTOM") {
                    OutlinedTextField(
                        value = customSymbols,
                        onValueChange = { customSymbols = it },
                        label = { Text("股票代码（逗号分隔）") },
                        placeholder = { Text("600000.SH, 000001.SZ") },
                    )
                }
                OutlinedTextField(
                    value = totalBudget, onValueChange = { totalBudget = it },
                    label = { Text("总资金预算（可选）") }, singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = perSymbolBudget, onValueChange = { perSymbolBudget = it },
                        label = { Text("单股投入") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = maxPrice, onValueChange = { maxPrice = it },
                        label = { Text("最高股价") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !submitting,
                onClick = {
                    val symbols = if (scope == "CUSTOM") {
                        val parsed = customSymbols.split(Regex("[,，\\s]+"))
                            .filter { it.isNotBlank() }
                            .mapNotNull { com.ashareai.app.data.normalizeSymbol(it) }
                        if (parsed.isEmpty()) {
                            error = "请输入有效的股票代码"
                            return@TextButton
                        }
                        parsed
                    } else null
                    onSubmit(
                        ResearchRequest(
                            trading_date = todayTradingDate(),
                            scope = scope,
                            symbols = symbols,
                            total_budget = totalBudget.toDoubleOrNull(),
                            per_symbol_budget = perSymbolBudget.toDoubleOrNull(),
                            max_stock_price = maxPrice.toDoubleOrNull(),
                        )
                    )
                },
            ) {
                if (submitting) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                else Text("提交")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
