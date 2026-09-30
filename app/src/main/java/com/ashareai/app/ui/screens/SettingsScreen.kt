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
import com.ashareai.app.ui.ConnectionUiState
import com.ashareai.app.ui.ConnectionViewModel
import com.ashareai.app.ui.LocalMarketViewModel
import com.ashareai.app.ui.MarketRefreshIntervals
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.components.KeyValueRow
import com.ashareai.app.ui.components.SectionTitle
import com.ashareai.app.workspace.SharedDataStore
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.viewmodel.compose.viewModel

/** 设置：服务器地址、前台行情刷新间隔、深浅色、超级岛监控开关、工作区数据共享。 */
@Composable
fun SettingsScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val connectionViewModel: ConnectionViewModel = viewModel()
    val connectionState by connectionViewModel.state.collectAsState()
    val context = appViewModel.screenContext()
    val marketViewModel = LocalMarketViewModel.current
    val foregroundRefreshIntervalSeconds by marketViewModel.refreshIntervalSeconds.collectAsState()
    val darkMode by appViewModel.settings.darkMode.collectAsState(initial = "system")
    val islandEnabled by appViewModel.settings.islandEnabled.collectAsState(initial = true)

    // 工作区数据共享状态
    val sharedDataStore = remember { SharedDataStore(context) }
    val sharedSettings by sharedDataStore.sharedSettings.collectAsState(
        initial = SharedDataStore.SharedSettings()
    )
    val sharedValues by sharedDataStore.sharedValues.collectAsState(
        initial = SharedDataStore.SharedValues()
    )

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
                    appViewModel.enableOptionalPush()
                    MonitorService.start(context)
                }
            }
        } else {
            message = "通知权限未开启，监控不会在后台运行"
        }
    }

    LaunchedEffect(Unit) {
        baseUrl = appViewModel.settings.currentBaseUrl()
        connectionViewModel.probeConfigured()
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
                        connectionViewModel.saveAndProbe(
                            address = baseUrl,
                            onResult = { probe ->
                                baseUrl = probe.address ?: baseUrl
                                message = probe.message
                                if (probe.sessionInvalidated) appViewModel.showLogin()
                            },
                        )
                    }) { Text("保存并探测") }
                    when (val state = connectionState) {
                        ConnectionUiState.Probing -> Text("正在探测服务…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        is ConnectionUiState.Result -> Text(
                            "连接分类：${ConnectionViewModel.label(state.probe.classification)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.probe.canEstablishSession) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                        is ConnectionUiState.Error -> Text(state.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        ConnectionUiState.Idle -> Unit
                    }
                    Text(
                        "修改后如果登录态失效，请重新登录。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                val probe = (connectionState as? ConnectionUiState.Result)?.probe
                AppCard {
                    Text("Fusion 基础设施", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    KeyValueRow("API", probe?.infrastructure?.api?.label() ?: "未知")
                    KeyValueRow("数据库", probe?.infrastructure?.database?.label() ?: "未知")
                    KeyValueRow("行情桥", probe?.infrastructure?.quoteBridge?.label() ?: "未知")
                    KeyValueRow("新闻桥", probe?.infrastructure?.newsBridge?.label() ?: "未知")
                    KeyValueRow("模型网关", probe?.infrastructure?.modelGateway?.label() ?: "未知")
                    Text(
                        "仅展示聚合状态，不展示服务端内部地址或错误详情。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                AppCard {
                    Text("前台行情自动刷新", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "仅应用在前台时自动请求行情，切到后台后会暂停。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        MarketRefreshIntervals.OPTIONS.chunked(3).forEach { rowOptions ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowOptions.forEach { sec ->
                                    FilterChip(
                                        selected = foregroundRefreshIntervalSeconds == sec,
                                        onClick = {
                                            marketViewModel.saveRefreshInterval(sec) { msg ->
                                                message = msg ?: "刷新间隔已改为 ${sec}s"
                                            }
                                        },
                                        label = { Text("${sec}s") },
                                    )
                                }
                            }
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
                            Text("省电模式自动优化", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "检测到手机进入省电模式时，自动降低液体玻璃特效和动画强度，延长续航时间。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = sharedSettings.autoOptimizeInPowerSaver,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    sharedDataStore.setAutoOptimizeInPowerSaver(enabled)
                                }
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp))
                    val isPowerSaveMode by appViewModel.isPowerSaveMode.collectAsState()
                    val batteryLevel by appViewModel.batteryLevel.collectAsState()
                    KeyValueRow("当前电量", "$batteryLevel%")
                    KeyValueRow("省电模式", if (isPowerSaveMode) "已开启" else "未开启")
                    if (isPowerSaveMode && sharedSettings.autoOptimizeInPowerSaver) {
                        Text(
                            "✓ 已启用省电优化：液体玻璃特效已简化",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
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
                                "后台监控持仓、风险提醒和研究进度。所有设备使用标准通知；系统允许时会提交焦点通知协议。",
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
                                        appViewModel.disableOptionalPushForSettings()
                                        MonitorService.stop(context)
                                    }
                                } else if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    scope.launch {
                                        appViewModel.settings.setIslandEnabled(true)
                                        appViewModel.enableOptionalPush()
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
                    KeyValueRow("焦点通知权限", if (capabilities?.focusPermissionGranted == true) "已开启" else "未开启或不可查询")
                    KeyValueRow("小米超级岛 App ID", if (capabilities?.appIdConfigured == true) "已配置" else "未配置")
                    KeyValueRow(
                        "HyperOS 3 超级岛",
                        if (capabilities?.superIslandReady == true) "本机条件就绪" else "普通通知降级可用",
                    )
                    Text(
                        "客户端按小米超级岛 param_v2 规范持续更新同一通知；正式展示仍取决于平台场景审核、证书指纹、ROM 与用户通知设置。",
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

            item {
                AppCard {
                    Text("工作区数据共享", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "选择在连接版和独立版之间共享的设置项。持仓数据、研究记录和登录凭证始终隔离。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("主题设置", style = MaterialTheme.typography.bodyMedium)
                            Text("深色模式偏好", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sharedSettings.shareTheme,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    sharedDataStore.updateSharedSettings(sharedSettings.copy(shareTheme = enabled))
                                    if (enabled) {
                                        sharedDataStore.syncThemeMode(darkMode)
                                    }
                                }
                            },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("超级岛开关", style = MaterialTheme.typography.bodyMedium)
                            Text("后台监控通知", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sharedSettings.shareIsland,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    sharedDataStore.updateSharedSettings(sharedSettings.copy(shareIsland = enabled))
                                    if (enabled) {
                                        sharedDataStore.syncIslandEnabled(islandEnabled)
                                    }
                                }
                            },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("AI 配置", style = MaterialTheme.typography.bodyMedium)
                            Text("提供商和模型选择", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sharedSettings.shareAiConfig,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    sharedDataStore.updateSharedSettings(sharedSettings.copy(shareAiConfig = enabled))
                                }
                            },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("通知偏好", style = MaterialTheme.typography.bodyMedium)
                            Text("通知总开关状态", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sharedSettings.shareNotifications,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    sharedDataStore.updateSharedSettings(sharedSettings.copy(shareNotifications = enabled))
                                    if (enabled) {
                                        sharedDataStore.syncNotificationsEnabled(islandEnabled)
                                    }
                                }
                            },
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("玻璃态材质", style = MaterialTheme.typography.bodyMedium)
                            Text("UI 高级视觉效果", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = sharedSettings.shareGlassEffect,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    sharedDataStore.updateSharedSettings(sharedSettings.copy(shareGlassEffect = enabled))
                                    if (enabled) {
                                        sharedDataStore.syncGlassEffectEnabled(true)
                                    }
                                }
                            },
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {
                            scope.launch {
                                sharedDataStore.updateSharedValues(
                                    SharedDataStore.SharedValues(
                                        themeMode = darkMode,
                                        islandEnabled = islandEnabled,
                                        notificationsEnabled = islandEnabled,
                                        glassEffectEnabled = true,
                                    )
                                )
                                message = "当前设置已同步到共享存储"
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("立即同步到共享存储")
                    }

                    Text(
                        "切换工作区时自动应用已启用的共享设置。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

private fun com.ashareai.app.data.InfrastructureAvailability.label(): String = when (this) {
    com.ashareai.app.data.InfrastructureAvailability.Available -> "可用"
    com.ashareai.app.data.InfrastructureAvailability.Unavailable -> "不可用"
    com.ashareai.app.data.InfrastructureAvailability.Unknown -> "未知"
}
