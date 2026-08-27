package com.ashareai.app.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.URI

private const val DEFAULT_API_PORT = 8000

/**
 * Normalizes a user-entered server address into a Retrofit-compatible base URL.
 */
fun normalizeServerUrl(input: String): Result<String> = runCatching {
    val raw = input.trim()
    require(raw.isNotEmpty()) { "请输入服务器地址" }

    val suppliedScheme = raw.substringBefore("://", missingDelimiterValue = "").lowercase()
    val candidate = if (suppliedScheme.isEmpty()) "https://$raw" else raw
    val candidateUrl = candidate.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("服务器地址格式不正确")
    val inferredScheme = if (suppliedScheme.isEmpty() && candidateUrl.host.isPrivateIpv4()) "http" else "https"
    val withScheme = if (suppliedScheme.isEmpty()) "$inferredScheme://$raw" else raw
    val parsed = withScheme.toHttpUrlOrNull()
        ?: throw IllegalArgumentException("服务器地址格式不正确")
    require(parsed.scheme == "http" || parsed.scheme == "https") {
        "服务器地址仅支持 http 或 https"
    }
    require(parsed.username.isEmpty() && parsed.password.isEmpty()) {
        "服务器地址不能包含用户名或密码"
    }
    require(parsed.query == null && parsed.fragment == null) {
        "服务器地址不能包含查询参数或片段"
    }
    require(!parsed.host.isLoopbackHost()) {
        "手机不能使用 localhost 或 127.0.0.1 访问电脑，请填写电脑的局域网 IP 地址"
    }
    require(!parsed.host.isWildcardHost()) {
        "0.0.0.0 仅用于服务器监听，请填写电脑的局域网 IP 地址"
    }
    require(parsed.port !in RESERVED_INTERNAL_PORTS) {
        "请填写 FastAPI 地址，不能直连内部桥接或模型服务端口"
    }
    require(parsed.scheme != "http" || parsed.host.isPrivateIpv4()) {
        "公网服务器必须使用 HTTPS"
    }

    val uri = URI(withScheme)
    val normalized = if (uri.port == -1 && parsed.scheme == "http" && parsed.host.isPrivateIpv4()) {
        parsed.newBuilder().port(DEFAULT_API_PORT).build()
    } else {
        parsed
    }
    normalized.toString().trimEnd('/')
}

internal fun String.isLegacyLoopbackAddress(): Boolean = runCatching {
    val raw = trim()
    val candidate = if (raw.contains("://")) raw else "http://$raw"
    val parsed = candidate.toHttpUrlOrNull() ?: return@runCatching false
    parsed.host.isLoopbackHost() || parsed.host.isWildcardHost()
}.getOrDefault(false)

internal fun String.isLoopbackHost(): Boolean {
    val normalized = trimEnd('.').lowercase()
    if (normalized == "localhost" || normalized == "::1" ||
        normalized == "0:0:0:0:0:0:0:1") return true
    if (normalized.startsWith("::ffff:")) {
        return normalized.removePrefix("::ffff:").isLoopbackHost()
    }
    val parts = normalized.split('.')
    if (parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 }) {
        return parts[0].toInt() == 127
    }
    return normalized.literalInetAddress()?.isLoopbackAddress == true
}

internal fun String.isWildcardHost(): Boolean {
    val normalized = trimEnd('.').lowercase()
    return normalized == "0.0.0.0" || normalized == "::" ||
        normalized == "0:0:0:0:0:0:0:0" ||
        normalized.literalInetAddress()?.isAnyLocalAddress == true
}

internal fun String.isPrivateIpv4(): Boolean {
    val parts = trimEnd('.').split('.')
    if (parts.size != 4 || parts.any { it.toIntOrNull() !in 0..255 }) return false
    val octets = parts.map(String::toInt)
    return octets[0] == 10 ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 192 && octets[1] == 168)
}

/** Parse only literal-looking hosts so URL normalization never performs DNS. */
private fun String.literalInetAddress(): InetAddress? {
    val normalized = trimEnd('.').lowercase()
    val ipv4Like = normalized.isNotEmpty() && normalized.all { it.isDigit() || it == '.' }
    val ipv6Like = normalized.contains(':') && normalized.all {
        it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' || it == '%'
    }
    if (!ipv4Like && !ipv6Like) return null
    return runCatching { InetAddress.getByName(normalized) }.getOrNull()
}

private val RESERVED_INTERNAL_PORTS = setOf(8787, 8081, 8082)
