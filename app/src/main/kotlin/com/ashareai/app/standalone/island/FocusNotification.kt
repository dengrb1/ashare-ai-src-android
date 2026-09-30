package com.ashareai.app.standalone.island

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ashareai.app.MainActivity
import com.ashareai.app.R
import com.ashareai.app.HybridApp
import com.xzakota.hyper.notification.focus.FocusNotification as HyperFocusNotification

data class FocusCapabilities(
    val protocolVersion: Int,
    val islandSupported: Boolean,
    val focusPermissionGranted: Boolean,
    val appIdConfigured: Boolean,
    val hyperOSVersion: HyperOSVersion,
) {
    val focusSupported: Boolean get() = protocolVersion > 0
    val superIslandReady: Boolean get() = islandSupported && focusPermissionGranted && appIdConfigured
    val v3PayloadAttached: Boolean
        get() = true
}

/**
 * HyperOS 版本检测：OS3 (HyperOS 1.x) 和 OS4 (HyperOS 2.x)。
 */
enum class HyperOSVersion {
    OS3,  // HyperOS 1.x
    OS4,  // HyperOS 2.x
    UNKNOWN
}

data class IslandNotificationSpec(
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
        title = title.take(12),
        content = content.take(16),
        subContent = subContent?.take(20)?.takeIf(String::isNotBlank),
        ticker = ticker.take(20),
        timeoutMinutes = timeoutMinutes.coerceIn(1, 720),
        islandTimeoutSeconds = islandTimeoutSeconds.coerceIn(60, 3_600),
    )
}

/**
 * Normal-notification first compatibility layer for the official HyperOS focus protocol v3.
 * ROMs that do not consume the v3 bundle continue to receive a standard notification.
 *
 * HyperOS 4 适配说明:
 * - 协议版本检测支持 v3 和 v4
 * - 使用 island.islandProperty = 1 标识为超级岛通知
 * - 仅 WARNING 优先级的通知会启用超级岛 (enableFloat = true)
 * - 其他通知类型 (NORMAL, PROGRESS) 不显示超级岛，避免打扰用户
 * - island.islandTimeout 控制超级岛停留时长
 */
object FocusNotification {
    private const val TEST_NOTIFICATION_ID = 1903

    fun capabilities(context: Context): FocusCapabilities {
        val protocol = runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)
        return FocusCapabilities(
            protocolVersion = protocol,
            islandSupported = protocol >= 3 || islandSystemProperty() || isHyperOS4(),
            focusPermissionGranted = hasFocusPermission(context),
            appIdConfigured = hasConfiguredAppId(context),
            hyperOSVersion = detectHyperOSVersion(),
        )
    }

    /**
     * 检测 HyperOS 版本：通过系统属性判断 OS3 (1.x) 或 OS4 (2.x)。
     */
    private fun detectHyperOSVersion(): HyperOSVersion = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getDeclaredMethod("get", String::class.java, String::class.java)
        val version = get.invoke(null, "ro.mi.os.version.incremental", "") as? String ?: ""

        when {
            version.startsWith("OS1.") -> HyperOSVersion.OS3
            version.startsWith("OS2.") -> HyperOSVersion.OS4
            else -> {
                // 备选方案：通过 Android 版本推断
                val androidVersion = android.os.Build.VERSION.SDK_INT
                when {
                    androidVersion <= 34 -> HyperOSVersion.OS3  // Android 14 及以下通常为 OS3
                    androidVersion >= 35 -> HyperOSVersion.OS4  // Android 15+ 通常为 OS4
                    else -> HyperOSVersion.UNKNOWN
                }
            }
        }
    }.getOrDefault(HyperOSVersion.UNKNOWN)

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

    fun showTest(context: Context): FocusCapabilities {
        val openApp = PendingIntent.getActivity(
            context,
            TEST_NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, HybridApp.CHANNEL_LOCAL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_trend)
            .setContentTitle("霁衡智研")
            .setContentText("测试通知")
            .setContentIntent(openApp)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        context.getSystemService(NotificationManager::class.java).notify(
            TEST_NOTIFICATION_ID,
            decorate(
                context = context,
                builder = builder,
                title = "霁衡智研",
                content = "测试通知",
                subContent = "超级岛载荷已发送",
                colorContent = "#E53935",
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
            islandFirstFloat = spec.enableFloat
            business = "ashare_standalone_alert"
            baseInfo {
                type = 2
                title = spec.title
                content = spec.content
            }
            picInfo {
                type = 1
                pic = iconKey
                picDark = iconKey
            }
            island {
                islandProperty = if (spec.enableFloat) 1 else 0
                islandTimeout = spec.islandTimeoutSeconds
                highlightColor = spec.colorContent?.let { softenColor(it) } ?: "#FF6B6B"
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
                            title = spec.content
                            content = spec.subContent.orEmpty().ifEmpty { " " }
                        }
                    }
                }
            }
        }

    /**
     * 柔和化颜色：红色和绿色使用更柔和的色调，提升视觉舒适度。
     */
    private fun softenColor(color: String): String = when (color) {
        "#E53935" -> "#FF6B6B"  // 红涨：更柔和的红色
        "#00A86B" -> "#51CF66"  // 绿跌：更柔和的绿色
        else -> color
    }

    private fun hasFocusPermission(context: Context): Boolean = runCatching {
        val extras = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"),
            "canShowFocus",
            null,
            extras,
        )?.getBoolean("canShowFocus", false) == true
    }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun hasConfiguredAppId(context: Context): Boolean = runCatching {
        context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.xiaomi.xms.APP_ID")
            ?.isNotBlank() == true
    }.getOrDefault(false)

    private fun islandSystemProperty(): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val getBoolean = systemProperties.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        getBoolean.invoke(null, "persist.sys.feature.island", false) as? Boolean ?: false
    }.getOrDefault(false)

    /**
     * 检测 HyperOS 4 系统
     * HyperOS 4 基于 Android 15，且系统版本号通常为 2.x 或更高
     */
    private fun isHyperOS4(): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getDeclaredMethod("get", String::class.java)

        // 检查是否为 HyperOS
        val osName = get.invoke(null, "ro.miui.ui.version.name") as? String ?: ""
        val isHyperOS = osName.startsWith("HYPER", ignoreCase = true)

        if (!isHyperOS) return@runCatching false

        // 检查 HyperOS 版本号 (ro.mi.os.version.incremental)
        val hyperVersion = get.invoke(null, "ro.mi.os.version.incremental") as? String ?: ""
        val versionNumber = hyperVersion.split(".").firstOrNull()?.toIntOrNull() ?: 0

        // HyperOS 4.x 及以上支持增强超级岛
        versionNumber >= 4 || android.os.Build.VERSION.SDK_INT >= 35
    }.getOrDefault(false)
}
