package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.island.MonitorService
import com.ashareai.app.data.normalizeServerUrl
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.AppCard
import kotlinx.coroutines.launch

/** 设置：服务器地址、行情刷新间隔、深浅色、超级岛监控开关。 */
@Composable
fun SettingsScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val assets by appViewModel.assets.collectAsState()
    val darkMode by appViewModel.settings.darkMode.collectAsState(initial = "system")
    val islandEnabled by appViewModel.settings.islandEnabled.collectAsState(initial = true)

    var baseUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        baseUrl = appViewModel.settings.currentBaseUrl()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "设置")

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text("服务器地址", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        val normalizedUrl = normalizeServerUrl(baseUrl).getOrElse {
                            message = it.message ?: "服务器地址格式不正确"
                            return@Button
                        }
                        baseUrl = normalizedUrl
                        scope.launch {
                            appViewModel.settings.setBaseUrl(normalizedUrl)
                            com.ashareai.app.data.ApiClient.rebuild()
                            message = "已保存，新地址将在下次请求生效"
                        }
                    }) { Text("保存") }
                    Text(
                        "修改后如果登录态失效，请重新登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                AppCard {
                    Text("行情刷新间隔", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 30, 60, 120).forEach { sec ->
                            FilterChip(
                                selected = assets?.market_refresh_interval_seconds == sec,
                                onClick = {
                                    appViewModel.saveRefreshInterval(sec) { msg ->
                                        message = msg ?: "刷新间隔已改为 ${sec}s"
                                    }
                                },
                                label = { Text("${sec}s") },
                            )
                        }
                    }
                }
            }

            item {
                AppCard {
                    Text("外观", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (v, label) ->
                            FilterChip(
                                selected = darkMode == v,
                                onClick = { scope.launch { appViewModel.settings.setDarkMode(v) } },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("超级岛持仓监控", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "在小米 HyperOS 超级岛常驻显示持仓盈亏；卖出/止损预警会以焦点通知弹出。其他设备显示为普通常驻通知。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = islandEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    appViewModel.settings.setIslandEnabled(enabled)
                                    if (enabled) {
                                        MonitorService.start(context)
                                    } else {
                                        MonitorService.stop(context)
                                    }
                                }
                            },
                        )
                    }
                }
            }

            message?.let {
                item {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
