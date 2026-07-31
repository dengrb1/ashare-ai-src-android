package com.ashareai.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ashareai.app.island.MonitorService
import com.ashareai.app.island.FocusCapabilities
import com.ashareai.app.island.FocusNotification
import com.ashareai.app.data.normalizeServerUrl
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.components.KeyValueRow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var notificationsGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var focusCapabilities by remember { mutableStateOf<FocusCapabilities?>(null) }
    var testAfterPermission by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
        if (granted) {
            if (testAfterPermission) {
                val capability = FocusNotification.showTest(context)
                message = "测试通知已发送：已附加 HyperOS v3 超级岛载荷，请观察顶部超级岛"
                testAfterPermission = false
            } else {
                scope.launch {
                    appViewModel.settings.setIslandEnabled(true)
                    MonitorService.start(context)
                }
            }
        } else {
            message = "通知权限未开启，监控不会在后台运行"
        }
    }

    LaunchedEffect(Unit) {
        baseUrl = appViewModel.settings.currentBaseUrl()
        focusCapabilities = withContext(Dispatchers.IO) { FocusNotification.capabilities(context) }
    }

    androidx.compose.runtime.DisposableEffect(context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notificationsGranted = Build.VERSION.SDK_INT < 33 ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            }
        }
        val owner = context as? androidx.lifecycle.LifecycleOwner
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
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
                            Text("行情与研究通知", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "后台监控持仓、交易预警和研究进度。所有设备使用标准通知；系统允许时会提交焦点通知协议。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = islandEnabled,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    scope.launch {
                                        appViewModel.settings.setIslandEnabled(false)
                                        MonitorService.stop(context)
                                    }
                                } else if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    scope.launch {
                                        appViewModel.settings.setIslandEnabled(true)
                                        MonitorService.start(context)
                                    }
                                }
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    KeyValueRow("普通通知", if (notificationsGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()) "可用" else "未授权")
                    val capabilities = focusCapabilities
                    KeyValueRow("HyperOS 焦点协议", capabilities?.protocolVersion?.takeIf { it > 0 }?.let { "v$it" } ?: "不支持")
                    KeyValueRow(
                        "HyperOS 3 超级岛",
                        if (capabilities?.superIslandReady == true) "已确认 v3 协议" else "已提交 v3 载荷（系统未公开回报）",
                    )
                    Text(
                        "实现采用 InstallerX-Revived 同款 focus-api v3 通知结构。系统仍可能按 ROM 版本、通知设置或应用授权决定是否上岛。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            },
                        )
                    }) { Text("打开系统通知设置") }
                }
            }

            item {
                AppCard {
                    Text("无需设备数据测试", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "发送一条模拟沪深300行情的持续通知。HyperOS 3 会尝试显示系统超级岛，其他系统显示普通通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                            testAfterPermission = true
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            val capability = FocusNotification.showTest(context)
                            message = "测试通知已发送：已附加 HyperOS v3 超级岛载荷，请观察顶部超级岛"
                        }
                    }) {
                        Text("测试上岛")
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
