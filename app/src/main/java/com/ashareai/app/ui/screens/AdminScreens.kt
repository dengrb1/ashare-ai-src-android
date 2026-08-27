package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.data.model.EdgeGatewayConfiguration
import com.ashareai.app.data.model.EdgeGatewayDraft
import com.ashareai.app.data.model.EdgeGatewayLogs
import com.ashareai.app.data.model.EdgeProxyHost
import com.ashareai.app.data.model.ModelProfileSettings
import com.ashareai.app.data.model.ModelSettings
import com.ashareai.app.data.model.ModelSettingsDraft
import com.ashareai.app.data.model.SystemResources
import com.ashareai.app.data.model.SystemSettings
import com.ashareai.app.data.model.SystemSettingsUnlockRequest
import com.ashareai.app.data.model.RuntimeIdentity
import com.ashareai.app.data.model.RuntimeIdentityRequest
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.AdminViewModel
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.components.ErrorBanner
import com.ashareai.app.ui.components.KeyValueRow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private fun adminUser(appViewModel: AppViewModel): Boolean =
    (appViewModel.authState.value as? AppViewModel.AuthState.LoggedIn)?.user?.let { user ->
        user.role.equals("ADMIN", true) || user.is_admin_account
    } == true

private fun bytes(value: Long?): String {
    if (value == null) return "--"
    val units = listOf("B", "KB", "MB", "GB")
    var amount = value.toDouble()
    var unit = 0
    while (amount >= 1024 && unit < units.lastIndex) {
        amount /= 1024
        unit++
    }
    return "%.1f %s".format(amount, units[unit])
}

private enum class SystemFieldKind { TEXT, INTEGER, DECIMAL, BOOLEAN, SECRET }

private data class SystemEditorField(
    val key: String,
    val label: String,
    val kind: SystemFieldKind,
)

