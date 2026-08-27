package com.ashareai.app.standalone.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ashareai.app.MainActivity
import com.ashareai.app.R
import com.ashareai.app.HybridApp
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.domain.LocalNotification
import com.ashareai.app.standalone.domain.NotificationPriority
import com.ashareai.app.standalone.island.FocusNotification
import java.util.UUID

class NotificationRepository(
    private val context: Context,
    private val local: LocalRepository,
    private val attachIslandPayload: () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun publish(
        title: String,
        body: String,
        priority: NotificationPriority,
        deepLink: String = "notifications",
        notificationId: String = UUID.randomUUID().toString(),
        systemNotificationId: Int? = null,
    ): LocalNotification {
        val item = LocalNotification(
            id = notificationId,
            title = title,
            body = body,
            priority = priority,
            deepLink = safeRoute(deepLink),
            createdAt = clock(),
            isRead = false,
        )
        // Persist first so a notification is never lost when the OS rejects delivery.
        local.saveNotification(item)
        if (canPostNotifications()) {
            manager().notify(systemNotificationId ?: stableId(item.id), buildNotification(item))
        }
        return item
    }

    fun showResearchProgress(title: String, body: String, progress: Int? = null) {
        if (canPostNotifications()) {
            manager().notify(
                RESEARCH_ACTIVITY_NOTIFICATION_ID,
                researchProgressNotification(title, body, progress),
            )
        }
    }

    fun monitoringNotification(title: String, body: String): Notification {
        val route = safeRoute("home")
        val builder = baseBuilder(
            channel = HybridApp.CHANNEL_LOCAL_NORMAL,
            title = title,
            body = body,
            route = route,
        ).setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)  // 静默通知，不打扰用户
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        // 监控服务通知：不显示超级岛，避免干扰
        return decorateIfEnabled(
            builder = builder,
            title = title,
            body = body,
            subContent = "持仓行情监控",
            color = "#616161",  // 灰色，低调
            enableFloat = false,  // 不启用超级岛
        )
    }

    fun researchProgressNotification(title: String, body: String, progress: Int? = null): Notification {
        val builder = baseBuilder(
            channel = HybridApp.CHANNEL_LOCAL_PROGRESS,
            title = title,
            body = body,
            route = "research",
        ).setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)  // 静默通知
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, progress?.coerceIn(0, 100) ?: 0, progress == null)
        // 进度通知：不显示超级岛
        return decorateIfEnabled(
            builder = builder,
            title = title,
            body = body,
            subContent = progress?.let { "本地研究进度 $it%" } ?: "本地研究进度",
            color = "#1E88E5",
            enableFloat = false,  // 不启用超级岛
        )
    }

    fun showIslandTest() = FocusNotification.showTest(context)

    private fun buildNotification(item: LocalNotification): Notification {
        val channel = when (item.priority) {
            NotificationPriority.NORMAL -> HybridApp.CHANNEL_LOCAL_NORMAL
            NotificationPriority.WARNING -> HybridApp.CHANNEL_LOCAL_ALERT
            NotificationPriority.PROGRESS -> HybridApp.CHANNEL_LOCAL_PROGRESS
        }
        val builder = baseBuilder(channel, item.title, item.body, item.deepLink)
            .setAutoCancel(true)
            .setPriority(
                if (item.priority == NotificationPriority.WARNING) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
        // 只有 WARNING 类型才显示超级岛，其他类型普通通知即可
        return decorateIfEnabled(
            builder = builder,
            title = item.title,
            body = item.body,
            subContent = when (item.priority) {
                NotificationPriority.WARNING -> "重要行情提醒"
                NotificationPriority.NORMAL -> null
                NotificationPriority.PROGRESS -> null
            },
            color = if (item.priority == NotificationPriority.WARNING) "#E53935" else null,
            enableFloat = item.priority == NotificationPriority.WARNING,
        )
    }

    private fun baseBuilder(
        channel: String,
        title: String,
        body: String,
        route: String,
    ): NotificationCompat.Builder = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_trend)
        .setContentTitle(title)
        .setContentText(body)
        .setContentIntent(contentIntent(route))
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

    private fun contentIntent(route: String): PendingIntent = PendingIntent.getActivity(
        context,
        stableId(route),
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROUTE, route)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun canPostNotifications(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun manager() = context.getSystemService(NotificationManager::class.java)

    private fun stableId(value: String): Int = value.hashCode().and(0x7fffffff)

    private fun decorateIfEnabled(
        builder: NotificationCompat.Builder,
        title: String,
        body: String,
        subContent: String?,
        color: String?,
        enableFloat: Boolean,
    ): Notification = if (attachIslandPayload()) {
        FocusNotification.decorate(
            context = context,
            builder = builder,
            title = title,
            content = body,
            subContent = subContent,
            colorContent = color,
            enableFloat = enableFloat,
        )
    } else {
        builder.build()
    }

    private fun safeRoute(value: String): String = value.takeIf {
        it in setOf(
            "home",
            "market",
            "assets",
            "alerts",
            "research",
            "reports",
            "candidates",
            "portfolio",
            "notifications",
            "chat",
            "settings",
        )
    } ?: "home"

    companion object {
        const val RESEARCH_ACTIVITY_NOTIFICATION_ID = 4402
    }
}
