package com.ashareai.app.data

import com.ashareai.app.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

private const val DEFAULT_API_PORT = 8000

/**
 * Normalizes a user-entered server address into a Retrofit-compatible base URL.
 */
fun normalizeServerUrl(input: String): Result<String> = runCatching {
    val raw = input.trim()
    require(raw.isNotEmpty()) { "请输入服务器地址" }

    val withScheme = if (raw.contains("://")) raw else "http://$raw"
    val parsed = withScheme.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("服务器地址格式不正确")
    require(parsed.scheme == "http" || parsed.scheme == "https") {
        "服务器地址仅支持 http 或 https"
    }
    require(BuildConfig.DEBUG || parsed.scheme == "https") {
        "正式版本仅允许 HTTPS 服务器"
    }
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
        "服务器地址不能包含用户名或密码"
    }
    require(parsed.query == null && parsed.fragment == null) {
        "服务器地址不能包含查询参数或片段"
    }

    val uri = URI(withScheme)
    val normalized = if (uri.port == -1 && parsed.scheme == "http" && parsed.host.isPrivateIpv4()) {
        parsed.newBuilder().port(DEFAULT_API_PORT).build()
    } else {
        parsed
    }
    normalized.toString().trimEnd('/')
}

private fun String.isPrivateIpv4(): Boolean {
    val parts = split('.').mapNotNull(String::toIntOrNull)
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return parts[0] == 10 ||
        (parts[0] == 172 && parts[1] in 16..31) ||
        (parts[0] == 192 && parts[1] == 168)
}