private val systemEditorSections = listOf(
    "AI 与节能" to listOf(
        SystemEditorField("api_runtime_mode", "运行模式（LIGHTWEIGHT / SUPREME）", SystemFieldKind.TEXT),
        SystemEditorField("api_runtime_auto_close", "收盘后自动释放行情进程", SystemFieldKind.BOOLEAN),
        SystemEditorField("energy_saving_enabled", "自动节能", SystemFieldKind.BOOLEAN),
        SystemEditorField("llm_agent_max_concurrency", "AI Agent 并发", SystemFieldKind.INTEGER),
        SystemEditorField("research_execution_mode", "研究运行模式（SERIAL / DUAL）", SystemFieldKind.TEXT),
        SystemEditorField("auto_restart_enabled", "拓扑变更后自动重启", SystemFieldKind.BOOLEAN),
    ),
    "行情与搜索" to listOf(
        SystemEditorField("searxng_base_url", "SearXNG 地址", SystemFieldKind.TEXT),
        SystemEditorField("searxng_timeout_seconds", "SearXNG 超时秒数", SystemFieldKind.DECIMAL),
        SystemEditorField("searxng_max_results", "搜索结果数", SystemFieldKind.INTEGER),
        SystemEditorField("market_cache_seconds", "行情缓存秒数", SystemFieldKind.INTEGER),
        SystemEditorField("market_kline_cache_seconds", "K 线缓存秒数", SystemFieldKind.INTEGER),
        SystemEditorField("market_prefetch_max_workers", "行情预取并发", SystemFieldKind.INTEGER),
        SystemEditorField("market_provider_max_workers", "供应商并发", SystemFieldKind.INTEGER),
        SystemEditorField("market_provider_max_queue", "供应商队列", SystemFieldKind.INTEGER),
        SystemEditorField("market_cache_max_entries", "行情缓存条目", SystemFieldKind.INTEGER),
        SystemEditorField("market_stale_seconds", "行情陈旧阈值", SystemFieldKind.INTEGER),
        SystemEditorField("market_timeout_seconds", "行情超时秒数", SystemFieldKind.DECIMAL),
        SystemEditorField("market_hedge_delay_seconds", "日线竞速延迟秒数", SystemFieldKind.DECIMAL),
        SystemEditorField("financial_search_cache_seconds", "金融搜索缓存秒数", SystemFieldKind.INTEGER),
        SystemEditorField("financial_search_max_concurrency", "金融搜索并发", SystemFieldKind.INTEGER),
        SystemEditorField("financial_search_rate_limit_per_minute", "金融搜索每分钟限额", SystemFieldKind.INTEGER),
    ),
    "研究与数据" to listOf(
        SystemEditorField("daily_research_start_hour", "每日研究启动小时", SystemFieldKind.INTEGER),
        SystemEditorField("daily_research_start_minute", "每日研究启动分钟", SystemFieldKind.INTEGER),
        SystemEditorField("daily_research_retry_minutes", "数据就绪重试分钟", SystemFieldKind.INTEGER),
        SystemEditorField("daily_research_retry_limit_minutes", "旧版数据就绪窗口分钟", SystemFieldKind.INTEGER),
        SystemEditorField("worker_lease_seconds", "Worker 租约秒数", SystemFieldKind.INTEGER),
        SystemEditorField("canonical_bundle_mode", "数据包模式（akshare / file / demo）", SystemFieldKind.TEXT),
        SystemEditorField("allow_demo_data", "允许演示数据", SystemFieldKind.BOOLEAN),
        SystemEditorField("akshare_bundle_size", "AKShare 标的数", SystemFieldKind.INTEGER),
        SystemEditorField("akshare_history_sessions", "AKShare 历史交易日", SystemFieldKind.INTEGER),
        SystemEditorField("akshare_fetch_max_attempts", "AKShare 最大尝试", SystemFieldKind.INTEGER),
        SystemEditorField("akshare_fetch_backoff_seconds", "AKShare 重试退避秒数", SystemFieldKind.DECIMAL),
        SystemEditorField("minimum_listing_days", "最低上市天数", SystemFieldKind.INTEGER),
        SystemEditorField("minimum_median_amount", "最低成交额", SystemFieldKind.DECIMAL),
    ),
    "存储与私密凭据" to listOf(
        SystemEditorField("object_store_endpoint", "对象存储 Endpoint", SystemFieldKind.TEXT),
        SystemEditorField("object_store_bucket", "对象存储 Bucket", SystemFieldKind.TEXT),
        SystemEditorField("object_store_secure", "对象存储 TLS", SystemFieldKind.BOOLEAN),
        SystemEditorField("tushare_token", "Tushare Token", SystemFieldKind.SECRET),
        SystemEditorField("object_store_access_key", "对象存储 Access Key", SystemFieldKind.SECRET),
        SystemEditorField("object_store_secret_key", "对象存储 Secret Key", SystemFieldKind.SECRET),
    ),
    "公网网关基础参数" to listOf(
        SystemEditorField("edge_gateway_enabled", "启用 Edge Gateway 控制器", SystemFieldKind.BOOLEAN),
        SystemEditorField("edge_domain", "公网域名", SystemFieldKind.TEXT),
        SystemEditorField("edge_acme_email", "证书申请邮箱", SystemFieldKind.TEXT),
        SystemEditorField("edge_acme_ca_server", "证书服务（letsencrypt / letsencrypt_test）", SystemFieldKind.TEXT),
        SystemEditorField("edge_frpc_enabled", "连接外部 FRP", SystemFieldKind.BOOLEAN),
        SystemEditorField("edge_frpc_config_file", "FRPC 私有配置文件路径", SystemFieldKind.TEXT),
    ),
)

private fun SystemSettings.valueText(key: String): String =
    values[key]?.jsonPrimitive?.content.orEmpty()

