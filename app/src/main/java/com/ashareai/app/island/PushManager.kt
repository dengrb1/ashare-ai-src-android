package com.ashareai.app.island

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ashareai.app.AShareApp
import com.ashareai.app.BuildConfig
import com.ashareai.app.MainActivity
import com.ashareai.app.R
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.SettingsStore
import com.ashareai.app.data.model.Notification
import com.ashareai.app.data.model.PushDeliveryReceipt
import com.ashareai.app.data.model.PushDeviceRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject

object PushManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private const val CLIENT_CLASS = "com.xiaomi.mipush.sdk.MiPushClient"
    val events = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    fun initialize(context: Context) {
        if (!isMainProcess(context) || !isConfigured()) return
        runCatching {
            val clazz = Class.forName(CLIENT_CLASS)
            clazz.getMethod(
                "registerPush",
                Context::class.java,
                String::class.java,
                String::class.java,
            ).invoke(null, context.applicationContext, BuildConfig.MIPUSH_APP_ID, BuildConfig.MIPUSH_APP_KEY)
        }
    }

    fun bindAuthenticatedDevice(context: Context) {
        if (!isConfigured()) return
        initialize(context)
        scope.launch {
            val registrationId = registrationId(context) ?: return@launch
            val settings = (context.applicationContext as AShareApp).settings
            val device = ApiClient.api.registerDevice(
                PushDeviceRequest(
                    installation_id = settings.installationId(),
                    registration_id = registrationId,
                    app_version = BuildConfig.VERSION_NAME,
                    os_version = Build.VERSION.RELEASE,
                    device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                ),
            )
            settings.setPushDeviceId(device.device_id)
        }
    }

    suspend fun unbindAuthenticatedDevice(context: Context) {
        val settings = (context.applicationContext as AShareApp).settings
        settings.currentPushDeviceId()?.let { deviceId ->
            runCatching { ApiClient.api.unregisterDevice(deviceId) }
        }
        settings.setPushDeviceId(null)
        runCatching {
            Class.forName(CLIENT_CLASS)
                .getMethod("unregisterPush", Context::class.java)
                .invoke(null, context.applicationContext)
        }
    }

    suspend fun handleMessage(context: Context, intent: Intent) {
        bindAuthenticatedDevice(context)
        val notificationId = notificationIdFromIntent(intent) ?: return
        handleNotificationId(
            context,
            notificationId,
            arrived = intent.action == "com.xiaomi.mipush.MESSAGE_ARRIVED",
            clicked = false,
        )
    }

    suspend fun handlePayload(context: Context, payload: String?, arrived: Boolean, clicked: Boolean) {
        bindAuthenticatedDevice(context)
        val notificationId = runCatching { JSONObject(payload.orEmpty()).optString("notification_id") }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: return
        handleNotificationId(context, notificationId, arrived, clicked)
    }

    private suspend fun handleNotificationId(
        context: Context,
        notificationId: String,
        arrived: Boolean,
        clicked: Boolean,
    ) {
        val notice = runCatching { ApiClient.api.notification(notificationId) }.getOrNull() ?: return
        events.tryEmit(Unit)
        acknowledge(context, notificationId, if (clicked) "OPENED" else "DELIVERED")
        val settings = (context.applicationContext as AShareApp).settings
        val firstArrival = settings.claimUnseenNotificationIds(listOf(notificationId)).contains(notificationId)
        if (arrived && firstArrival) {
            showForegroundNotification(context, notice)
        }
        if (clicked) {
            context.startActivity(Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_ROUTE, NotificationNavigation.forNotification(notice))
                putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notificationId)
            })
        }
    }

    fun notificationIdFromIntent(intent: Intent?): String? {
        val extras = intent?.extras ?: return null
        for (key in extras.keySet()) {
            val value = runCatching { extras.get(key) }.getOrNull()
            val text = value as? String ?: continue
            val id = runCatching { JSONObject(text).optString("notification_id") }.getOrNull()
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    fun acknowledgeOpened(context: Context, notificationId: String) {
        scope.launch { acknowledge(context, notificationId, "OPENED") }
    }

    private suspend fun acknowledge(context: Context, notificationId: String, status: String) {
        val settings: SettingsStore = (context.applicationContext as AShareApp).settings
        val deviceId = settings.currentPushDeviceId() ?: return
        runCatching {
            ApiClient.api.acknowledgeDelivery(
                deviceId,
                PushDeliveryReceipt(notification_id = notificationId, status = status),
            )
        }
    }

    private fun showForegroundNotification(context: Context, notice: Notification) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val route = NotificationNavigation.forNotification(notice)
        val pendingIntent = PendingIntent.getActivity(
            context,
            notice.notification_id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_ROUTE, route)
                putExtra(MainActivity.EXTRA_NOTIFICATION_ID, notice.notification_id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val channel = if (notice.severity.uppercase() in setOf("HIGH", "CRITICAL")) {
            AShareApp.CHANNEL_ALERT
        } else {
            AShareApp.CHANNEL_PROGRESS
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        val notification = FocusNotification.decorate(
            context = context,
            builder = builder,
            title = notice.title,
            content = notice.body,
            enableFloat = notice.severity.uppercase() in setOf("HIGH", "CRITICAL"),
        )
        context.getSystemService(NotificationManager::class.java)
            .notify(notice.notification_id.hashCode(), notification)
    }

    private fun registrationId(context: Context): String? = runCatching {
        Class.forName(CLIENT_CLASS)
            .getMethod("getRegId", Context::class.java)
            .invoke(null, context.applicationContext) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun isConfigured(): Boolean =
        BuildConfig.MIPUSH_APP_ID.isNotBlank() && BuildConfig.MIPUSH_APP_KEY.isNotBlank()

    private fun isMainProcess(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java)
        val process = manager.runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }
        return process?.processName == context.applicationInfo.processName
    }
}
