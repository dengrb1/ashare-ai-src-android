package com.ashareai.app.standalone.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.ashareai.app.standalone.StandaloneApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MarketMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as StandaloneApp
        startForeground(
            NOTIFICATION_ID,
            app.container.notifications.monitoringNotification("持仓监控启动中", "正在检查交易时段与持仓"),
        )
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            app.container.monitoring.monitorLoop { snapshot ->
                getSystemService(android.app.NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    app.container.notifications.monitoringNotification(snapshot.title, snapshot.body),
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val app = application as StandaloneApp
        app.container.monitoringFallbackScheduler.schedule()
        monitorJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 4401

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(context, Intent(context, MarketMonitorService::class.java))
            }.onFailure {
                (context.applicationContext as? StandaloneApp)
                    ?.container
                    ?.monitoringFallbackScheduler
                    ?.schedule()
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MarketMonitorService::class.java))
        }
    }
}
