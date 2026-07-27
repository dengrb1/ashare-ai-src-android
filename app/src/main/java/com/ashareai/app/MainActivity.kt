package com.ashareai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ashareai.app.island.MonitorService
import com.ashareai.app.island.PushManager
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.navigation.AppRoot
import com.ashareai.app.ui.theme.AShareTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val darkMode by appViewModel.settings.darkMode.collectAsState(initial = "system")
            val authState by appViewModel.authState.collectAsState()
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
            var notificationPermissionRequested by remember { mutableStateOf(false) }
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                notificationsGranted = granted
            }

            LaunchedEffect(notificationsGranted, notificationPermissionRequested) {
                if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted && !notificationPermissionRequested) {
                    notificationPermissionRequested = true
                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // 登录后按设置启动持仓监控前台服务
            LaunchedEffect(authState, islandEnabled, notificationsGranted) {
                if (authState is AppViewModel.AuthState.LoggedIn && islandEnabled &&
                    notificationsGranted && NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                ) {
                    MonitorService.start(this@MainActivity)
                } else if (authState is AppViewModel.AuthState.LoggedOut) {
                    MonitorService.stop(this@MainActivity)
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> appViewModel.onForeground()
                        Lifecycle.Event.ON_STOP -> appViewModel.onBackground()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            AShareTheme(darkModePref = darkMode) {
                AppRoot(appViewModel, pendingRoute) { pendingRoute.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
            ?: PushManager.notificationIdFromIntent(intent)?.let { "notifications" }
        intent?.getStringExtra(EXTRA_NOTIFICATION_ID)?.let { PushManager.acknowledgeOpened(this, it) }
        PushManager.notificationIdFromIntent(intent)?.let { PushManager.acknowledgeOpened(this, it) }
    }

    companion object {
        const val EXTRA_ROUTE = "com.ashareai.app.extra.ROUTE"
        const val EXTRA_NOTIFICATION_ID = "com.ashareai.app.extra.NOTIFICATION_ID"
    }
}
