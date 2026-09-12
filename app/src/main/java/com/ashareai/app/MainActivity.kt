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
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.workspace.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
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
            val workspace by app.workspaceStore.currentWorkspace.collectAsState(initial = Workspace.LOCAL)

            AnimatedContent(
                targetState = workspace,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(300)) + scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(300)
                    )).togetherWith(
                        fadeOut(animationSpec = tween(300)) + scaleOut(
                            targetScale = 0.95f,
                            animationSpec = tween(300)
                        )
                    )
                },
                label = "workspace_transition"
            ) { targetWorkspace ->
                when (targetWorkspace) {
                    Workspace.LOCAL -> {
                    // 本地工作区：复用原 standalone MainActivity 逻辑
                    val viewModel: com.ashareai.app.standalone.ui.StandaloneViewModel = viewModel()
                    val settings by viewModel.settings.collectAsState()
                    var permissionState by remember { mutableStateOf(readPermissionState()) }
                    val notificationLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission(),
                    ) {
                        permissionState = readPermissionState()
                    }
                    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                        permissionState = readPermissionState()
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
                        }
                    }
                    com.ashareai.app.standalone.ui.StandaloneTheme {
                        com.ashareai.app.standalone.ui.StandaloneAppRoot(
                            viewModel = viewModel,
                            pendingRoute = pendingRoute,
                            onRouteConsumed = { pendingRoute.value = null },
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

                    com.ashareai.app.ui.theme.AShareTheme(darkModePref = darkMode) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            com.ashareai.app.ui.LocalAppContainer provides appViewModel.container,
                            com.ashareai.app.ui.LocalMarketViewModel provides marketViewModel,
                        ) {
                            com.ashareai.app.ui.navigation.AppRoot(appViewModel, pendingRoute) {
                                pendingRoute.value = null
                            }
                        }
                    }
                }
            }
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
        // 前后台状态由各工作区 ViewModel 自行管理
    }

    override fun onStop() {
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
