package com.ashareai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.NotificationManagerCompat
import com.ashareai.app.island.MonitorService
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.navigation.AppRoot
import com.ashareai.app.ui.theme.AShareTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val darkMode by appViewModel.settings.darkMode.collectAsState(initial = "system")
            val authState by appViewModel.authState.collectAsState()
            val islandEnabled by appViewModel.settings.islandEnabled.collectAsState(initial = true)

            // 登录后按设置启动持仓监控前台服务
            LaunchedEffect(authState, islandEnabled) {
                if (authState is AppViewModel.AuthState.LoggedIn && islandEnabled &&
                    NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
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
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "com.ashareai.app.extra.ROUTE"
    }
}
