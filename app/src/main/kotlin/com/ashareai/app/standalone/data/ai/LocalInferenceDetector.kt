package com.ashareai.app.standalone.data.ai

import android.content.Context
import android.os.Build

/**
 * HyperOS AI 引擎检测器：检测小米端侧 AI 推理能力。
 *
 * HyperOS 提供了 MiAI 引擎，支持端侧推理能力，包括：
 * - 小型语言模型（如 Llama 2、Gemini Nano）
 * - 文本理解和生成
 * - 图像识别和分析
 *
 * 优先使用端侧推理可降低延迟、节省流量并保护隐私。
 */
object LocalInferenceDetector {
    /**
     * 检测 HyperOS AI 引擎可用性。
     *
     * 检测逻辑：
     * 1. 检查系统属性 ro.miui.ai.engine.version
     * 2. 检查是否存在 MiAI SDK 特征
     * 3. 检查 Android 版本（Android 14+ 支持 AICore）
     */
    fun isLocalInferenceAvailable(context: Context): Boolean {
        // 检查 HyperOS AI 引擎
        val hasMiAIEngine = checkMiAIEngine()

        // 检查 Android AICore（Android 14+ Pixel 功能，部分 OEM 移植）
        val hasAICore = Build.VERSION.SDK_INT >= 34 && checkAICore(context)

        // 检查设备性能：需要至少 6GB RAM 才能流畅运行端侧模型
        val hasEnoughMemory = checkMemory(context)

        return (hasMiAIEngine || hasAICore) && hasEnoughMemory
    }

    /**
     * 获取本地推理能力描述。
     */
    fun getCapabilityDescription(context: Context): String {
        if (!isLocalInferenceAvailable(context)) {
            return "端侧推理不可用"
        }

        val capabilities = mutableListOf<String>()

        if (checkMiAIEngine()) {
            capabilities.add("MiAI 引擎")
        }

        if (Build.VERSION.SDK_INT >= 34 && checkAICore(context)) {
            capabilities.add("Android AICore")
        }

        val memoryGB = getTotalMemoryGB(context)
        capabilities.add("${memoryGB}GB RAM")

        return "端侧推理可用 (${capabilities.joinToString(", ")})"
    }

    /**
     * 检测 HyperOS MiAI 引擎。
     */
    private fun checkMiAIEngine(): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getDeclaredMethod("get", String::class.java, String::class.java)

        // 检查 MiAI 引擎版本
        val aiVersion = get.invoke(null, "ro.miui.ai.engine.version", "") as? String ?: ""
        if (aiVersion.isNotBlank()) return@runCatching true

        // 检查 HyperOS 版本（HyperOS 1.0.2+ 支持 MiAI）
        val hyperVersion = get.invoke(null, "ro.mi.os.version.incremental", "") as? String ?: ""
        val versionParts = hyperVersion.replace("OS", "").split(".")
        val majorVersion = versionParts.getOrNull(0)?.toIntOrNull() ?: 0
        val minorVersion = versionParts.getOrNull(1)?.toIntOrNull() ?: 0

        // HyperOS 1.0.2+ 或 2.0+
        (majorVersion == 1 && minorVersion >= 2) || majorVersion >= 2
    }.getOrDefault(false)

    /**
     * 检测 Android AICore（Google Pixel 特性，部分 OEM 移植）。
     */
    private fun checkAICore(context: Context): Boolean = runCatching {
        // AICore 特征检测：检查是否存在 com.google.android.aicore
        val pm = context.packageManager
        pm.getPackageInfo("com.google.android.aicore", 0)
        true
    }.getOrElse { false }

    /**
     * 检查设备内存：需要至少 6GB RAM 才能流畅运行端侧模型。
     */
    private fun checkMemory(context: Context): Boolean {
        val totalMemoryGB = getTotalMemoryGB(context)
        return totalMemoryGB >= 6
    }

    /**
     * 获取设备总内存（GB）。
     */
    private fun getTotalMemoryGB(context: Context): Int = runCatching {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        (memoryInfo.totalMem / (1024 * 1024 * 1024)).toInt()
    }.getOrDefault(4)

    /**
     * 检查是否应该使用本地推理（考虑电量和网络状态）。
     *
     * 策略：
     * - 低电量（<20%）：仅在充电时使用本地推理
     * - 弱网络（2G/3G）：优先使用本地推理
     * - WiFi 且电量充足：优先使用云端推理（更强大的模型）
     */
    fun shouldUseLocalInference(context: Context): Boolean {
        if (!isLocalInferenceAvailable(context)) {
            return false
        }

        val batteryLevel = getBatteryLevel(context)
        val isCharging = isCharging(context)
        val networkType = getNetworkType(context)

        return when {
            // 低电量且未充电：禁用本地推理（省电优先）
            batteryLevel < 20 && !isCharging -> false

            // 弱网络：优先本地推理
            networkType in listOf(NetworkType.TWO_G, NetworkType.THREE_G, NetworkType.NONE) -> true

            // WiFi 且电量充足：优先云端推理（更强大）
            networkType == NetworkType.WIFI && batteryLevel > 40 -> false

            // 默认：使用本地推理
            else -> true
        }
    }

    internal fun getBatteryLevel(context: Context): Int = runCatching {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrDefault(100)

    internal fun isCharging(context: Context): Boolean = runCatching {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
        batteryManager.isCharging
    }.getOrDefault(false)

    internal fun getNetworkType(context: Context): NetworkType = runCatching {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return@runCatching NetworkType.NONE
        val capabilities = connectivityManager.getNetworkCapabilities(network)
            ?: return@runCatching NetworkType.NONE

        when {
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE)
                    as android.telephony.TelephonyManager
                when (telephonyManager.dataNetworkType) {
                    android.telephony.TelephonyManager.NETWORK_TYPE_LTE,
                    android.telephony.TelephonyManager.NETWORK_TYPE_NR -> NetworkType.FOUR_G_OR_FIVE_G
                    android.telephony.TelephonyManager.NETWORK_TYPE_UMTS,
                    android.telephony.TelephonyManager.NETWORK_TYPE_HSPA -> NetworkType.THREE_G
                    else -> NetworkType.TWO_G
                }
            }
            else -> NetworkType.NONE
        }
    }.getOrDefault(NetworkType.NONE)

    enum class NetworkType {
        WIFI,
        FOUR_G_OR_FIVE_G,
        THREE_G,
        TWO_G,
        NONE
    }
}
