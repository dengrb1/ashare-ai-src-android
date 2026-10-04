package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ashareai.app.standalone.data.archive.ArchiveMergePreview
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.sync.SyncDirection
import com.ashareai.app.sync.SyncItem
import com.ashareai.app.sync.SyncResolution
import com.ashareai.app.sync.WorkspaceSyncPreview
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.WorkspaceSyncUiState
import com.ashareai.app.ui.WorkspaceSyncViewModel
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.components.ErrorBanner
import com.ashareai.app.ui.components.KeyValueRow
import com.ashareai.app.ui.components.LoadingBox

@Composable
fun WorkspaceSyncScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val vm: WorkspaceSyncViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "双工作区同步")
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SyncSetupCard(state, vm) }
            item {
                Button(
                    onClick = vm::preview,
                    enabled = !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("读取连接版并生成预览") }
            }
            if (state.loading) item { LoadingBox() }
            state.preview?.let { preview ->
                item { PreviewSummary(preview) }
                if (preview.conflicts.isNotEmpty()) {
                    item { Text("冲突处理（${preview.conflicts.size}）", style = MaterialTheme.typography.titleMedium) }
                    items(preview.conflicts, key = { "${it.collection}:${it.key}" }) { conflict ->
                        ConflictCard(conflict, state, vm)
                    }
                }
                item {
                    Button(
                        onClick = vm::apply,
                        enabled = !state.loading && preview.completeForSelectedScopes,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("确认同步") }
                }
                if (!preview.completeForSelectedScopes) {
                    item {
                        Text(
                            "连接版快照不完整，已暂停确认。请修复读取提示后重新生成预览。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            state.outcome?.let { outcome ->
                item {
                    AppCard {
                        Text("同步结果", style = MaterialTheme.typography.titleSmall)
                        KeyValueRow("幂等键", outcome.idempotencyKey.take(8) + "…")
                        KeyValueRow("本地已应用", outcome.appliedLocal.size.toString())
                        KeyValueRow("连接版已应用", outcome.appliedConnected.size.toString())
                        KeyValueRow("服务端只读", outcome.unsupportedConnected.size.toString())
                        if (outcome.alreadyApplied) Text("该请求已完成，重试不会重复写入。", color = MaterialTheme.colorScheme.primary)
                        outcome.unsupportedConnected.takeIf { it.isNotEmpty() }?.let { scopes ->
                            Text("只读范围：${scopes.joinToString { scopeLabel(it) }}", color = MaterialTheme.colorScheme.error)
                        }
                        outcome.warnings.forEach { warning ->
                            Text(warning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            state.preview?.connected?.warnings?.takeIf { it.isNotEmpty() }?.let { warnings ->
                item {
                    AppCard {
                        Text("读取提示", style = MaterialTheme.typography.titleSmall)
                        warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            state.error?.let { message -> item { ErrorBanner(message, vm::retry) } }
        }
    }
}

@Composable
private fun SyncSetupCard(state: WorkspaceSyncUiState, vm: WorkspaceSyncViewModel) {
    AppCard {
        Text("同步设置", style = MaterialTheme.typography.titleSmall)
        Text(
            "同步由用户发起。先生成差异预览，再确认合并；令牌、AI 密钥、推送凭证、行情缓存和日志始终排除。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text("方向", style = MaterialTheme.typography.labelLarge)
        DirectionRow(state.direction, vm::setDirection)
        Spacer(Modifier.height(10.dp))
        Text("范围", style = MaterialTheme.typography.labelLarge)
        ArchiveScope.all.toList().chunked(2).forEach { rowScopes ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowScopes.forEach { scope ->
                    FilterChip(
                        selected = scope in state.scopes,
                        onClick = { vm.toggleScope(scope) },
                        label = { Text(scopeLabel(scope)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowScopes.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DirectionRow(selected: SyncDirection, onSelected: (SyncDirection) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            SyncDirection.BIDIRECTIONAL to "双向",
            SyncDirection.LOCAL_TO_CONNECTED to "本地 → 连接版",
            SyncDirection.CONNECTED_TO_LOCAL to "连接版 → 本地",
        ).forEach { (direction, label) ->
            FilterChip(
                selected = selected == direction,
                onClick = { onSelected(direction) },
                label = { Text(label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PreviewSummary(preview: WorkspaceSyncPreview) {
    val selected = when (preview.direction) {
        SyncDirection.LOCAL_TO_CONNECTED -> preview.localToConnected
        SyncDirection.CONNECTED_TO_LOCAL -> preview.connectedToLocal
        SyncDirection.BIDIRECTIONAL -> preview.localToConnected.mergeForDisplay(preview.connectedToLocal)
    }
    AppCard {
        Text("同步预览", style = MaterialTheme.typography.titleSmall)
        KeyValueRow("新增", selected.additions.size.toString())
        KeyValueRow("更新", selected.updates.size.toString())
        KeyValueRow("冲突", preview.conflicts.size.toString())
        KeyValueRow("删除", selected.deletions.size.toString())
        Text("冲突默认保留${if (preview.direction == SyncDirection.CONNECTED_TO_LOCAL) "本地" else "连接版"}，可逐条修改。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ArchiveMergePreview.mergeForDisplay(other: ArchiveMergePreview): ArchiveMergePreview =
    ArchiveMergePreview(
        additions = (additions + other.additions).distinctBy { "${it.collection}:${it.key}" },
        updates = (updates + other.updates).distinctBy { "${it.collection}:${it.key}" },
        conflicts = (conflicts + other.conflicts).distinctBy { "${it.collection}:${it.key}" },
        deletions = (deletions + other.deletions).distinctBy { "${it.collection}:${it.key}" },
    )

@Composable
private fun ConflictCard(item: SyncItem, state: WorkspaceSyncUiState, vm: WorkspaceSyncViewModel) {
    val key = "${item.collection}:${item.key}"
    val selected = state.resolutions[key] ?: SyncResolution.KEEP_LOCAL
    AppCard {
        Text("${scopeLabel(item.collection)} · ${item.key}", style = MaterialTheme.typography.titleSmall)
        Text("本地版本：${item.localRevision ?: "无"}   连接版版本：${item.connectedRevision ?: "无"}", style = MaterialTheme.typography.bodySmall)
        ResolutionRow("保留本地", selected == SyncResolution.KEEP_LOCAL) { vm.resolve(item.collection, item.key, SyncResolution.KEEP_LOCAL) }
        ResolutionRow("保留连接版", selected == SyncResolution.KEEP_CONNECTED) { vm.resolve(item.collection, item.key, SyncResolution.KEEP_CONNECTED) }
        ResolutionRow("跳过此条", selected == SyncResolution.SKIP) { vm.resolve(item.collection, item.key, SyncResolution.SKIP) }
    }
}

@Composable
private fun ResolutionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        RadioButton(selected = selected, onClick = onClick)
    }
}

private fun scopeLabel(scope: String): String = when (scope) {
    ArchiveScope.HOLDINGS, "holdings" -> "持仓"
    ArchiveScope.WATCHLIST, "watchlist" -> "自选"
    ArchiveScope.ALERTS, "alerts" -> "提醒"
    ArchiveScope.TRADING_RESEARCH, "researchRuns", "candidates", "notifications" -> "研究记录"
    ArchiveScope.REPORTS, "reports" -> "报告"
    ArchiveScope.SIMULATION_PORTFOLIOS, "simulationPortfolios" -> "模拟组合"
    ArchiveScope.BACKTESTS, "backtests", "backtestTrades" -> "回测"
    else -> scope
}
