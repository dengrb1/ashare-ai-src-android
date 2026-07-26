package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.Backtest
import com.ashareai.app.data.model.BacktestRequest
import com.ashareai.app.data.model.Snapshot
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/** 回测工作台：提交回测 + 任务列表（活动任务 3s 轮询）。 */
@Composable
fun BacktestScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var backtests by remember { mutableStateOf<List<Backtest>>(emptyList()) }
    var snapshots by remember { mutableStateOf<List<Snapshot>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSubmit by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            backtests = ApiClient.api.backtests(limit = 20)
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        load()
        try {
            snapshots = ApiClient.api.snapshots()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(backtests.any { isActiveStatus(it.status) }) {
        while (backtests.any { isActiveStatus(it.status) }) {
            delay(3000)
            load()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "回测工作台")

        Row(Modifier.padding(horizontal = 16.dp)) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSubmit = true }) { Text("新建回测") }
        }

        error?.let { Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }

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
                                    scope.launch {
                                        try {
                                            ApiClient.api.retryBacktest(bt.backtest_id)
                                            load()
                                        } catch (e: Exception) {
                                            error = e.toUserMessage()
                                        }
                                    }
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
                scope.launch {
                    try {
                        ApiClient.api.submitBacktest(newIdempotencyKey(), request)
                        load()
                    } catch (e: Exception) {
                        error = e.toUserMessage()
                    }
                }
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
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var selectedSnapshots by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建回测") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = startDate, onValueChange = { startDate = it },
                        label = { Text("开始 yyyy-MM-dd") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = endDate, onValueChange = { endDate = it },
                        label = { Text("结束 yyyy-MM-dd") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
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
