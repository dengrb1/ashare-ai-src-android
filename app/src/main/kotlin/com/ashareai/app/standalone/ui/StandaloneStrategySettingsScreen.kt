package com.ashareai.app.standalone.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.standalone.backtest.BacktestStatus
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun StandaloneStrategySettingsScreen(
    viewModel: StandaloneViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by viewModel.settings.collectAsState()
    val watchlist by viewModel.watchlist.collectAsState()
    val backtests by viewModel.backtests.collectAsState()
    var trainingName by rememberSaveable { mutableStateOf("自选股智能训练") }
    var startDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().minusYears(2).toString()) }
    var endDate by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var initialCash by rememberSaveable { mutableStateOf("100000") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                viewModel.appContext.contentResolver.openInputStream(uri)?.use { stream ->
                    Json.parseToJsonElement(stream.readBytes().decodeToString()).jsonObject
                } ?: error("无法读取训练包")
            }.onSuccess { config ->
                config["name"]?.jsonPrimitive?.contentOrNull?.let { trainingName = it }
                config["start_date"]?.jsonPrimitive?.contentOrNull?.let { startDate = it }
                config["end_date"]?.jsonPrimitive?.contentOrNull?.let { endDate = it }
                config["initial_cash"]?.jsonPrimitive?.contentOrNull?.let { initialCash = it }
                message = "已导入本地训练配置"
            }.onFailure { error = "训练包格式无效：${it.message ?: "未知错误"}" }
        }
    }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("策略设置", style = MaterialTheme.typography.headlineSmall)
            TextButton(onClick = onBack) { Text("返回") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("确定性策略", style = MaterialTheme.typography.titleMedium)
                Text("本地研究、模拟组合和训练使用同一套确定性研究引擎，AI 只负责解释。", style = MaterialTheme.typography.bodySmall)
                Text("当前训练标的：${watchlist.size} 只自选股", style = MaterialTheme.typography.bodyMedium)
                StrategySwitchRow("每日智能研究", settings.dailyResearchEnabled, viewModel::setDailyResearchEnabled)
                StrategySwitchRow("买入区间报警", settings.alertsEnabled, viewModel::setAlertsEnabled)
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("模拟盘智能训练", style = MaterialTheme.typography.titleMedium)
                Text("使用本机自选股对应的真实历史 K 线缓存；没有自选股时不会启动训练。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(trainingName, { trainingName = it }, label = { Text("训练名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(startDate, { startDate = it }, label = { Text("开始日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endDate, { endDate = it }, label = { Text("结束日期 YYYY-MM-DD") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(initialCash, { initialCash = it.filter(Char::isDigit) }, label = { Text("初始资金") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) { Text("导入训练配置") }
                    Button(enabled = watchlist.isNotEmpty(), onClick = {
                            viewModel.submitWatchlistTraining(startDate, endDate, initialCash.toDoubleOrNull() ?: 100000.0) { result ->
                            if (result == null) message = "$trainingName 已提交，本地训练完成后可在回测记录查看"
                            else error = result
                        }
                    }) { Text("开始训练") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("本地训练记录", style = MaterialTheme.typography.titleMedium)
                if (backtests.isEmpty()) {
                    Text("暂无训练记录", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    backtests.take(8).forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.startDate} ~ ${item.endDate}", style = MaterialTheme.typography.bodySmall)
                            Text(if (item.status == BacktestStatus.SUCCEEDED) "已完成" else item.status.name, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StrategySwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
