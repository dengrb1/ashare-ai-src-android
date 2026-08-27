package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ashareai.app.data.model.BacktestRequest
import com.ashareai.app.data.model.Snapshot
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonPrimitive

/** 回测工作台：提交回测 + 任务列表（活动任务 3s 轮询）。 */
@Composable
fun BacktestScreen(appViewModel: AppViewModel) {
    val simulationViewModel: SimulationViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val backtestState by simulationViewModel.backtestState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var showSubmit by remember { mutableStateOf(false) }

    val workspace = when (val state = backtestState) {
        is ScreenState.Content -> state.value
        is ScreenState.Error -> state.previous
        ScreenState.Loading, ScreenState.Empty -> null
    }
    val backtests = workspace?.backtests.orEmpty()
    val snapshots = workspace?.snapshots.orEmpty()
    val loading = backtestState is ScreenState.Loading
    val error = (backtestState as? ScreenState.Error)?.message

    LaunchedEffect(Unit) { simulationViewModel.loadBacktests() }

    val hasActiveBacktest = backtests.any { isActiveStatus(it.status) }
    LaunchedEffect(lifecycleOwner, hasActiveBacktest) {
        if (!hasActiveBacktest) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(3000)
                simulationViewModel.refreshBacktests()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "回测工作台")

        Row(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSubmit = true }) { Text("新建回测") }
        }

        error?.let { Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) { simulationViewModel.refreshBacktests() } } }

        if (loading) {
            LoadingBox()
        } else if (backtests.isEmpty()) {
            EmptyPlaceholder("暂无回测任务")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(backtests, key = { it.backtest_id }) { bt ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(bt.name ?: bt.backtest_id.take(12), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${bt.start_date ?: "?"} ~ ${bt.end_date ?: "?"}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusChip(statusLabel(bt.status), statusColor(bt.status))
                        }
                        bt.error_message?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        // 绩效指标
                        bt.metrics?.let { metrics ->
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(6.dp))
                            metrics.entries.take(8).forEach { (k, v) ->
                                KeyValueRow(k, (v as? JsonPrimitive)?.content ?: v.toString())
                            }
                        }
                        if (bt.status.uppercase() == "FAILED") {
                            Spacer(Modifier.height(4.dp))
                            Row {
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = {
                                    simulationViewModel.retryBacktest(bt.backtest_id)
                                }) { Text("重试") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubmit) {
        SubmitBacktestDialog(
            snapshots = snapshots,
            onDismiss = { showSubmit = false },
            onSubmit = { request ->
                showSubmit = false
                simulationViewModel.submitBacktest(request)
            },
        )
    }
}

@Composable
private fun SubmitBacktestDialog(
    snapshots: List<Snapshot>,
    onDismiss: () -> Unit,
    onSubmit: (BacktestRequest) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(java.time.LocalDate.now().minusYears(1).toString()) }
    var endDate by remember { mutableStateOf(todayTradingDate()) }
    var selectedSnapshots by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建回测") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                DateSelectorField(startDate, { startDate = it }, modifier = Modifier.fillMaxWidth(), label = "开始日期")
                DateSelectorField(endDate, { endDate = it }, modifier = Modifier.fillMaxWidth(), label = "结束日期")
                if (snapshots.isNotEmpty()) {
                    Text("选择快照（${selectedSnapshots.size}）", style = MaterialTheme.typography.labelLarge)
                    snapshots.take(8).forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = s.snapshot_id in selectedSnapshots,
                                onCheckedChange = { checked ->
                                    selectedSnapshots = if (checked) selectedSnapshots + s.snapshot_id
                                    else selectedSnapshots - s.snapshot_id
                                },
                            )
                            Text(
                                "${s.trading_date ?: ""} ${s.snapshot_id.take(12)}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || startDate.isBlank() || endDate.isBlank()) {
                    error = "请填写名称与起止日期"
                    return@TextButton
                }
                if (startDate > endDate) {
                    error = "开始日期不能晚于结束日期"
                    return@TextButton
                }
                onSubmit(
                    BacktestRequest(
                        name = name.trim(),
                        start_date = startDate.trim(),
                        end_date = endDate.trim(),
                        snapshot_ids = selectedSnapshots.toList(),
                    )
                )
            }) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
