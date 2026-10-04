package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.ScreenState
import com.ashareai.app.ui.StrategyEvolutionViewModel
import com.ashareai.app.ui.components.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

@Composable
fun StrategyEvolutionScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val vm: StrategyEvolutionViewModel = viewModel()
    val state by vm.state.collectAsState()
    val busy by vm.busy.collectAsState()
    LaunchedEffect(Unit) { vm.load() }
    val content = when (val value = state) {
        is ScreenState.Content -> value.value
        is ScreenState.Error -> value.previous
        else -> null
    }
    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "策略演化")
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text("确定性评分策略", style = MaterialTheme.typography.titleSmall)
                    Text("候选只作为影子结果，批准后才会成为新的版本。历史研究结果和风险结论不会被改写。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Text("当前版本：${content?.active?.value("version") ?: content?.active?.value("version_id") ?: "未知"}")
                    Text("评分由固定公式计算，AI 只能解释结果。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = vm::rollback, enabled = !busy && content?.active != null) { Text("回滚上一版本") }
                }
            }
            item { Text("待审核候选（${content?.candidates?.size ?: 0}）", style = MaterialTheme.typography.titleMedium) }
            if (content?.candidates.isNullOrEmpty()) {
                item { Text("暂无待审核候选。非管理员或服务端未启用时，这里会显示不可用原因。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                content!!.candidates.forEach { candidate ->
                    item {
                        AppCard {
                            Text(candidate.value("candidate_id") ?: candidate.value("id") ?: "候选", style = MaterialTheme.typography.titleSmall)
                            Text("公式版本：${candidate.value("formula_version") ?: candidate.value("version") ?: "未知"}")
                            candidate.value("shadow_formula_version")?.let { Text("影子公式：$it") }
                            candidate.value("shadow_score")?.let { Text("影子评分：$it") }
                            candidate.value("status")?.let { Text("状态：$it") }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val id = candidate.value("candidate_id") ?: candidate.value("id")
                                Button(onClick = { id?.let(vm::approve) }, enabled = id != null && !busy) { Text("批准") }
                                OutlinedButton(onClick = { id?.let(vm::reject) }, enabled = id != null && !busy) { Text("拒绝") }
                            }
                        }
                    }
                }
            }
            (state as? ScreenState.Error)?.message?.let { message -> item { ErrorBanner(message) { vm.load() } } }
        }
    }
}

private fun JsonElement.value(key: String): String? = (this as? kotlinx.serialization.json.JsonObject)
    ?.get(key)?.jsonPrimitive?.contentOrNull
