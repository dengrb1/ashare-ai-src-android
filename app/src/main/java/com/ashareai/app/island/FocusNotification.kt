package com.ashareai.app.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ashareai.app.AShareApp
import com.ashareai.app.MainActivity
import com.ashareai.app.R
import com.xzakota.hyper.notification.focus.FocusNotification as HyperFocusNotification

data class FocusCapabilities(
    val protocolVersion: Int,
    val islandSupported: Boolean,
) {
    val focusSupported: Boolean get() = protocolVersion > 0
    val superIslandReady: Boolean get() = protocolVersion >= MIN_SUPER_ISLAND_PROTOCOL
    /** HyperOS does not consistently expose the protocol setting to third-party apps. */
    val v3PayloadAttached: Boolean get() = true

    private companion object {
        const val MIN_SUPER_ISLAND_PROTOCOL = 3
    }
}

/**
 * Normalized input for HyperOS's undocumented local focus-notification protocol.
 *
 * Keeping normalization separate makes all notification sources use the same limits and
 * permits JVM tests without a Xiaomi device.
 */
internal data class IslandNotificationSpec(
    val title: String,
    val content: String,
    val subContent: String?,
    val colorContent: String?,
    val ticker: String,
    val enableFloat: Boolean,
    val timeoutMinutes: Int,
    val islandTimeoutSeconds: Int,
) {
    fun normalized() = copy(
        title = title.take(40),
        content = content.take(80),
        subContent = subContent?.take(80)?.takeIf { it.isNotBlank() },
        ticker = ticker.take(30),
        timeoutMinutes = timeoutMinutes.coerceIn(1, 720),
        islandTimeoutSeconds = islandTimeoutSeconds.coerceIn(60, 3_600),
    )
}

/** Xiaomi HyperOS focus notification / Super Island compatibility layer. */
object FocusNotification {
    private const val TEST_NOTIFICATION_ID = 1903

    fun capabilities(context: Context): FocusCapabilities {
        val protocol = runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)
        return FocusCapabilities(
            protocolVersion = protocol,
            // Protocol v3 is the capability used by InstallerX and contains the Island schema.
            islandSupported = protocol >= 3,
        )
    }

    /**
     * Builds a standard Android notification and always adds the local HyperOS v3 payload.
     *
     * InstallerX-Revived submits the v3 bundle directly.  Its approach is important because
     * HyperOS 3 devices often leave `notification_focus_protocol` unreadable to regular apps;
     * using that value as a gate made this app silently emit a normal notification.  ROMs that
     * do not consume the bundle ignore it and retain the standard Android notification.
     */
    fun decorate(
        context: Context,
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        subContent: String? = null,
        colorContent: String? = null,
        ticker: String? = null,
        enableFloat: Boolean = false,
        timeoutMinutes: Int = 120,
        islandTimeoutSeconds: Int = 3_600,
    ): Notification {
        val spec = IslandNotificationSpec(
            title = title,
            content = content,
            subContent = subContent,
            colorContent = colorContent,
            ticker = ticker ?: title,
            enableFloat = enableFloat,
            timeoutMinutes = timeoutMinutes,
            islandTimeoutSeconds = islandTimeoutSeconds,
        ).normalized()
        runCatching { builder.addExtras(buildV3Extras(context, spec)) }
        return builder.build()
    }

    /** Sends a self-contained sample so the Island can be checked without an account or backend. */
    fun showTest(context: Context): FocusCapabilities {
        val openApp = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, AShareApp.CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle("A股超级岛")
            .setContentText("沪深300 +1.26% · 行情监控正常")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        context.getSystemService(NotificationManager::class.java).notify(
            TEST_NOTIFICATION_ID,
            decorate(
                context = context,
                builder = builder,
                title = "A股超级岛",
                content = "+1.26%",
                subContent = "沪深300 · 行情监控正常",
                colorContent = "#E53935",
                ticker = "沪深300 +1.26%",
                enableFloat = true,
                timeoutMinutes = 10,
                islandTimeoutSeconds = 600,
            ),
        )
        return capabilities(context)
    }

    private fun buildV3Extras(context: Context, spec: IslandNotificationSpec) =
        HyperFocusNotification.buildV3 {
            val iconKey = createPicture(
                "ashare_trend",
                Icon.createWithResource(context, R.drawable.ic_stat_trend),
            )
            ticker = spec.ticker
            tickerPic = iconKey
            timeout = spec.timeoutMinutes
            updatable = true
            enableFloat = spec.enableFloat
            // Unlike the normal notification heads-up behaviour, the first Island update must
            // be marked as floating even for an ongoing monitor/progress notification.
            islandFirstFloat = true
            business = "ashare_market_monitor"

            baseInfo {
                type = 2
                title = spec.title
                content = listOfNotNull(spec.content, spec.subContent).joinToString(" · ")
            }
            picInfo {
                type = 1
                pic = iconKey
                picDark = iconKey
            }
            island {
                islandProperty = 1
                islandTimeout = spec.islandTimeoutSeconds
                highlightColor = spec.colorContent
                smallIslandArea {
                    picInfo {
                        type = 1
                        pic = iconKey
                    }
                }
                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        picInfo {
                            type = 1
                            pic = iconKey
                        }
                    }
                    imageTextInfoRight {
                        type = 3
                        textInfo {
                            title = spec.content.take(18)
                            content = spec.subContent.orEmpty().take(22).ifEmpty { " " }
                        }
                    }
                }
            }
        }
}
