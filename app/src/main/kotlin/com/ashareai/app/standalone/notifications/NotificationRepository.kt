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
import com.ashareai.app.standalone.MainActivity
import com.ashareai.app.standalone.R
import com.ashareai.app.standalone.StandaloneApp
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
            manager().notify(stableId(item.id), buildNotification(item))
        }
        return item
    }

    fun monitoringNotification(title: String, body: String): Notification {
        val route = safeRoute("home")
        val builder = baseBuilder(
            channel = StandaloneApp.CHANNEL_NORMAL,
            title = title,
            body = body,
            route = route,
        ).setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        return decorateIfEnabled(
            builder = builder,
            title = title,
            body = body,
            subContent = "持仓行情监控",
            color = "#E53935",
            enableFloat = false,
        )
    }

    fun researchProgressNotification(title: String, body: String): Notification {
        val builder = baseBuilder(
            channel = StandaloneApp.CHANNEL_PROGRESS,
            title = title,
            body = body,
            route = "research",
        ).setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        return decorateIfEnabled(
            builder = builder,
            title = title,
            body = body,
            subContent = "本地研究进度",
            color = "#1E88E5",
            enableFloat = false,
        )
    }

    fun showIslandTest() = FocusNotification.showTest(context)

    private fun buildNotification(item: LocalNotification): Notification {
        val channel = when (item.priority) {
            NotificationPriority.NORMAL -> StandaloneApp.CHANNEL_NORMAL
            NotificationPriority.WARNING -> StandaloneApp.CHANNEL_ALERT
            NotificationPriority.PROGRESS -> StandaloneApp.CHANNEL_PROGRESS
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
        return decorateIfEnabled(
            builder = builder,
            title = item.title,
            body = item.body,
            subContent = null,
            color = null,
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
}
