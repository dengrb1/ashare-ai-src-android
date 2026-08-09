package com.ashareai.app.standalone

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.standalone.monitor.MarketMonitorService
import com.ashareai.app.standalone.ui.StandaloneAppRoot
import com.ashareai.app.standalone.ui.StandaloneTheme
import com.ashareai.app.standalone.ui.StandaloneViewModel
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeIntent(intent)
        setContent {
            val viewModel: StandaloneViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            var requestedNotifications by remember { mutableStateOf(false) }
            val notificationLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                requestedNotifications = true
            }
            LaunchedEffect(settings.firstRun, requestedNotifications) {
                if (
                    settings.firstRun &&
                    !requestedNotifications &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            LaunchedEffect(settings.monitoringEnabled) {
                if (settings.monitoringEnabled) MarketMonitorService.start(this@MainActivity)
            }
            StandaloneTheme {
                StandaloneAppRoot(
                    viewModel = viewModel,
                    pendingRoute = pendingRoute,
                    onRouteConsumed = { pendingRoute.value = null },
                )
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
    }

    companion object {
        const val EXTRA_ROUTE = "com.ashareai.app.standalone.extra.ROUTE"
    }
}
