package com.ashareai.app.data

import com.ashareai.app.data.model.RefreshRequest
import com.ashareai.app.data.model.TokenResponse
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 单例网络栈。baseUrl 可在设置页修改，修改后调用 [rebuild] 重建。
 * Token 刷新采用同步互斥：多个并发 401 只触发一次 refresh。
 */
object ApiClient {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Volatile
    private var settings: SettingsStore? = null

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var httpClient: OkHttpClient? = null

    @Volatile
    var onSessionExpired: (() -> Unit)? = null

    fun init(store: SettingsStore) {
        settings = store
    }

    fun rebuild() {
        synchronized(this) {
            retrofit = null
            httpClient = null
        }
    }

    val api: ApiService
        get() = getRetrofit().create(ApiService::class.java)

    fun okHttp(): OkHttpClient = getOrCreateClient()

    fun currentBaseUrl(): String {
        val store = settings ?: return SettingsStore.DEFAULT_BASE_URL
        return runBlocking { store.currentBaseUrl() }
    }

    private fun getRetrofit(): Retrofit {
        retrofit?.let { return it }
        synchronized(this) {
            retrofit?.let { return it }
            val base = currentBaseUrl().trimEnd('/') + "/"
            val built = Retrofit.Builder()
                .baseUrl(base)
                .client(getOrCreateClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
            retrofit = built
            return built
        }
    }

    private fun getOrCreateClient(): OkHttpClient {
        httpClient?.let { return it }
        synchronized(this) {
            httpClient?.let { return it }
            val store = requireNotNull(settings) { "ApiClient.init 未调用" }
            val built = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .addInterceptor(AuthInterceptor(store))
                .authenticator(TokenAuthenticator(store))
                .build()
            httpClient = built
            return built
        }
    }

    private class AuthInterceptor(private val store: SettingsStore) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.header("Authorization") != null || isAuthPath(request)) {
                return chain.proceed(request)
            }
            val token = runBlocking { store.currentAccessToken() }
            return if (token.isNullOrBlank()) {
                chain.proceed(request)
            } else {
                chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
            }
        }
    }

    private class TokenAuthenticator(private val store: SettingsStore) : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            if (isAuthPath(response.request)) return null
            if (responseCount(response) >= 2) {
                onSessionExpired?.invoke()
                return null
            }
            val newToken = synchronized(this@ApiClient) {
                runBlocking {
                    val current = store.currentAccessToken()
                    val failed = response.request.header("Authorization")?.removePrefix("Bearer ")
                    // 另一个请求可能已经刷新过了
                    if (!current.isNullOrBlank() && current != failed) return@runBlocking current
                    refreshBlocking(store)
                }
            } ?: run {
                onSessionExpired?.invoke()
                return null
            }
            return response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }

        private suspend fun refreshBlocking(store: SettingsStore): String? {
            val refreshToken = store.currentRefreshToken() ?: return null
            return try {
                val base = currentBaseUrl().trimEnd('/')
                val bodyJson = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
                val request = Request.Builder()
                    .url("$base/api/v1/auth/refresh")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
                // 独立裸客户端，避免拦截器递归
                val plain = OkHttpClient.Builder()
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()
                plain.newCall(request).execute().use { resp ->
                    if (!resp.isSuccessful) {
                        store.clearTokens()
                        return null
                    }
                    val tokens = json.decodeFromString(TokenResponse.serializer(), resp.body!!.string())
                    store.saveTokens(tokens.access_token, tokens.refresh_token, tokens.expires_in)
                    tokens.access_token
                }
            } catch (_: Exception) {
                null
            }
        }

        private fun responseCount(response: Response): Int {
            var count = 1
            var prior = response.priorResponse
            while (prior != null) {
                count++
                prior = prior.priorResponse
            }
            return count
        }
    }

    private fun isAuthPath(request: Request): Boolean {
        val path = request.url.encodedPath
        return path.endsWith("/auth/token") || path.endsWith("/auth/refresh") || path.endsWith("/auth/revoke")
    }
}
