package com.ashareai.app.standalone.data.ai

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import androidx.core.content.ContextCompat
import com.ashareai.app.standalone.domain.DailyCandle
import com.ashareai.app.standalone.domain.Holding
import com.ashareai.app.standalone.domain.MarketQuote
import com.ashareai.app.standalone.domain.ResearchResult

/**
 * AI 调度策略：根据电量和网络状态选择模型和请求频率。
 */
data class AiSchedulingPolicy(
    val useLocalInference: Boolean,
    val modelPreference: ModelPreference,
    val requestThrottleMs: Long,
    val cachePriority: CachePriority,
) {
    enum class ModelPreference {
        FASTEST,   // 最快模型（低电量或弱网）
        BALANCED,  // 平衡模型（默认）
        STRONGEST  // 最强模型（WiFi + 充足电量）
    }

    enum class CachePriority {
        HIGH,    // 高优先级缓存（弱网或低电量）
        NORMAL,  // 正常缓存
        LOW      // 低优先级缓存（WiFi + 充足电量，优先实时查询）
    }

    companion object {
        /**
         * 根据设备状态创建调度策略。
         */
        fun create(context: Context): AiSchedulingPolicy {
            val detector = LocalInferenceDetector
            val batteryLevel = detector.getBatteryLevel(context)
            val isCharging = detector.isCharging(context)
            val networkType = detector.getNetworkType(context)
            val useLocal = detector.shouldUseLocalInference(context)

            return when {
                // 低电量且未充电：省电模式
                batteryLevel < 20 && !isCharging -> AiSchedulingPolicy(
                    useLocalInference = false,
                    modelPreference = ModelPreference.FASTEST,
                    requestThrottleMs = 5000,  // 5秒节流
                    cachePriority = CachePriority.HIGH,
                )

                // 弱网络：本地推理 + 高缓存
                networkType in listOf(
                    LocalInferenceDetector.NetworkType.TWO_G,
                    LocalInferenceDetector.NetworkType.THREE_G,
                    LocalInferenceDetector.NetworkType.NONE
                ) -> AiSchedulingPolicy(
                    useLocalInference = useLocal,
                    modelPreference = ModelPreference.FASTEST,
                    requestThrottleMs = 3000,
                    cachePriority = CachePriority.HIGH,
                )

                // WiFi + 充足电量：性能模式
                networkType == LocalInferenceDetector.NetworkType.WIFI && batteryLevel > 40 -> AiSchedulingPolicy(
                    useLocalInference = false,
                    modelPreference = ModelPreference.STRONGEST,
                    requestThrottleMs = 0,
                    cachePriority = CachePriority.LOW,
                )

                // 默认：平衡模式
                else -> AiSchedulingPolicy(
                    useLocalInference = useLocal,
                    modelPreference = ModelPreference.BALANCED,
                    requestThrottleMs = 1000,
                    cachePriority = CachePriority.NORMAL,
                )
            }
        }
    }

    // 将私有方法移到 LocalInferenceDetector 的伴生对象
    private fun LocalInferenceDetector.getBatteryLevel(context: Context): Int = runCatching {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(100)

    private fun LocalInferenceDetector.isCharging(context: Context): Boolean = runCatching {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        batteryManager.isCharging
    }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    private fun LocalInferenceDetector.getNetworkType(context: Context): LocalInferenceDetector.NetworkType = runCatching {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return@runCatching LocalInferenceDetector.NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return@runCatching LocalInferenceDetector.NetworkType.NONE

        when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> LocalInferenceDetector.NetworkType.WIFI
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
                    return@runCatching LocalInferenceDetector.NetworkType.TWO_G
                }
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as android.telephony.TelephonyManager
                when (telephonyManager.dataNetworkType) {
                    android.telephony.TelephonyManager.NETWORK_TYPE_LTE,
                    android.telephony.TelephonyManager.NETWORK_TYPE_NR -> LocalInferenceDetector.NetworkType.FOUR_G_OR_FIVE_G
                    android.telephony.TelephonyManager.NETWORK_TYPE_UMTS,
                    android.telephony.TelephonyManager.NETWORK_TYPE_HSPA -> LocalInferenceDetector.NetworkType.THREE_G
                    else -> LocalInferenceDetector.NetworkType.TWO_G
                }
            }
            else -> LocalInferenceDetector.NetworkType.NONE
        }
    }.getOrDefault(LocalInferenceDetector.NetworkType.NONE)
}

object AiPayloadBuilder {
    fun researchPrompt(
        result: ResearchResult,
        candles: List<DailyCandle>,
        holding: Holding?,
        includePortfolio: Boolean,
    ): String = buildString {
        append("请只解释以下本地确定性研究，不得改写风险门槛或编造基本面、事件数据。\n")
        append("股票：")
        append(result.symbol)
        append(" ")
        append(result.name)
        append("\n本地摘要：")
        append(result.summary)
        append("\n报价：")
        append(quoteLine(result.quote))
        append("\n近")
        append(candles.takeLast(20).size)
        append("日收盘：")
        append(candles.takeLast(20).joinToString(",") { it.close.toString() })
        if (includePortfolio && holding != null) {
            append("\n已获用户单独授权的持仓信息：成本 ")
            append(holding.averageCost)
            append("，数量 ")
            append(holding.quantity)
        }
    }

    fun chatPrompt(
        userText: String,
        result: ResearchResult?,
        candles: List<DailyCandle>,
        holding: Holding?,
        includePortfolio: Boolean,
    ): String {
        if (result == null) return userText
        return researchPrompt(result, candles, holding, includePortfolio) + "\n用户问题：" + userText
    }

    private fun quoteLine(quote: MarketQuote?): String = when {
        quote == null -> "不可用"
        quote.lastPrice == null -> "不可用"
        else -> quote.lastPrice.toString() + "（" + quote.freshness.name + "）"
    }
}
