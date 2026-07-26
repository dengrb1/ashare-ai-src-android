package com.ashareai.app.island

import android.app.Notification
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ashareai.app.R
import org.json.JSONObject

data class FocusCapabilities(
    val protocolVersion: Int,
    val islandSupported: Boolean,
    val focusPermission: Boolean,
) {
    val focusSupported: Boolean get() = protocolVersion > 0
    val superIslandReady: Boolean get() = protocolVersion >= 3 && islandSupported && focusPermission
}

/** Xiaomi HyperOS focus notification / Super Island compatibility layer. */
object FocusNotification {
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val ISLAND_ICON = "miui.focus.pic_imageText"

    fun capabilities(context: Context): FocusCapabilities {
        val protocol = runCatching {
            Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        }.getOrDefault(0)
        return FocusCapabilities(
            protocolVersion = protocol,
            islandSupported = systemBoolean("persist.sys.feature.island", false),
            focusPermission = protocol > 0 && hasFocusPermission(context),
        )
    }

    @android.annotation.SuppressLint("PrivateApi")
    private fun systemBoolean(key: String, defaultValue: Boolean): Boolean = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        val method = clazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.javaPrimitiveType)
        method.invoke(null, key, defaultValue) as? Boolean ?: defaultValue
    }.getOrDefault(defaultValue)

    private fun hasFocusPermission(context: Context): Boolean = runCatching {
        val extras = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"),
            "canShowFocus",
            null,
            extras,
        )?.getBoolean("canShowFocus", false) == true
    }.getOrDefault(false)

    private fun buildFocusExtras(
        context: Context,
        capabilities: FocusCapabilities,
        title: String,
        content: String,
        subContent: String?,
        colorContent: String?,
        ticker: String,
        enableFloat: Boolean,
        timeoutMinutes: Int,
        islandTimeoutSeconds: Int,
    ): Bundle {
        val baseInfo = JSONObject().apply {
            put("type", 1)
            put("title", title.take(40))
            put("content", content.take(80))
            subContent?.let { put("subContent", it.take(80)) }
            colorContent?.let {
                put("colorContent", it)
                put("colorContentDark", it)
            }
        }
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("business", "ashare_market_monitor")
            put("enableFloat", enableFloat)
            put("islandFirstFloat", enableFloat)
            put("updatable", true)
            put("filterWhenNoPermission", false)
            put("timeout", timeoutMinutes.coerceIn(1, 720))
            put("ticker", ticker.take(30))
            put("baseInfo", baseInfo)

            if (capabilities.protocolVersion >= 3 && capabilities.islandSupported) {
                val picInfo = JSONObject().apply {
                    put("type", 1)
                    put("pic", ISLAND_ICON)
                }
                val textInfo = JSONObject().apply {
                    put("frontTitle", title.take(12))
                    put("title", content.take(18))
                    put("content", subContent.orEmpty().take(22))
                    put("useHighLight", colorContent != null)
                }
                val bigArea = JSONObject().apply {
                    put("imageTextInfoLeft", JSONObject().apply {
                        put("type", 1)
                        put("picInfo", picInfo)
                        put("miui.focus.paramtextInfo", textInfo)
                    })
                    put("picInfo", picInfo)
                }
                val smallArea = JSONObject().apply { put("picInfo", picInfo) }
                put("param_island", JSONObject().apply {
                    put("islandProperty", 1)
                    put("islandTimeout", islandTimeoutSeconds.coerceIn(60, 3_600))
                    colorContent?.let { put("highlightColor", it) }
                    put("bigIslandArea", bigArea)
                    put("smallIslandArea", smallArea)
                })
            }
        }
        return Bundle().apply {
            putString(FOCUS_PARAM, JSONObject().put("param_v2", paramV2).toString())
            if (capabilities.protocolVersion >= 3 && capabilities.islandSupported) {
                putBundle(FOCUS_PICS, Bundle().apply {
                    putParcelable(ISLAND_ICON, Icon.createWithResource(context, R.drawable.ic_stat_trend))
                })
            }
        }
    }

    /** Builds a normal Android notification and decorates it only when Xiaomi granted focus permission. */
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
        val capabilities = capabilities(context)
        if (capabilities.focusSupported && capabilities.focusPermission) {
            builder.addExtras(
                buildFocusExtras(
                    context = context,
                    capabilities = capabilities,
                    title = title,
                    content = content,
                    subContent = subContent,
                    colorContent = colorContent,
                    ticker = ticker ?: title,
                    enableFloat = enableFloat,
                    timeoutMinutes = timeoutMinutes,
                    islandTimeoutSeconds = islandTimeoutSeconds,
                ),
            )
        }
        return builder.build()
    }
}
