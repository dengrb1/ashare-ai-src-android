package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.*
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.normalizeSymbol
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.launch

/** 自选与持仓管理：持仓增删改、账户总资金、监控开关。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val assets by appViewModel.assets.collectAsState()
    val quotes by appViewModel.quotes.collectAsState()
    val scope = rememberCoroutineScope()

    var error by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<PaperPosition?>(null) }
    var showAddPosition by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<PaperPosition?>(null) }

    fun currentRequest(): AssetStateRequest? {
        val a = assets ?: return null
        return AssetStateRequest(
            watchlist = a.watchlist,
            positions = a.positions,
            total_assets = a.total_assets,
            exit_monitor_enabled = a.exit_monitor_enabled,
            default_profit_trigger = a.default_profit_trigger,
            stop_loss_monitor_enabled = a.stop_loss_monitor_enabled,
            buy_monitor_enabled = a.buy_monitor_enabled,
            market_refresh_interval_seconds = a.market_refresh_interval_seconds,
        )
    }

    fun savePositions(positions: List<PaperPosition>) {
        val req = currentRequest() ?: return
        appViewModel.saveAssets(req.copy(positions = positions)) { msg ->
            if (msg != null) error = msg else appViewModel.forceRefresh()
        }
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text("自选与持仓", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { showAddPosition = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新增持仓")
                }
            },
        )

        error?.let {
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) { error = null } }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 总资金
            item {
                TotalAssetsCard(
                    totalAssets = assets?.total_assets,
                    onSave = { value ->
                        val req = currentRequest() ?: return@TotalAssetsCard
                        appViewModel.saveAssets(req.copy(total_assets = value)) { msg -> if (msg != null) error = msg }
                    },
                )
            }

            // 监控开关
            item {
                MonitorCard(
                    assets = assets,
                    onSave = { req ->
                        appViewModel.saveExitMonitor(req) { msg -> if (msg != null) error = msg }
                    },
                )
            }

            item { SectionTitle("模拟持仓（${assets?.positions?.size ?: 0}/15）") }

            val positions = assets?.positions ?: emptyList()
            if (positions.isEmpty()) {
                item { EmptyPlaceholder("暂无持仓记录，点右上角添加") }
            } else {
                items(positions, key = { it.symbol }) { p ->
                    PositionCard(
                        position = p,
                        quote = quotes[p.symbol],
                        onEdit = { editing = p },
                        onDelete = { confirmDelete = p },
                        onExitResearch = {
                            scope.launch {
                                try {
                                    ApiClient.api.manualExitAdvice(newIdempotencyKey(), ManualExitRequest(p.symbol))
                                    error = null
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showAddPosition || editing != null) {
        PositionEditDialog(
            initial = editing,
            onDismiss = { showAddPosition = false; editing = null },
            onSave = { position ->
                val positions = assets?.positions ?: emptyList()
                val newList = if (editing != null) {
                    positions.map { if (it.symbol == editing!!.symbol) position else it }
                } else {
                    if (positions.size >= 15) {
                        error = "持仓最多 15 只"
                        showAddPosition = false
                        return@PositionEditDialog
                    }
                    if (positions.any { it.symbol == position.symbol }) {
                        error = "该股票已在持仓中"
                        showAddPosition = false
                        return@PositionEditDialog
                    }
                    positions + position
                }
                savePositions(newList)
                showAddPosition = false
                editing = null
            },
        )
    }

    confirmDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除持仓") },
            text = { Text("确定删除 ${p.name ?: p.symbol} 的持仓记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    savePositions((assets?.positions ?: emptyList()).filter { it.symbol != p.symbol })
                    confirmDelete = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TotalAssetsCard(totalAssets: Double?, onSave: (Double?) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("账户总资金", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(totalAssets.fmtAmount(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = { input = totalAssets?.toString() ?: ""; editing = true }) { Text("修改") }
        }
    }

    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("账户总资金") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("金额（元）") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onSave(input.toDoubleOrNull())
                    editing = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun MonitorCard(assets: AssetState?, onSave: (ExitMonitorRequest) -> Unit) {
    AppCard {
        Text("监控设置", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        MonitorSwitch("浮盈退出监控", assets?.exit_monitor_enabled == true) { enabled ->
            onSave(
                ExitMonitorRequest(
                    exit_monitor_enabled = enabled,
                    default_profit_trigger = assets?.default_profit_trigger,
                    stop_loss_monitor_enabled = assets?.stop_loss_monitor_enabled,
                    buy_monitor_enabled = assets?.buy_monitor_enabled,
                )
            )
        }
        MonitorSwitch("止损预警", assets?.stop_loss_monitor_enabled == true) { enabled ->
            onSave(
                ExitMonitorRequest(
                    exit_monitor_enabled = assets?.exit_monitor_enabled == true,
                    default_profit_trigger = assets?.default_profit_trigger,
                    stop_loss_monitor_enabled = enabled,
                    buy_monitor_enabled = assets?.buy_monitor_enabled,
                )
            )
        }
        MonitorSwitch("自选买入区间监控", assets?.buy_monitor_enabled == true) { enabled ->
            onSave(
                ExitMonitorRequest(
                    exit_monitor_enabled = assets?.exit_monitor_enabled == true,
                    default_profit_trigger = assets?.default_profit_trigger,
                    stop_loss_monitor_enabled = assets?.stop_loss_monitor_enabled,
                    buy_monitor_enabled = enabled,
                )
            )
        }
    }
}

@Composable
private fun MonitorSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PositionCard(
    position: PaperPosition,
    quote: Quote?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExitResearch: () -> Unit,
) {
    val price = quote?.price
    val marketValue = (price ?: position.cost) * position.quantity
    val pnl = if (price != null) (price - position.cost) * position.quantity else null
    val pnlPct = if (price != null && position.cost > 0) (price - position.cost) / position.cost * 100 else null

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(position.name ?: position.symbol, style = MaterialTheme.typography.titleSmall)
                Text(position.symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                ChangeText(pnl, pnl.fmtSigned(), MaterialTheme.typography.titleSmall, FontWeight.SemiBold)
                ChangeText(pnl, pnlPct.fmtPercent(), MaterialTheme.typography.labelMedium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PosStat("持仓", position.quantity.fmtVolume())
            PosStat("成本", position.cost.fmt2())
            PosStat("现价", price.fmt2())
            PosStat("市值", marketValue.fmtAmount())
        }
        if (position.stop_loss_price != null || position.exit_trigger_price != null) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                position.stop_loss_price?.let { TagPill("止损 ${it.fmt2()}", MaterialTheme.colorScheme.error) }
                position.exit_trigger_price?.let { TagPill("触发 ${it.fmt2()}") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onExitResearch, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("退出研究") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, contentDescription = "编辑", modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = "删除", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PosStat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

/** 持仓编辑对话框，校验规则对齐后端：数量>0、成本>0、手动止损须在成本 5%-10% 下方。 */
@Composable
private fun PositionEditDialog(
    initial: PaperPosition?,
    onDismiss: () -> Unit,
    onSave: (PaperPosition) -> Unit,
) {
    var symbol by remember { mutableStateOf(initial?.symbol ?: "") }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var quantity by remember { mutableStateOf(initial?.quantity?.toString() ?: "") }
    var cost by remember { mutableStateOf(initial?.cost?.toString() ?: "") }
    var stopLoss by remember { mutableStateOf(initial?.stop_loss_price?.toString() ?: "") }
    var triggerPrice by remember { mutableStateOf(initial?.exit_trigger_price?.toString() ?: "") }
    var stopLossEnabled by remember { mutableStateOf(initial?.stop_loss_enabled ?: true) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新增持仓" else "编辑持仓") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = symbol,
                    onValueChange = { symbol = it },
                    label = { Text("代码") },
                    placeholder = { Text("600000 或 600000.SH") },
                    singleLine = true,
                    enabled = initial == null,
                )
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称（可选）") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity, onValueChange = { quantity = it },
                        label = { Text("数量(股)") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = cost, onValueChange = { cost = it },
                        label = { Text("成本价") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = stopLoss, onValueChange = { stopLoss = it },
                        label = { Text("手动止损价") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = triggerPrice, onValueChange = { triggerPrice = it },
                        label = { Text("触发价") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用止损", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = stopLossEnabled, onCheckedChange = { stopLossEnabled = it })
                }
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val normalized = normalizeSymbol(symbol)
                if (normalized == null) {
                    error = "代码格式不正确"
                    return@TextButton
                }
                val q = quantity.toDoubleOrNull()
                val c = cost.toDoubleOrNull()
                if (q == null || q <= 0) { error = "数量必须大于 0"; return@TextButton }
                if (c == null || c <= 0) { error = "成本价必须大于 0"; return@TextButton }
                val sl = stopLoss.toDoubleOrNull()
                if (stopLoss.isNotBlank() && sl == null) { error = "止损价格式不正确"; return@TextButton }
                if (sl != null) {
                    val lo = c * 0.90
                    val hi = c * 0.95
                    if (sl < lo || sl > hi) {
                        error = "手动止损须在成本价下方 5%-10%（${lo.fmt2()} ~ ${hi.fmt2()}）"
                        return@TextButton
                    }
                }
                onSave(
                    PaperPosition(
                        symbol = normalized,
                        name = name.ifBlank { null },
                        quantity = q,
                        cost = c,
                        stop_loss_price = sl,
                        stop_loss_mode = if (sl != null) "MANUAL" else initial?.stop_loss_mode,
                        exit_trigger_price = triggerPrice.toDoubleOrNull(),
                        stop_loss_enabled = stopLossEnabled,
                        acquired_on = initial?.acquired_on,
                        profit_trigger_amount = initial?.profit_trigger_amount,
                        target_weight = initial?.target_weight,
                    )
                )
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