@Composable
fun ModelSettingsScreen(appViewModel: AppViewModel) {
    if (!adminUser(appViewModel)) {
        AccessDeniedScreen()
        return
    }
    val scope = rememberCoroutineScope()
    val admin: AdminViewModel = viewModel()
    var current by remember { mutableStateOf<ModelSettings?>(null) }
    var draft by remember { mutableStateOf(ModelSettingsDraft(base_url = "")) }
    var logs by remember { mutableStateOf(emptyList<com.ashareai.app.data.model.ModelProbeLog>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showKey by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            runCatching {
                val result = admin.modelSettings()
                current = result
                draft = ModelSettingsDraft(
                    base_url = result.base_url,
                    search_model = result.search_model,
                    search_reasoning_effort = result.search_reasoning_effort,
                    research_model = result.research_model,
                    research_reasoning_effort = result.research_reasoning_effort,
                    model_profiles = result.model_profiles,
                    timeout_seconds = result.timeout_seconds,
                    enabled = result.enabled,
                )
                logs = admin.modelProbeLogs()
            }.onFailure { error = it.toUserMessage() }
        }
    }
    LaunchedEffect(Unit) { load() }

    fun updateProfile(index: Int, profile: ModelProfileSettings) {
        draft = draft.copy(model_profiles = draft.model_profiles.mapIndexed { i, item -> if (i == index) profile else item })
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple("AI 模型配置")
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text("配置状态", style = MaterialTheme.typography.titleSmall)
                    KeyValueRow("版本", if (current?.configured == true) "v${current?.version}" else "未配置")
                    KeyValueRow("连通性", when { current?.reachable == true -> "可用"; current?.configured == true -> "已配置 / 不可达"; else -> "未配置" })
                    KeyValueRow("协议", listOfNotNull(current?.structured_output_supported?.let { if (it) "JSON Schema" else null }, current?.streaming_supported?.let { if (it) "流式" else null }).ifEmpty { listOf("兼容模式") }.joinToString(" · "))
                    Text(
                        when {
                            current?.reachable == true -> "模型网关状态：可用"
                            current?.configured == true -> "模型网关状态：已配置，当前不可达"
                            else -> "模型网关状态：未配置"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                AppCard {
                    Text("OpenAI-compatible API", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(draft.base_url, { draft = draft.copy(base_url = it) }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(draft.api_key.orEmpty(), { draft = draft.copy(api_key = it) }, label = { Text("API Key（留空保持已保存密钥）") }, visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(showKey, { showKey = it })
                        Text("显示密钥", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text("后端加密保存", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedTextField(draft.search_model, { draft = draft.copy(search_model = it) }, label = { Text("搜索模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ReasoningPicker(draft.search_reasoning_effort) { draft = draft.copy(search_reasoning_effort = it) }
                    OutlinedTextField(draft.research_model, { draft = draft.copy(research_model = it) }, label = { Text("研究模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    ReasoningPicker(draft.research_reasoning_effort) { draft = draft.copy(research_reasoning_effort = it) }
                    OutlinedTextField(draft.timeout_seconds.toString(), { draft = draft.copy(timeout_seconds = it.toDoubleOrNull() ?: draft.timeout_seconds) }, label = { Text("超时秒数") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(draft.enabled, { draft = draft.copy(enabled = it) })
                        Text("启用 AI 配置", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(enabled = !busy, onClick = {
                            busy = true; message = null; error = null
                            scope.launch { runCatching { admin.testModelSettings(draft) }.onSuccess { message = it.message }.onFailure { error = it.toUserMessage() }; busy = false; load() }
                        }) { Text(if (busy) "测试中…" else "测试连接") }
                        TextButton(enabled = !busy, onClick = {
                            busy = true; message = null; error = null
                            scope.launch { runCatching { admin.listConfiguredModels(draft) }.onSuccess { message = "已读取 ${it.models.size} 个模型：${it.models.take(4).joinToString()}" }.onFailure { error = it.toUserMessage() }; busy = false }
                        }) { Text("读取模型") }
                        Button(enabled = !busy, onClick = {
                            busy = true; message = null; error = null
                            scope.launch { runCatching { admin.saveModelSettings(draft.copy(api_key = draft.api_key?.takeIf { it.isNotBlank() })) }.onSuccess { message = "模型配置 v${it.version} 已启用" }.onFailure { error = it.toUserMessage() }; busy = false; load() }
                        }) { Text("验证并启用") }
                    }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    error?.let { ErrorBanner(it) }
                }
            }
            item {
                AppCard {
                    Text("模型档案", style = MaterialTheme.typography.titleSmall)
                    Text("用于缓存协议、上下文预算与成本统计；最多 32 条。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    draft.model_profiles.forEachIndexed { index, profile ->
                        ProfileEditor(profile, onChange = { updateProfile(index, it) }, onRemove = { draft = draft.copy(model_profiles = draft.model_profiles.filterIndexed { i, _ -> i != index }) })
                    }
                    TextButton(enabled = draft.model_profiles.size < 32, onClick = { draft = draft.copy(model_profiles = draft.model_profiles + ModelProfileSettings(model = "")) }) { Text("添加模型档案") }
                }
            }
            item {
                AppCard {
                    Text("探测日志", style = MaterialTheme.typography.titleSmall)
                    logs.take(10).forEach { log ->
                        KeyValueRow("${log.model} · ${log.purpose}", "${log.outcome} · ${log.duration_ms}ms")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasoningPicker(value: String, onChange: (String) -> Unit) {
    Column {
        Text("推理", style = MaterialTheme.typography.labelSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("low", "medium", "high", "xhigh").forEach { option ->
                FilterChip(selected = value == option, onClick = { onChange(option) }, label = { Text(option) })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileEditor(profile: ModelProfileSettings, onChange: (ModelProfileSettings) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(profile.model, { onChange(profile.copy(model = it)) }, label = { Text("模型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("COMPATIBLE", "OPENAI", "GROK").forEach { policy ->
                FilterChip(selected = profile.cache_policy == policy, onClick = { onChange(profile.copy(cache_policy = policy)) }, label = { Text(policy) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(profile.context_window_tokens.toString(), { onChange(profile.copy(context_window_tokens = it.toIntOrNull() ?: profile.context_window_tokens)) }, label = { Text("上下文") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(profile.output_token_reserve.toString(), { onChange(profile.copy(output_token_reserve = it.toIntOrNull() ?: profile.output_token_reserve)) }, label = { Text("输出预留") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(profile.reasoning_token_reserve.toString(), { onChange(profile.copy(reasoning_token_reserve = it.toIntOrNull() ?: profile.reasoning_token_reserve)) }, label = { Text("推理预留") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(profile.input_price_per_million.toString(), { onChange(profile.copy(input_price_per_million = it.toDoubleOrNull() ?: profile.input_price_per_million)) }, label = { Text("输入 / 百万") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(profile.cached_input_price_per_million.toString(), { onChange(profile.copy(cached_input_price_per_million = it.toDoubleOrNull() ?: profile.cached_input_price_per_million)) }, label = { Text("缓存读 / 百万") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(profile.cache_write_price_per_million.toString(), { onChange(profile.copy(cache_write_price_per_million = it.toDoubleOrNull() ?: profile.cache_write_price_per_million)) }, label = { Text("缓存写 / 百万") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(profile.output_price_per_million.toString(), { onChange(profile.copy(output_price_per_million = it.toDoubleOrNull() ?: profile.output_price_per_million)) }, label = { Text("输出 / 百万") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = onRemove) { Text("删除此档案", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun SystemSettingsScreen(appViewModel: AppViewModel) {
    if (!adminUser(appViewModel)) { AccessDeniedScreen(); return }
    val scope = rememberCoroutineScope()
    val admin: AdminViewModel = viewModel()
    var resources by remember { mutableStateOf<SystemResources?>(null) }
    var settings by remember { mutableStateOf<SystemSettings?>(null) }
    var identity by remember { mutableStateOf<RuntimeIdentity?>(null) }
    var unlock by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var showUnlock by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var energy by remember { mutableStateOf(false) }
    var lowResident by remember { mutableStateOf(true) }
    var editorValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var editorToggles by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var editorSecrets by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun load() = scope.launch {
        runCatching {
            resources = admin.systemResources()
            settings = admin.systemSettings()
            identity = admin.runtimeIdentity()
            energy = admin.energySaving().enabled
            lowResident = settings?.values?.get("api_runtime_mode")?.toString()?.contains("LIGHTWEIGHT") != false
            val loaded = settings ?: return@runCatching
            editorValues = systemEditorSections.flatMap { it.second }
                .filter { it.kind != SystemFieldKind.BOOLEAN && it.kind != SystemFieldKind.SECRET }
                .associate { it.key to loaded.valueText(it.key) }
            editorToggles = systemEditorSections.flatMap { it.second }
                .filter { it.kind == SystemFieldKind.BOOLEAN }
                .associate { it.key to (loaded.valueText(it.key).toBooleanStrictOrNull() ?: false) }
        }.onFailure { error = it.toUserMessage() }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple("系统资源与运行配置")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("资源监控", style = MaterialTheme.typography.titleSmall)
                    KeyValueRow("内存", "${resources?.memory?.percent?.let { "%.1f%%".format(it) } ?: "--"} · ${bytes(resources?.memory?.available_bytes)} 可用")
                    KeyValueRow("CPU", "${resources?.cpu?.percent?.let { "%.1f%%".format(it) } ?: "--"} · ${resources?.cpu?.logical_cores ?: 0} 核")
                    KeyValueRow("磁盘", "${resources?.disk?.percent?.let { "%.1f%%".format(it) } ?: "--"} · ${bytes(resources?.disk?.available_bytes)} 可用")
                    KeyValueRow("拓扑估算", "${resources?.topology_estimate?.worker_replicas ?: 0} Worker · ${bytes(resources?.topology_estimate?.typical_increment_bytes)}")
                    resources?.warnings.orEmpty().forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    TextButton(onClick = { load() }) { Text("刷新资源") }
                }
            }
            systemEditorSections.forEach { (title, fields) ->
                item {
                    AppCard {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        Text("保存前需要解锁；密钥字段留空会保持服务端已加密的值。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        fields.forEach { field ->
                            when (field.kind) {
                                SystemFieldKind.BOOLEAN -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(editorToggles[field.key] == true, { editorToggles = editorToggles + (field.key to it) }, enabled = unlock != null)
                                    Text(field.label, style = MaterialTheme.typography.bodyMedium)
                                }
                                SystemFieldKind.SECRET -> OutlinedTextField(
                                    value = editorSecrets[field.key].orEmpty(),
                                    onValueChange = { editorSecrets = editorSecrets + (field.key to it) },
                                    label = { Text("${field.label}${if (settings?.secret_configured?.get(field.key) == true) "（已配置）" else ""}") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    enabled = unlock != null,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                else -> OutlinedTextField(
                                    value = editorValues[field.key].orEmpty(),
                                    onValueChange = { editorValues = editorValues + (field.key to it) },
                                    label = { Text(field.label) },
                                    enabled = unlock != null,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        Button(enabled = unlock != null, onClick = {
                            val activeUnlock = unlock ?: return@Button
                            val payload = buildJsonObject {
                                fields.forEach { field ->
                                    when (field.kind) {
                                        SystemFieldKind.BOOLEAN -> {
                                            // A missing backend value means "inherit environment"; do not turn
                                            // it into an explicit false merely by saving another field in the section.
                                            if (settings?.values?.containsKey(field.key) == true || editorToggles[field.key] == true) {
                                                put(field.key, editorToggles[field.key] == true)
                                            }
                                        }
                                        SystemFieldKind.INTEGER -> editorValues[field.key]?.toIntOrNull()?.let { put(field.key, it) }
                                        SystemFieldKind.DECIMAL -> editorValues[field.key]?.toDoubleOrNull()?.let { put(field.key, it) }
                                        SystemFieldKind.TEXT -> editorValues[field.key]?.takeIf(String::isNotBlank)?.let { put(field.key, it) }
                                        SystemFieldKind.SECRET -> editorSecrets[field.key]?.takeIf(String::isNotBlank)?.let { put(field.key, it) }
                                    }
                                }
                            }
                            if (payload.isEmpty()) { message = "没有有效的修改"; return@Button }
                            scope.launch { runCatching { admin.saveSystemSettings(activeUnlock, payload) }.onSuccess { settings = it; message = "$title 已保存"; editorSecrets = emptyMap() }.onFailure { error = it.toUserMessage() } }
                        }) { Text("保存$title") }
                        TextButton(enabled = unlock != null, onClick = {
                            val activeUnlock = unlock ?: return@TextButton
                            scope.launch {
                                runCatching {
                                    fields.forEach { admin.restoreSystemSetting(it.key, activeUnlock) }
                                }.onSuccess {
                                    message = "$title 已恢复为环境变量基线"
                                    load()
                                }.onFailure { error = it.toUserMessage() }
                            }
                        }) { Text("恢复$title 环境基线") }
                    }
                }
            }
            item {
                AppCard {
                    Text("低驻留策略", style = MaterialTheme.typography.titleSmall)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(lowResident, { lowResident = it })
                        Column {
                            Text("LIGHTWEIGHT", style = MaterialTheme.typography.bodyMedium)
                            Text("按需启动供应商进程，研究空闲时释放缓存。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(energy, { energy = it; scope.launch { runCatching { if (it) admin.rearmEnergySaving() else admin.wakeEnergySaving() }.onFailure { error = it.toUserMessage() } } })
                        Text("自动节能 / 深度待机", style = MaterialTheme.typography.bodyMedium)
                    }
                    Button(enabled = unlock != null, onClick = {
                        val token = unlock ?: return@Button
                        scope.launch { runCatching { admin.saveSystemSettings(token, buildJsonObject { put("api_runtime_mode", if (lowResident) "LIGHTWEIGHT" else "SUPREME"); put("energy_saving_enabled", energy) }) }.onSuccess { settings = it; message = "运行策略已保存" }.onFailure { error = it.toUserMessage() } }
                    }) { Text("保存运行策略") }
                }
            }
            item {
                AppCard {
                    Text("运行身份", style = MaterialTheme.typography.titleSmall)
                    if (identity?.applicable == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { identity?.supported_modes.orEmpty().filter { it != "system" }.forEach { option -> FilterChip(selected = identity?.mode == option, onClick = { identity = identity?.copy(mode = option) }, label = { Text(option) }) } }
                        Button(enabled = unlock != null && identity?.mode != null, onClick = { scope.launch { runCatching { admin.saveRuntimeIdentity(unlock!!, RuntimeIdentityRequest(identity!!.mode!!)) }.onSuccess { identity = it; message = "运行身份已保存" }.onFailure { error = it.toUserMessage() } } }) { Text("保存运行身份") }
                    } else {
                        Text(identity?.note ?: "当前部署由 Docker 管理，无需在手机端设置运行身份。", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                AppCard {
                    Text("编辑权限", style = MaterialTheme.typography.titleSmall)
                    Text(if (unlock == null) "读取资源无需解锁；保存设置前需要管理员密码。" else "已解锁，可保存运行配置。", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { showUnlock = true }) { Text(if (unlock == null) "解锁设置" else "重新验证") }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    error?.let { ErrorBanner(it) }
                }
            }
        }
    }
    if (showUnlock) {
        AlertDialog(onDismissRequest = { showUnlock = false }, title = { Text("解锁系统设置") }, text = { OutlinedTextField(password, { password = it }, label = { Text("当前账户密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }, confirmButton = { TextButton(onClick = { scope.launch { runCatching { admin.unlockSystemSettings(SystemSettingsUnlockRequest(password)) }.onSuccess { unlock = it.unlock_token; password = ""; showUnlock = false }.onFailure { error = it.toUserMessage() } } }) { Text("验证") } }, dismissButton = { TextButton(onClick = { showUnlock = false }) { Text("取消") } })
    }
}

@Composable
fun EdgeGatewayScreen(appViewModel: AppViewModel) {
    if (!adminUser(appViewModel)) { AccessDeniedScreen(); return }
    val scope = rememberCoroutineScope()
    val admin: AdminViewModel = viewModel()
    var config by remember { mutableStateOf<EdgeGatewayConfiguration?>(null) }
    var logs by remember { mutableStateOf<EdgeGatewayLogs?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var showUnlock by remember { mutableStateOf(false) }
    var hosts by remember { mutableStateOf(emptyList<EdgeProxyHost>()) }
    var enabled by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf("STRICT") }
    var frpc by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun load() = scope.launch {
        runCatching { config = admin.edgeGateway(token); logs = admin.edgeGatewayLogs(); config?.let { enabled = it.enabled; mode = it.validation_mode; hosts = it.proxy_hosts; if (token != null) frpc = it.frpc_toml } }.onFailure { error = it.toUserMessage() }
    }
    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple("Edge Gateway")
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("公网边缘网关", style = MaterialTheme.typography.titleSmall)
                    KeyValueRow("配置版本", "v${config?.version ?: 0}")
                    KeyValueRow("应用状态", config?.apply_status ?: "未配置")
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(enabled, { enabled = it }); Text("启用公网网关") }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(selected = mode == "STRICT", onClick = { mode = "STRICT" }, label = { Text("严格校验") }); FilterChip(selected = mode == "COMPATIBLE", onClick = { mode = "COMPATIBLE" }, label = { Text("兼容模式") }) }
                }
            }
            item {
                AppCard {
                    Text("FRP 配置", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(frpc, { frpc = it }, label = { Text(if (token == null) "解锁后可编辑 frpc.toml" else "frpc.toml") }, enabled = token != null, minLines = 8, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { scope.launch { runCatching { admin.validateEdgeGateway(EdgeGatewayDraft(enabled, mode, hosts, frpc)) }.onSuccess { message = "校验通过：${it.proxy_count} 个入口" }.onFailure { error = it.toUserMessage() } } }) { Text("校验") }
                        Button(enabled = token != null, onClick = { scope.launch { runCatching { admin.saveEdgeGateway(token!!, EdgeGatewayDraft(enabled, mode, hosts, frpc)) }.onSuccess { config = it; message = "网关配置已保存" }.onFailure { error = it.toUserMessage() } } }) { Text("保存") }
                        TextButton(enabled = token != null, onClick = { scope.launch { runCatching { admin.rollbackEdgeGateway(token!!) }.onSuccess { config = it; hosts = it.proxy_hosts; frpc = it.frpc_toml; message = "已回滚上一版配置" }.onFailure { error = it.toUserMessage() } } }) { Text("回滚") }
                    }
                }
            }
            item {
                AppCard {
                    Text("访问入口", style = MaterialTheme.typography.titleSmall)
                    hosts.forEachIndexed { index, host ->
                        OutlinedTextField(host.domains.joinToString(","), { value -> hosts = hosts.mapIndexed { i, item -> if (i == index) item.copy(domains = value.split(",").map(String::trim).filter(String::isNotBlank)) else item } }, label = { Text("入口 ${index + 1} 域名") }, enabled = token != null, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) { Switch(host.enabled, { value -> hosts = hosts.mapIndexed { i, item -> if (i == index) item.copy(enabled = value) else item } }); Text("启用") }
                    }
                    TextButton(enabled = token != null && hosts.size < 32, onClick = { hosts = hosts + EdgeProxyHost(name = "新入口", domains = listOf("example.com"), forward_host = "web") }) { Text("添加入口") }
                }
            }
            item {
                AppCard {
                    Text("编辑权限与日志", style = MaterialTheme.typography.titleSmall)
                    Button(onClick = { showUnlock = true }) { Text(if (token == null) "解锁编辑" else "重新验证") }
                    logs?.lines?.takeLast(8)?.forEach { Text(it, style = MaterialTheme.typography.labelSmall) }
                    message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
                    error?.let { ErrorBanner(it) }
                }
            }
        }
    }
    if (showUnlock) AlertDialog(onDismissRequest = { showUnlock = false }, title = { Text("解锁网关配置") }, text = { OutlinedTextField(password, { password = it }, label = { Text("当前账户密码") }, visualTransformation = PasswordVisualTransformation(), singleLine = true) }, confirmButton = { TextButton(onClick = { scope.launch { runCatching { admin.unlockSystemSettings(SystemSettingsUnlockRequest(password)) }.onSuccess { token = it.unlock_token; password = ""; showUnlock = false; load() }.onFailure { error = it.toUserMessage() } } }) { Text("验证") } }, dismissButton = { TextButton(onClick = { showUnlock = false }) { Text("取消") } })
}

@Composable
private fun AccessDeniedScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("仅管理员可访问", style = MaterialTheme.typography.titleMedium)
    }
}
