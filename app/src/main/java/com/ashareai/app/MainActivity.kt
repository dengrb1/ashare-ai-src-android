package com.ashareai.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ashareai.app.island.MonitorService
import com.ashareai.app.island.PushManager
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.LocalAppContainer
import com.ashareai.app.ui.LocalMarketViewModel
import com.ashareai.app.ui.MarketViewModel
import com.ashareai.app.ui.navigation.AppRoot
import com.ashareai.app.ui.theme.AShareTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingRoute = MutableStateFlow<String?>(null)
    private lateinit var appViewModel: AppViewModel
    private lateinit var marketViewModel: MarketViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        appViewModel = ViewModelProvider(this)[AppViewModel::class.java]
        marketViewModel = ViewModelProvider(this)[MarketViewModel::class.java]
        appViewModel.attachHostContext(this)
        setContent {
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
            // 登录后按设置启动持仓监控前台服务
            LaunchedEffect(authState, islandEnabled, notificationsGranted) {
                if (authState is AppViewModel.AuthState.LoggedIn && islandEnabled &&
                    notificationsGranted && NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()
                ) {
                    MonitorService.start(this@MainActivity)
                } else {
                    MonitorService.stop(this@MainActivity)
                }
            }

            LaunchedEffect(authState, foreground) {
                marketViewModel.bindSession(
                    isSignedIn = authState is AppViewModel.AuthState.LoggedIn,
                    isForeground = foreground,
                )
            }

            AShareTheme(darkModePref = darkMode) {
                CompositionLocalProvider(
                    LocalAppContainer provides appViewModel.container,
                    LocalMarketViewModel provides marketViewModel,
                ) {
                    AppRoot(appViewModel, pendingRoute) { pendingRoute.value = null }
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
        appViewModel.onForeground()
    }

    override fun onStop() {
        appViewModel.onBackground()
        super.onStop()
    }

    override fun onDestroy() {
        appViewModel.detachHostContext(this)
        super.onDestroy()
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
