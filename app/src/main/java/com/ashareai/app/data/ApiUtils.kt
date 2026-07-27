package com.ashareai.app.data

import com.ashareai.app.data.model.ApiErrorBody
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.io.IOException
import javax.net.ssl.SSLHandshakeException
import java.util.UUID

/** 统一把异常翻成用户可读的中文消息。 */
fun Throwable.toUserMessage(): String = when (this) {
    is HttpException -> {
        val detail = try {
            response()?.errorBody()?.string()?.let { raw ->
                val body = ApiClient.json.decodeFromString(ApiErrorBody.serializer(), raw)
                when (val d = body.detail) {
                    is JsonPrimitive -> d.content
                    is JsonArray -> d.joinToString("；") { el ->
                        (el as? JsonObject)?.get("msg")?.let { (it as? JsonPrimitive)?.content } ?: el.toString()
                    }
                    is JsonObject -> d.toString()
                    else -> null
                }
            }
        } catch (_: Exception) {
            null
        }
        val requestPath = response()?.raw()?.request?.url?.encodedPath
        when (code()) {
            401 -> if (requestPath?.endsWith("/auth/token") == true) {
                detail ?: "用户名或密码错误"
            } else {
                "登录已过期，请重新登录"
            }
            403 -> detail ?: "无权限执行此操作"
            404 -> detail ?: "数据不存在"
            409 -> detail ?: "操作冲突，请刷新后重试"
            422 -> detail ?: "请求参数不合法"
            429 -> "请求过于频繁，请稍后再试"
            else -> detail ?: "服务器错误 (${code()})"
        }
    }
    is SocketTimeoutException -> "连接服务器超时，请确认手机与服务器在同一局域网"
    is ConnectException -> "服务器拒绝连接，请确认端口正确，且服务已监听 0.0.0.0"
    is UnknownHostException -> "找不到服务器，请检查服务器地址是否输入正确"
    is SSLHandshakeException -> "HTTPS 证书校验失败，请检查服务器证书"
    is IOException -> "网络连接失败，请检查手机与服务器是否在同一局域网"
    else -> message ?: "未知错误"
}

fun newIdempotencyKey(): String = UUID.randomUUID().toString()

/** 证券代码规范化：600000 -> 600000.SH；000001 -> 000001.SZ；8/4开头 -> BJ。 */
fun normalizeSymbol(input: String): String? {
    val raw = input.trim().uppercase()
    if (Regex("^\\d{6}\\.(SH|SZ|BJ)$").matches(raw)) return raw
    if (Regex("^\\d{6}$").matches(raw)) {
        val suffix = when (raw.first()) {
            '6', '5', '9' -> "SH"
            '0', '1', '2', '3' -> "SZ"
            '4', '8' -> "BJ"
            else -> return null
        }
        return "$raw.$suffix"
    }
    return null
}
