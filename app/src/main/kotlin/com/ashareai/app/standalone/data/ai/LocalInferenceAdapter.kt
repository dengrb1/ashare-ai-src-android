package com.ashareai.app.standalone.data.ai

import android.content.Context

/** Platform adapter boundary. Implementations must be able to execute before returning true. */
interface LocalInferenceAdapter {
    val id: String
    fun isPlatformAvailable(context: Context): Boolean
    fun isInvocable(context: Context): Boolean
}

class AICoreLocalInferenceAdapter : LocalInferenceAdapter {
    override val id: String = "Android AICore"

    override fun isPlatformAvailable(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo("com.google.android.aicore", 0)
        true
    }.getOrDefault(false)

    // The app does not bundle or reflect an OEM-specific AICore invocation API yet.
    override fun isInvocable(context: Context): Boolean = false
}

class MiAILocalInferenceAdapter : LocalInferenceAdapter {
    override val id: String = "MiAI"

    override fun isPlatformAvailable(context: Context): Boolean = runCatching {
        val systemProperties = Class.forName("android.os.SystemProperties")
        val get = systemProperties.getDeclaredMethod("get", String::class.java, String::class.java)
        val aiVersion = get.invoke(null, "ro.miui.ai.engine.version", "") as? String ?: ""
        val hyperVersion = get.invoke(null, "ro.mi.os.version.incremental", "") as? String ?: ""
        aiVersion.isNotBlank() || hyperVersion.isNotBlank()
    }.getOrDefault(false)

    // MiAI SDK is not a compile-time dependency; keep this adapter unavailable until
    // a concrete SDK implementation is supplied by the device integration.
    override fun isInvocable(context: Context): Boolean = false
}

class UnavailableLocalInferenceAdapter : LocalInferenceAdapter {
    override val id: String = "不可用"
    override fun isPlatformAvailable(context: Context): Boolean = false
    override fun isInvocable(context: Context): Boolean = false
}
