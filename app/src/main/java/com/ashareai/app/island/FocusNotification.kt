package com.ashareai.app.island

import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * 小米 HyperOS 超级岛（焦点通知）辅助。
 *
 * 机制：在通知 extras 里附加 `miui.focus.param` JSON（param_v2 协议），
 * HyperOS 2 的系统 UI 会将其渲染到灵动岛区域；非 MIUI/HyperOS 设备
 * 忽略这些 extras，自动降级为普通通知，无需分支处理。
 */
object FocusNotification {

    fun isMiui(): Boolean {
        return try {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java)
            val miui = get.invoke(null, "ro.miui.ui.version.name") as? String
            miui.isNullOrBlank().not()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 构造焦点通知 extras。
     *
     * @param title 岛上主标题（如 "浮动盈亏"）
     * @param content 主内容（如 "+1,234.56"）
     * @param subContent 副内容（如 "+2.31%"）
     * @param colorContent 内容颜色（#RRGGBB），涨红跌绿
     * @param ticker 状态栏胶囊简文
     * @param enableFloat 是否弹出浮窗（预警场景 true，常驻监控 false）
     * @param timeoutSeconds 焦点展示超时秒数
     */
    fun buildFocusExtras(
        title: String,
        content: String,
        subContent: String? = null,
        colorContent: String? = null,
        ticker: String? = null,
        enableFloat: Boolean = false,
        timeoutSeconds: Int = 300,
    ): Bundle {
        val baseInfo = JSONObject().apply {
            put("type", 1)
            put("title", title)
            put("content", content)
            if (subContent != null) put("subContent", subContent)
            if (colorContent != null) {
                put("colorContent", colorContent)
                put("colorContentDark", colorContent)
            }
        }
        val paramV2 = JSONObject().apply {
            put("protocol", 1)
            put("enableFloat", enableFloat)
            put("isCcbAutoFocus", true)
            put("updatable", true)
            put("timeout", timeoutSeconds)
            if (ticker != null) {
                put("ticker", ticker)
                put("tickerDark", ticker)
            }
            put("baseInfo", baseInfo)
        }
        val focusParam = JSONObject().apply {
            put("param_v2", paramV2)
        }
        return Bundle().apply {
            putString("miui.focus.param", focusParam.toString())
        }
    }

    /** 把焦点 extras 挂到 NotificationCompat.Builder 上。 */
    fun attach(builder: NotificationCompat.Builder, extras: Bundle): NotificationCompat.Builder {
        builder.addExtras(extras)
        return builder
    }

    /** 兼容检查：焦点通知只在 MIUI/HyperOS 上生效，其余平台此调用是无害 no-op。 */
    fun decorate(
        builder: NotificationCompat.Builder,
        title: String,
        content: String,
        subContent: String? = null,
        colorContent: String? = null,
        ticker: String? = null,
        enableFloat: Boolean = false,
        timeoutSeconds: Int = 300,
    ): Notification {
        if (isMiui()) {
            attach(
                builder,
                buildFocusExtras(title, content, subContent, colorContent, ticker, enableFloat, timeoutSeconds),
            )
        }
        return builder.build()
    }
}
