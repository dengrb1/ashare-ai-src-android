package com.ashareai.app.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ashareai.app.AShareApp
import com.ashareai.app.MainActivity
import com.ashareai.app.R
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * 持仓监控前台服务：
 * 1. 常驻通知（超级岛）显示总浮动盈亏，按用户刷新间隔轮询行情。
 * 2. 轮询通知 summary，出现卖出/止损/高风险通知时以焦点浮窗弹出。
 * 3. 存在活动研究任务时展示进度。
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private val df = DecimalFormat("#,##0.00")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundCompat(buildMonitorNotification("持仓监控", "正在获取行情…", null, null))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                (application as AShareApp).settings.setIslandEnabled(false)
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (loop == null) {
            loop = scope.launch { monitorLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        scope.launch { (application as AShareApp).settings.setIslandEnabled(false) }
        stopSelf(startId)
    }

    private fun startForegroundCompat(notification: Notification) {
        startForeground(NOTIFY_ID_MONITOR, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private suspend fun monitorLoop() {
        val settings = (application as AShareApp).settings
        while (true) {
            val interval = runCatching { updateOnce(settings) }.getOrDefault(30)
            delay(interval * 1000L)
        }
    }

    /** 返回下次轮询间隔（秒）。 */
    private suspend fun updateOnce(settings: SettingsStore): Int {
        val token = settings.currentAccessToken()
        if (token.isNullOrBlank()) {
            notifyMonitor("持仓监控", "未登录", null, null)
            return 60
        }
        val api = ApiClient.api

        // 1) 持仓盈亏
        var interval = 30
        try {
            val assets = api.assets()
            interval = assets.market_refresh_interval_seconds.coerceAtLeast(15)
            val symbols = assets.positions.map { it.symbol }.distinct()
            if (symbols.isEmpty()) {
                notifyMonitor("持仓监控", "暂无持仓", null, null)
            } else {
                val quotes = api.quotes(symbols.joinToString(",")).associateBy { it.symbol }
                var cost = 0.0
                var value = 0.0
                assets.positions.forEach { p ->
                    val price = quotes[p.symbol]?.price ?: p.cost
                    cost += p.cost * p.quantity
                    value += price * p.quantity
                }
                val pnl = value - cost
                val pct = if (cost > 0) pnl / cost * 100 else 0.0
                val sign = if (pnl >= 0) "+" else ""
                notifyMonitor(
                    title = "持仓盈亏",
                    content = "$sign${df.format(pnl)}",
                    subContent = "$sign${df.format(pct)}% · ${assets.positions.size}只持仓",
                    color = if (pnl >= 0) "#E53935" else "#00A86B",
                )
            }
        } catch (_: Exception) {
            // 保持上次内容
        }

        // 2) 预警通知（卖出建议 / 止损 / 高风险）
        try {
            val summary = api.notificationSummary()
            val unseen = settings.claimUnseenNotificationIds(
                summary.latest.filter { it.read_at == null }.map { it.notification_id },
            )
            summary.latest
                .filter { it.read_at == null && it.notification_id in unseen }
                .filter { it.severity.uppercase() in setOf("HIGH", "CRITICAL", "WARNING") }
                .take(3)
                .forEach { n ->
                    notifyAlert(
                        n.notification_id.hashCode(),
                        n.title,
                        n.body,
                        NotificationNavigation.forNotification(n),
                    )
                }
        } catch (_: Exception) {
        }

        // 3) 活动研究任务进度
        try {
            val active = api.researchRuns(limit = 5, mine = true)
                .firstOrNull { it.status.uppercase() in setOf("PENDING", "QUEUED", "RUNNING", "PROCESSING") }
            if (active != null) {
                notifyProgress(active.phase ?: "研究进行中", active.progress ?: 0)
            } else {
                cancelProgress()
            }
        } catch (_: Exception) {
        }

        return interval
    }

    private fun contentIntent(route: String = "home"): PendingIntent = PendingIntent.getActivity(
        this, route.hashCode(),
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        0,
        Intent(this, MonitorService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun buildMonitorNotification(title: String, content: String, subContent: String?, color: String?): Notification {
        val builder = NotificationCompat.Builder(this, AShareApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle(title)
            .setContentText(listOfNotNull(content, subContent).joinToString(" "))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent())
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "停止监控", stopIntent())
        return FocusNotification.decorate(
            context = this,
            builder = builder,
            title = title,
            content = content,
            subContent = subContent,
            colorContent = color,
            ticker = content,
            enableFloat = false,
            timeoutMinutes = 720,
            islandTimeoutSeconds = 3_600,
        )
    }

    private fun notifyMonitor(title: String, content: String, subContent: String?, color: String?) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFY_ID_MONITOR, buildMonitorNotification(title, content, subContent, color))
    }

    private fun notifyAlert(id: Int, title: String, body: String, route: String) {
        val builder = NotificationCompat.Builder(this, AShareApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(contentIntent(route))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val notification = FocusNotification.decorate(
            context = this,
            builder = builder,
            title = title,
            content = body.take(40),
            ticker = title,
            enableFloat = true,
            timeoutMinutes = 10,
            islandTimeoutSeconds = 600,
        )
        getSystemService(NotificationManager::class.java).notify(id, notification)
    }

    private fun notifyProgress(phase: String, progress: Int) {
        val builder = NotificationCompat.Builder(this, AShareApp.CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle("每日研究")
            .setContentText("$phase · $progress%")
            .setProgress(100, progress, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent("research"))
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val notification = FocusNotification.decorate(
            context = this,
            builder = builder,
            title = "每日研究",
            content = "$progress%",
            subContent = phase,
            ticker = "研究 $progress%",
            enableFloat = false,
            timeoutMinutes = 30,
            islandTimeoutSeconds = 1_800,
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFY_ID_PROGRESS, notification)
    }

    private fun cancelProgress() {
        getSystemService(NotificationManager::class.java).cancel(NOTIFY_ID_PROGRESS)
    }

    companion object {
        private const val NOTIFY_ID_MONITOR = 1001
        private const val NOTIFY_ID_PROGRESS = 1002
        private const val ACTION_STOP = "com.ashareai.app.island.STOP"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, MonitorService::class.java))
        }

        @android.annotation.SuppressLint("ImplicitSamInstance")
        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
