package com.ashareai.app.ui.screens

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ashareai.app.data.model.*
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate

/** 策略控制台：策略参数、服务端智能迭代、自选报警与模拟盘训练集中管理。 */
@Composable
fun StrategySettingsScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    val market = LocalMarketViewModel.current
    val simulation: SimulationViewModel = viewModel()
    val assets by market.assets.collectAsState()
    val monitors by simulation.buyMonitorState.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var schedule by remember { mutableStateOf<StrategyEvolutionSchedule?>(null) }
    var active by remember { mutableStateOf<JsonObject?>(null) }
    var candidates by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var trainingConfig by remember { mutableStateOf<JsonObject?>(null) }
    var trainingName by rememberSaveable { mutableStateOf("自选股智能训练") }
    var trainingSessions by rememberSaveable { mutableStateOf("160") }
    var historySessions by rememberSaveable { mutableStateOf("240") }
    var entryDiscounts by rememberSaveable { mutableStateOf("0,0.01,0.02") }

    fun reload() = scope.launch {
        error = null
        runCatching {
            val research = appViewModel.container.researchRepository
            Triple(research.strategyEvolutionSchedule(), research.activeStrategyVersion(), research.strategyCandidates())
        }.onSuccess { (newSchedule, newActive, newCandidates) ->
            schedule = newSchedule
            active = newActive
            candidates = newCandidates.versions
        }.onFailure { error = it.toUserMessage() }
        simulation.loadBuyMonitors()
    }
    LaunchedEffect(Unit) { reload() }

    val importTraining = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                appViewModel.screenContext().contentResolver.openInputStream(uri)?.use { stream ->
                    Json.parseToJsonElement(stream.readBytes().decodeToString()).jsonObject
                } ?: error("无法读取训练包")
            }.onSuccess { imported ->
                trainingConfig = imported
                message = "已导入训练配置，可继续提交到服务器"
            }.onFailure { error = "训练包格式无效：${it.message ?: "未知错误"}" }
        }
    }

    fun submitTraining() = scope.launch {
        val symbols = assets?.watchlist.orEmpty()
        if (symbols.isEmpty()) { error = "请先添加自选股，训练数据将只使用自选股真实行情"; return@launch }
        val config = trainingConfig ?: buildJsonObject {
            put("scope", "WATCHLIST")
            put("symbols", JsonArray(symbols.map(::JsonPrimitive)))
            put("training_sessions", trainingSessions.toIntOrNull()?.coerceIn(1, 10000) ?: 160)
            put("history_sessions", historySessions.toIntOrNull()?.coerceIn(30, 2000) ?: 240)
            put("entry_discounts", JsonArray(entryDiscounts.split(',').mapNotNull { it.trim().toDoubleOrNull() }.map(::JsonPrimitive)))
            put("use_real_watchlist_quotes", true)
            put("monitor_buy_entry_alerts", true)
        }
        runCatching {
            appViewModel.container.simulationRepository.submitBacktest(
                BacktestRequest(
                    name = trainingName.ifBlank { "自选股智能训练" },
                    start_date = LocalDate.now().minusYears(2).toString(),
                    end_date = LocalDate.now().toString(),
                    config = config,
                ),
            )
        }.onSuccess { message = "训练任务已提交，完成后可在回测工作台查看并生成入场提醒" }
            .onFailure { error = it.toUserMessage() }
    }

    Column(Modifier.fillMaxSize()) {
        CompactTopBar(
            title = "策略设置",
            navigation = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } },
            actions = { TextButton(onClick = { reload() }) { Text("刷新") } },
        )
        TabRow(selectedTabIndex = tab) {
            listOf("策略参数", "智能迭代", "模拟盘训练", "自选报警").forEachIndexed { index, label ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(label) })
            }
        }
        error?.let { ErrorBanner(it) { error = null } }
        message?.let { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (tab) {
                0 -> item {
                    AppCard {
                        Text("确定性评分策略", style = MaterialTheme.typography.titleSmall)
                        Text("评分、排序、模拟组合和报告使用同一服务端版本；AI 仅解释结果。", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        KeyValueRow("当前版本", active?.get("version")?.jsonPrimitive?.contentOrNull ?: active?.get("version_id")?.jsonPrimitive?.contentOrNull ?: "未知")
                        KeyValueRow("自选股数据", "${assets?.watchlist?.size ?: 0} 只，真实行情")
                        KeyValueRow("买入提醒", if (assets?.buy_monitor_enabled == true) "已开启" else "未开启")
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { tab = 1 }) { Text("管理智能迭代") }
                    }
                }
                1 -> {
                    item {
                        AppCard {
                            Text("服务端智能迭代", style = MaterialTheme.typography.titleSmall)
                            Text("候选版本先进入影子状态，批准后才会影响确定性评分。", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("周度任务")
                                Switch(checked = schedule?.enabled == true, onCheckedChange = { enabled ->
                                    scope.launch { runCatching { appViewModel.container.researchRepository.setStrategyEvolutionSchedule(enabled) }.onSuccess { schedule = it; message = "周度智能迭代已${if (enabled) "开启" else "关闭"}" }.onFailure { error = it.toUserMessage() } }
                                })
                            }
                            schedule?.let { s ->
                                KeyValueRow("运行时间", "每周 ${s.weekday} ${s.hour}:00")
                                KeyValueRow("最小样本 / 最大回撤", "${s.minimum_samples} / ${(s.maximum_drawdown * 100).toInt()}%")
                                KeyValueRow("最大换手 / 候选数", "${s.maximum_turnover} / ${s.maximum_candidates}")
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { scope.launch { runCatching { appViewModel.container.researchRepository.rollbackStrategy() }.onSuccess { message = "已请求回滚上一版本"; reload() }.onFailure { error = it.toUserMessage() } } }) { Text("回滚上一版本") }
                        }
                    }
                    item { Text("候选版本（${candidates.size}）", style = MaterialTheme.typography.titleMedium) }
                    if (candidates.isEmpty()) item { Text("暂无待审核候选", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(candidates, key = { it["candidate_id"]?.jsonPrimitive?.content ?: it.toString() }) { candidate ->
                        AppCard {
                            Text(candidate["candidate_id"]?.jsonPrimitive?.content ?: "候选版本", style = MaterialTheme.typography.titleSmall)
                            Text("公式：${candidate["formula_version"]?.jsonPrimitive?.content ?: candidate["version"]?.jsonPrimitive?.content ?: "未知"}")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val id = candidate["candidate_id"]?.jsonPrimitive?.content ?: candidate["id"]?.jsonPrimitive?.content
                                Button(enabled = id != null, onClick = { scope.launch { runCatching { appViewModel.container.researchRepository.approveStrategy(id!!) }.onSuccess { message = "候选版本已批准"; reload() }.onFailure { error = it.toUserMessage() } } }) { Text("批准") }
                                OutlinedButton(enabled = id != null, onClick = { scope.launch { runCatching { appViewModel.container.researchRepository.rejectStrategy(id!!) }.onSuccess { message = "候选版本已拒绝"; reload() }.onFailure { error = it.toUserMessage() } } }) { Text("拒绝") }
                            }
                        }
                    }
                }
                2 -> item {
                    AppCard {
                        Text("模拟盘智能训练", style = MaterialTheme.typography.titleSmall)
                        Text("训练范围固定为自选股，使用服务器真实历史行情；训练结果只用于模拟盘和入场区间提醒。", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = trainingName, onValueChange = { value -> trainingName = value }, label = { Text("任务名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = trainingSessions, onValueChange = { value -> trainingSessions = value }, label = { Text("训练交易日") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = historySessions, onValueChange = { value -> historySessions = value }, label = { Text("历史窗口") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = entryDiscounts, onValueChange = { value -> entryDiscounts = value }, label = { Text("入场折价候选") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("当前自选：${assets?.watchlist.orEmpty().joinToString(", ").ifBlank { "暂无" }}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { importTraining.launch(arrayOf("application/json", "text/*")) }) { Text("导入训练包") }
                            Button(onClick = { submitTraining() }, enabled = assets?.watchlist?.isNotEmpty() == true) { Text("提交训练") }
                        }
                        trainingConfig?.let { Text("已载入外部训练配置（提交时优先使用）", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                else -> item {
                    val monitorItems = (monitors as? ScreenState.Content)?.value?.monitors.orEmpty()
                    AppCard {
                        Text("自选股报警与推荐入手价", style = MaterialTheme.typography.titleSmall)
                        Text("开启后，服务器会根据训练/交易方案生成次交易日入场区间提醒。", style = MaterialTheme.typography.bodySmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("启用买入提醒")
                            Switch(checked = assets?.buy_monitor_enabled == true, onCheckedChange = { enabled ->
                                val current = assets ?: return@Switch
                                market.saveExitMonitor(ExitMonitorRequest(current.exit_monitor_enabled, current.default_profit_trigger, current.stop_loss_monitor_enabled, enabled)) { msg -> error = msg; if (msg == null) market.refreshAll() }
                            })
                        }
                        KeyValueRow("服务器提醒", "${monitorItems.size} 条")
                        monitorItems.take(10).forEach { monitor ->
                            HorizontalDivider(Modifier.padding(vertical = 6.dp))
                            KeyValueRow(monitor.symbol, "${monitor.entry_low ?: "-"} ~ ${monitor.entry_high ?: "-"} · ${monitor.status}")
                        }
                    }
                }
            }
        }
    }
}
