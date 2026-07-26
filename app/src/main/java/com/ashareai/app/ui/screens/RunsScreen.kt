package com.ashareai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.AuditEvent
import com.ashareai.app.data.model.RunActivity
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

private val typeFilters = listOf(
    null to "全部", "RESEARCH" to "研究", "BACKTEST" to "回测",
    "TRADE_PLAN" to "买入方案", "EXIT_ADVICE" to "卖出建议",
)

/** 运行与审计：活动流（游标分页）+ 审计时间线。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunsScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<RunActivity>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var typeFilter by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<RunActivity?>(null) }
    var auditEvents by remember { mutableStateOf<List<AuditEvent>>(emptyList()) }

    suspend fun load(reset: Boolean) {
        if (reset) {
            loading = true
            cursor = null
        } else {
            loadingMore = true
        }
        try {
            val page = ApiClient.api.runsActivity(
                cursor = if (reset) null else cursor,
                type = typeFilter,
                limit = 20,
            )
            items = if (reset) page.items else items + page.items
            cursor = page.next_cursor
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
            loadingMore = false
        }
    }

    LaunchedEffect(typeFilter) { load(reset = true) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "运行与审计")

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            typeFilters.forEach { (value, label) ->
                FilterChip(
                    selected = typeFilter == value,
                    onClick = { typeFilter = value },
                    label = { Text(label) },
                )
            }
        }

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load(true) } } } }

        if (loading) {
            LoadingBox()
        } else if (items.isEmpty()) {
            EmptyPlaceholder("暂无运行记录")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { "${it.run_id}-${it.activity_type}" }) { activity ->
                    AppCard(modifier = Modifier.clickable {
                        selected = activity
                        auditEvents = emptyList()
                        scope.launch {
                            try {
                                auditEvents = ApiClient.api.runAudit(activity.run_id)
                            } catch (_: Exception) {
                            }
                        }
                    }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    activity.title ?: runTypeLabel(activity.run_type),
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "${activity.trading_date ?: ""} · ${activity.created_at.fmtTime()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            StatusChip(statusLabel(activity.status), statusColor(activity.status))
                        }
                        activity.error_message?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                if (cursor != null) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(
                                onClick = { scope.launch { load(reset = false) } },
                                enabled = !loadingMore,
                            ) {
                                if (loadingMore) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                else Text("加载更多")
                            }
                        }
                    }
                }
            }
        }
    }

    selected?.let { activity ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
            ) {
                Text(activity.title ?: activity.run_id, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                KeyValueRow("运行 ID", activity.run_id.take(24))
                KeyValueRow("类型", runTypeLabel(activity.run_type))
                KeyValueRow("状态", statusLabel(activity.status))
                KeyValueRow("交易日", activity.trading_date ?: "--")
                KeyValueRow("开始", activity.started_at.fmtTime(full = true))
                KeyValueRow("完成", activity.completed_at.fmtTime(full = true))
                if (auditEvents.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("审计时间线", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    auditEvents.take(20).forEach { event ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusChip(
                                    event.event_type ?: "--",
                                    when (event.severity?.uppercase()) {
                                        "HIGH", "CRITICAL", "ERROR" -> MaterialTheme.colorScheme.error
                                        "WARNING" -> androidx.compose.ui.graphics.Color(0xFFFFA000)
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    event.created_at.fmtTime(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            event.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun runTypeLabel(type: String?): String = when (type?.uppercase()) {
    "RESEARCH" -> "研究"
    "BACKTEST" -> "回测"
    "TRADE_PLAN" -> "买入方案"
    "EXIT_ADVICE" -> "卖出建议"
    else -> type ?: "任务"
}
