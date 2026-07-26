package com.ashareai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.island.MonitorService
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.navigation.AppRoot
import com.ashareai.app.ui.theme.AShareTheme

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val darkMode by appViewModel.settings.darkMode.collectAsState(initial = "system")
            val authState by appViewModel.authState.collectAsState()
            val islandEnabled by appViewModel.settings.islandEnabled.collectAsState(initial = true)

            // 登录后按设置启动持仓监控前台服务
            LaunchedEffect(authState, islandEnabled) {
                if (authState is AppViewModel.AuthState.LoggedIn && islandEnabled) {
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
                AppRoot(appViewModel)
            }
        }
    }
}
