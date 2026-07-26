package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.AutomaticResearchReportSettings
import com.ashareai.app.data.model.ResearchSettings
import com.ashareai.app.data.model.ResearchSettingsRequest
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

private data class AutomaticDraft(
    val slot: String,
    val enabled: Boolean,
    val scope: String,
    val symbols: String,
    val total: String,
    val perSymbol: String,
    val maxPrice: String,
    val configVersion: Int,
)

@Composable
internal fun AutomaticResearchDialog(
    settings: ResearchSettings,
    onDismiss: () -> Unit,
    onSaved: (ResearchSettings) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val fallback = listOf(
        AutomaticResearchReportSettings(
            slot = "A",
            enabled = settings.auto_enabled,
            scope = settings.automatic_scope,
            total_budget = settings.automatic_total_budget,
            per_symbol_budget = settings.automatic_per_symbol_budget,
            max_stock_price = settings.automatic_max_stock_price,
        ),
        AutomaticResearchReportSettings("B"),
    )
    var drafts by remember(settings) {
        mutableStateOf((settings.automatic_reports.takeIf { it.size == 2 } ?: fallback).map {
            AutomaticDraft(
                it.slot,
                it.enabled,
                it.scope,
                it.symbols.joinToString(", "),
                it.total_budget.toString(),
                it.per_symbol_budget.toString(),
                it.max_stock_price?.toString().orEmpty(),
                it.config_version,
            )
        })
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun update(slot: String, transform: (AutomaticDraft) -> AutomaticDraft) {
        drafts = drafts.map { if (it.slot == slot) transform(it) else it }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("自动每日报告", style = MaterialTheme.typography.titleLarge)
                Text("A、B 两套配置相互独立；自选与持仓会在运行时读取最新内容。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                drafts.forEach { draft ->
                    AutomaticDraftEditor(draft = draft, onUpdate = { next -> update(draft.slot) { next } })
                }
                error?.let { ErrorBanner(it) }
                Text("上海交易日 15:05 检查；基准未就绪时最多等待至下一交易日 09:25。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Button(
                        enabled = !saving,
                        onClick = {
                            val reports = buildAutomaticReports(drafts).getOrElse {
                                error = it.message
                                return@Button
                            }
                            saving = true
                            coroutineScope.launch {
                                try {
                                    onSaved(ApiClient.api.saveResearchSettings(ResearchSettingsRequest(reports)))
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    ) { Text(if (saving) "保存中" else "保存设置") }
                }
            }
        }
    }
}

private fun buildAutomaticReports(drafts: List<AutomaticDraft>): Result<List<AutomaticResearchReportSettings>> = runCatching {
    drafts.map { draft ->
        val total = positiveNumber(draft.total) ?: error("报告 ${draft.slot} 的预算必须大于 0")
        val per = positiveNumber(draft.perSymbol) ?: error("报告 ${draft.slot} 的预算必须大于 0")
        val maximum = optionalPositiveNumber(draft.maxPrice)
        val symbols = parseResearchSymbols(draft.symbols)
        require(per <= total) { "报告 ${draft.slot} 的单股投入不能超过总预算" }
        require(draft.maxPrice.isBlank() || maximum != null) { "报告 ${draft.slot} 的最高股价必须大于 0" }
        require(draft.scope != "CUSTOM" || symbols.isNotEmpty()) { "报告 ${draft.slot} 需要至少一只有效 A 股代码" }
        AutomaticResearchReportSettings(
            slot = draft.slot,
            enabled = draft.enabled,
            scope = draft.scope,
            symbols = symbols.takeIf { draft.scope == "CUSTOM" } ?: emptyList(),
            total_budget = total,
            per_symbol_budget = per,
            max_stock_price = maximum,
            config_version = draft.configVersion,
        )
    }
}

@Composable
private fun AutomaticDraftEditor(draft: AutomaticDraft, onUpdate: (AutomaticDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("报告 ${draft.slot}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(if (draft.enabled) "每天运行" else "暂停", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            Switch(checked = draft.enabled, onCheckedChange = { onUpdate(draft.copy(enabled = it)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            researchScopes.forEach { (value, label) ->
                FilterChip(selected = draft.scope == value, onClick = { onUpdate(draft.copy(scope = value)) }, label = { Text(label) })
            }
        }
        if (draft.scope == "CUSTOM") {
            OutlinedTextField(draft.symbols, { onUpdate(draft.copy(symbols = it.take(2_000))) }, label = { Text("股票代码") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
        } else if (draft.scope == "WATCHLIST") {
            Text("运行时动态读取当前自选股和模拟持仓。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val numeric = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        OutlinedTextField(draft.total, { onUpdate(draft.copy(total = it.take(18))) }, label = { Text("总预算") }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.perSymbol, { onUpdate(draft.copy(perSymbol = it.take(18))) }, label = { Text("单股最高投入") }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(draft.maxPrice, { onUpdate(draft.copy(maxPrice = it.take(18))) }, label = { Text("最高股价（可选）") }, keyboardOptions = numeric, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
