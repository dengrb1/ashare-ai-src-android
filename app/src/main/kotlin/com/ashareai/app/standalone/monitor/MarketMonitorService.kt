package com.ashareai.app.standalone.monitor

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

class MarketMonitorService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitorJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = com.ashareai.app.HybridApp.from(this)
        startForeground(
            NOTIFICATION_ID,
            app.localContainer.notifications.monitoringNotification("持仓监控启动中", "正在检查交易时段与持仓"),
        )
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            app.localContainer.monitoring.monitorLoop { snapshot ->
                getSystemService(android.app.NotificationManager::class.java).notify(
                    NOTIFICATION_ID,
                    app.localContainer.notifications.monitoringNotification(snapshot.title, snapshot.body),
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        val app = com.ashareai.app.HybridApp.from(this)
        app.localContainer.monitoringFallbackScheduler.schedule()
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
            val intent = Intent(context, MarketMonitorService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MarketMonitorService::class.java))
        }
    }
}
