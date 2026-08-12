package com.ashareai.app.standalone

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.standalone.monitor.MarketMonitorService
import com.ashareai.app.standalone.ui.DevicePermissionState
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
                if (settings.monitoringEnabled) MarketMonitorService.start(this@MainActivity)
            }
            StandaloneTheme {
                StandaloneAppRoot(
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
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeIntent(intent)
    }

    private fun consumeIntent(intent: Intent?) {
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
    }

    private fun readPermissionState(): DevicePermissionState {
        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        }
        val powerManager = getSystemService(PowerManager::class.java)
        return DevicePermissionState(
            notificationsGranted = notificationGranted,
            batteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName),
        )
    }

    companion object {
        const val EXTRA_ROUTE = "com.ashareai.app.standalone.extra.ROUTE"
    }
}
