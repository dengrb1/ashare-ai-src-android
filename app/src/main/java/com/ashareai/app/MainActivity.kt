package com.ashareai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.workspace.Workspace
import com.ashareai.app.ui.components.WorkspaceSwitcher
import com.ashareai.app.performance.AppVisibilityState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * 统一 MainActivity：根据当前工作区启动不同的 Compose 根。
 *
 * 本地工作区：StandaloneAppRoot + StandaloneViewModel
 * Fusion 工作区：AppRoot + AppViewModel
 *
 * 通知深链路由携带工作区标识，点击通知时自动切换到对应工作区。
 */
class MainActivity : ComponentActivity() {
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)

        val app = HybridApp.from(this)

        setContent {
            val workspaceState by app.workspaceStore.workspaceState.collectAsState(
                initial = com.ashareai.app.workspace.WorkspaceState(
                    current = Workspace.LOCAL,
                    lastLocalRoute = null,
                    lastFusionRoute = null,
                    fusionSessionValid = false,
                ),
            )
            val workspace = workspaceState.current
            val localAnimationsFlow = remember(app.localContainer.settings.settings) {
                app.localContainer.settings.settings
                .map { it.fullAnimationsEnabled }
            }
            val localFullAnimationsEnabled by localAnimationsFlow.collectAsState(initial = true)
            val fusionFullAnimationsEnabled by app.fusionSettings.fullAnimationsEnabled.collectAsState(initial = true)
            val fullAnimationsEnabled = if (workspace == Workspace.LOCAL) {
                localFullAnimationsEnabled
            } else {
                fusionFullAnimationsEnabled
            }
            val systemPowerSave = remember {
                getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            }
            var workspaceSwitchInFlight by remember { mutableStateOf(false) }
            LaunchedEffect(workspace) {
                workspaceSwitchInFlight = false
            }

            Box(Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = workspace,
                    transitionSpec = {
                        if (!fullAnimationsEnabled || systemPowerSave) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (fadeIn(animationSpec = tween(380)) + scaleIn(
                                initialScale = 0.985f,
                                animationSpec = tween(380),
                            )).togetherWith(
                                fadeOut(animationSpec = tween(260)) + scaleOut(
                                    targetScale = 0.985f,
                                    animationSpec = tween(260),
                                )
                            )
                        }
                    },
                    label = "workspace_transition",
                ) { targetWorkspace ->
                when (targetWorkspace) {
                    Workspace.LOCAL -> {
                    // 本地工作区：复用原 standalone MainActivity 逻辑
                    val viewModel: com.ashareai.app.standalone.ui.StandaloneViewModel = viewModel()
                    val settings by viewModel.settings.collectAsState()
                    val isPowerSaveMode by viewModel.isPowerSaveMode.collectAsState()
                    var permissionState by remember { mutableStateOf(readPermissionState()) }
                    val notificationLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) {
                        permissionState = readPermissionState()
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        permissionState = readPermissionState()
                    }
                    DisposableEffect(viewModel) {
                        onDispose {
                            com.ashareai.app.standalone.monitor.MarketMonitorService.stop(this@MainActivity)
                        }
                    }
                    val requestNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                },
                            )
                        }
                    }
                    LaunchedEffect(settings.monitoringEnabled) {
                        if (settings.monitoringEnabled) {
                            com.ashareai.app.standalone.monitor.MarketMonitorService.start(this@MainActivity)
                        } else {
                            com.ashareai.app.standalone.monitor.MarketMonitorService.stop(this@MainActivity)
                        }
                    }
                    com.ashareai.app.standalone.ui.StandaloneTheme(
                        darkModePref = settings.darkMode,
                        glassEnabled = settings.glassEnabled,
                        fullAnimationsEnabled = settings.fullAnimationsEnabled,
                        powerSaveMode = isPowerSaveMode,
                    ) {
                        com.ashareai.app.standalone.ui.StandaloneAppRoot(
                            viewModel = viewModel,
                            pendingRoute = pendingRoute,
                            onRouteConsumed = { pendingRoute.value = null },
                            initialRoute = workspaceState.lastLocalRoute,
                            onRouteChanged = { route ->
                                lifecycleScope.launch {
                                    app.workspaceStore.saveLastRoute(Workspace.LOCAL, route)
                                }
                            },
                            onSwitchToConnected = {
                                lifecycleScope.launch {
                                    com.ashareai.app.standalone.monitor.MarketMonitorService.stop(this@MainActivity)
                                    app.workspaceStore.setWorkspace(Workspace.FUSION)
                                }
                            },
                            permissionState = permissionState,
                            onRequestNotifications = requestNotifications,
                            onOpenBatterySettings = {
                                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                            },
                            onOpenAppSettings = {
                                startActivity(
                                    Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        "package:$packageName".toUri(),
                                    ),
                                )
                            },
                        )
                    }
                }

                Workspace.FUSION -> {
                    // Fusion 工作区：复用原 connected MainActivity 逻辑
                    val appViewModel: com.ashareai.app.ui.AppViewModel = viewModel()
                    val marketViewModel: com.ashareai.app.ui.MarketViewModel = viewModel()
                    val darkMode by appViewModel.settings.darkMode.collectAsState(initial = "system")
                    val glassEnabled by appViewModel.settings.glassEnabled.collectAsState(initial = true)
                    val fullAnimationsEnabled by appViewModel.settings.fullAnimationsEnabled.collectAsState(initial = true)
                    val isPowerSaveMode by appViewModel.isPowerSaveMode.collectAsState()
                    val authState by appViewModel.authState.collectAsState()
                    val foreground by appViewModel.foreground.collectAsState()
                    val islandEnabled by appViewModel.settings.islandEnabled.collectAsState(initial = false)
                    var notificationsGranted by remember {
                        mutableStateOf(
                            Build.VERSION.SDK_INT < 33 ||
                                ContextCompat.checkSelfPermission(
                                    this@MainActivity,
                                    Manifest.permission.POST_NOTIFICATIONS,
                                ) == PackageManager.PERMISSION_GRANTED,
                        )
                    }

                    LaunchedEffect(appViewModel) {
                        appViewModel.attachHostContext(this@MainActivity)
                    }

                    DisposableEffect(appViewModel) {
                        val owner = this@MainActivity
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> appViewModel.onForeground()
                                Lifecycle.Event.ON_STOP -> appViewModel.onBackground()
                                else -> Unit
                            }
                        }
                        owner.lifecycle.addObserver(observer)
                        onDispose {
                            owner.lifecycle.removeObserver(observer)
                            appViewModel.onBackground()
                            marketViewModel.bindSession(isSignedIn = false, isForeground = false)
                            com.ashareai.app.island.MonitorService.stop(this@MainActivity)
                            appViewModel.detachHostContext(owner)
                        }
                    }

                    // 登录后按设置启动持仓监控前台服务
                    LaunchedEffect(authState, islandEnabled, notificationsGranted) {
                        if (authState is com.ashareai.app.ui.AppViewModel.AuthState.LoggedIn && islandEnabled &&
                            notificationsGranted && NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                        ) {
                            com.ashareai.app.island.MonitorService.start(this@MainActivity)
                        } else {
                            com.ashareai.app.island.MonitorService.stop(this@MainActivity)
                        }
                    }

                    LaunchedEffect(authState, foreground) {
                        marketViewModel.bindSession(
                            isSignedIn = authState is com.ashareai.app.ui.AppViewModel.AuthState.LoggedIn,
                            isForeground = foreground,
                        )
                    }

                    LaunchedEffect(Unit) {
                        if (authState is com.ashareai.app.ui.AppViewModel.AuthState.LoggedIn) {
                            appViewModel.onForeground()
                        }
                    }

                    com.ashareai.app.ui.theme.AShareTheme(
                        darkModePref = darkMode,
                        glassEnabled = glassEnabled,
                        fullAnimationsEnabled = fullAnimationsEnabled,
                        powerSaveMode = isPowerSaveMode,
                    ) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.ashareai.app.ui.LocalAppContainer provides appViewModel.container,
                            com.ashareai.app.ui.LocalMarketViewModel provides marketViewModel,
                        ) {
                            com.ashareai.app.ui.navigation.AppRoot(
                                appViewModel = appViewModel,
                                pendingRoute = pendingRoute,
                                onRouteConsumed = { pendingRoute.value = null },
                                initialRoute = workspaceState.lastFusionRoute,
                                onRouteChanged = { route ->
                                    lifecycleScope.launch {
                                        app.workspaceStore.saveLastRoute(Workspace.FUSION, route)
                                    }
                                },
                                onSwitchToLocal = {
                                    lifecycleScope.launch {
                                        com.ashareai.app.island.MonitorService.stop(this@MainActivity)
                                        app.workspaceStore.setWorkspace(Workspace.LOCAL)
                                    }
                                },
                            )
                        }
                    }
                }
                }
                }
                WorkspaceSwitcher(
                    workspace = workspace,
                    onWorkspaceSelected = { selected ->
                        if (selected == workspace || workspaceSwitchInFlight) return@WorkspaceSwitcher
                        workspaceSwitchInFlight = true
                        lifecycleScope.launch {
                            if (selected == Workspace.LOCAL) {
                                com.ashareai.app.island.MonitorService.stop(this@MainActivity)
                            } else {
                                com.ashareai.app.standalone.monitor.MarketMonitorService.stop(this@MainActivity)
                            }
                            app.workspaceStore.setWorkspace(selected)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(top = 6.dp, end = 12.dp),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibilityState.isForeground = true
    }

    override fun onStop() {
        AppVisibilityState.isForeground = false
        super.onStop()
    }

    private fun consumeIntent(intent: Intent?) {
        // 通知深链路由携带工作区标识
        val workspace = intent?.getStringExtra(EXTRA_WORKSPACE)
        val route = intent?.getStringExtra(EXTRA_ROUTE)

        if (workspace != null && route != null) {
            // 切换到指定工作区并设置路由
            val app = HybridApp.from(this)
            lifecycleScope.launch {
                val targetWorkspace = when (workspace) {
                    "FUSION" -> Workspace.FUSION
                    else -> Workspace.LOCAL
                }
                app.workspaceStore.setWorkspace(targetWorkspace)
                pendingRoute.value = route
            }
        } else {
            // 兼容旧的 EXTRA_ROUTE（不带工作区标识）
            pendingRoute.value = route
                ?: com.ashareai.app.island.PushManager.notificationIdFromIntent(intent)?.let { "notifications" }
        }

        intent?.getStringExtra(EXTRA_NOTIFICATION_ID)?.let {
            com.ashareai.app.island.PushManager.acknowledgeOpened(this, it)
        }
        com.ashareai.app.island.PushManager.notificationIdFromIntent(intent)?.let {
            com.ashareai.app.island.PushManager.acknowledgeOpened(this, it)
        }
    }

    private fun readPermissionState(): com.ashareai.app.standalone.ui.DevicePermissionState {
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
        val powerManager = getSystemService(PowerManager::class.java)
        return com.ashareai.app.standalone.ui.DevicePermissionState(
            notificationsGranted = notificationGranted,
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName),
        )
    }

    companion object {
        const val EXTRA_WORKSPACE = "com.ashareai.app.extra.WORKSPACE"
        const val EXTRA_ROUTE = "com.ashareai.app.extra.ROUTE"
        const val EXTRA_NOTIFICATION_ID = "com.ashareai.app.extra.NOTIFICATION_ID"
    }
}
