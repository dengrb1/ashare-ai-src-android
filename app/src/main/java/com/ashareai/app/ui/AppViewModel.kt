package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.NotificationCenter
import com.ashareai.app.data.model.*
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import com.ashareai.app.island.PushManager
import retrofit2.HttpException

/**
 * 会话级全局状态：登录态、资产、行情报价轮询、通知红点。
 * 行情按独立版的前台刷新偏好轮询，仅在应用前台运行。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AShareApp
    val settings get() = app.settings
    private val api get() = ApiClient.api

    // ---- 登录态 ----
    sealed class AuthState {
        data object Loading : AuthState()
        data object LoggedOut : AuthState()
        data class ConnectionFailed(val message: String) : AuthState()
        data class LoggedIn(val user: UserResponse) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _assets = MutableStateFlow<AssetState?>(null)
    val assets: StateFlow<AssetState?> = _assets.asStateFlow()

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val _foregroundRefreshIntervalSeconds =
        MutableStateFlow(MarketRefreshIntervals.DEFAULT_SECONDS)
    val foregroundRefreshIntervalSeconds: StateFlow<Int> = _foregroundRefreshIntervalSeconds.asStateFlow()

    private val _marketSession = MutableStateFlow<MarketSession?>(null)
    val marketSession: StateFlow<MarketSession?> = _marketSession.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    val notificationCenter = NotificationCenter(api, viewModelScope) { _unreadCount.value = it }

    private val _globalError = MutableStateFlow<String?>(null)
    val globalError: StateFlow<String?> = _globalError.asStateFlow()

    private var pollJob: Job? = null
    private var pushEventsJob: Job? = null
    private var foreground = false

    init {
        ApiClient.onSessionExpired = {
            viewModelScope.launch {
                pollJob?.cancel()
                disableOptionalPush()
                settings.clearTokens()
                _authState.value = AuthState.LoggedOut
            }
        }
        restoreSession()
        viewModelScope.launch {
            settings.foregroundMarketRefreshIntervalSeconds.collect { seconds ->
                _foregroundRefreshIntervalSeconds.value = MarketRefreshIntervals.normalize(seconds)
            }
        }
    }

    private fun restoreSession() {
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val token = settings.currentAccessToken()
            if (token.isNullOrBlank()) {
                _authState.value = AuthState.LoggedOut
                return@launch
            }
            try {
                val bootstrap = withTimeout(20_000) { api.bootstrap() }
                val user = bootstrap.user ?: api.me()
                _assets.value = bootstrap.assets ?: api.assets()
                _authState.value = AuthState.LoggedIn(user)
                initializeOptionalPush()
                restartPolling()
            } catch (e: Exception) {
                if (e is HttpException && e.code() in setOf(401, 403)) {
                    settings.clearTokens()
                    _authState.value = AuthState.LoggedOut
                } else {
                    _authState.value = AuthState.ConnectionFailed("无法连接服务器，请检查网络或服务器地址后重试。")
                }
            }
        }
    }

    fun retrySessionRestore() = restoreSession()

    fun showLogin() {
        pollJob?.cancel()
        _authState.value = AuthState.LoggedOut
    }

    fun login(username: String, password: String, rememberPassword: Boolean, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val tokens = api.token(LoginRequest(username, password))
                settings.saveTokens(
                    tokens.access_token,
                    tokens.refresh_token,
                    tokens.expires_in,
                    username,
                    password,
                    rememberPassword,
                )
                val bootstrap = api.bootstrap()
                val user = bootstrap.user ?: api.me()
                _assets.value = bootstrap.assets ?: api.assets()
                _authState.value = AuthState.LoggedIn(user)
                initializeOptionalPush()
                restartPolling()
            } catch (e: Exception) {
                onError(e.toUserMessage())
            }
        }
    }

    fun logout() {
        pollJob?.cancel()
        viewModelScope.launch {
            disableOptionalPush()
            try {
                settings.currentRefreshToken()?.let { api.revoke(RefreshRequest(it)) }
            } catch (_: Exception) {
            }
            settings.clearTokens()
            _assets.value = null
            _quotes.value = emptyMap()
            _authState.value = AuthState.LoggedOut
        }
    }

    fun loadAssets() {
        viewModelScope.launch {
            try {
                _assets.value = api.assets()
                restartPolling()
            } catch (e: Exception) {
                _globalError.value = e.toUserMessage()
            }
        }
    }

    // ---- 行情轮询 ----

    fun onForeground() {
        foreground = true
        restartPolling()
    }

    fun onBackground() {
        foreground = false
        pollJob?.cancel()
    }

    private fun restartPolling() {
        pollJob?.cancel()
        if (!foreground || _authState.value !is AuthState.LoggedIn) return
        pollJob = viewModelScope.launch {
            var sessionTick = 0
            while (foreground && _authState.value is AuthState.LoggedIn) {
                coroutineScope {
                    val quoteJob = async { refreshQuotes() }
                    val auxiliaryJobs = if (sessionTick % 4 == 0) {
                        listOf(
                            async { refreshMarketStatus() },
                            async { refreshNotificationSummary() },
                        )
                    } else {
                        emptyList()
                    }
                    quoteJob.await()
                    auxiliaryJobs.forEach { it.await() }
                }
                sessionTick++
                delay(_foregroundRefreshIntervalSeconds.value * 1000L)
            }
        }
    }

    suspend fun refreshQuotes() {
        val asset = _assets.value ?: return
        val symbols = (asset.watchlist + asset.positions.map { it.symbol }).distinct()
        if (symbols.isEmpty()) return
        try {
            val list = api.quotes(symbols.joinToString(","))
            _quotes.value = _quotes.value + list.associateBy { it.symbol }
        } catch (_: Exception) {
            // 轮询失败静默，下轮重试
        }
    }

    private suspend fun refreshMarketStatus() {
        try {
            _marketSession.value = api.marketStatus().market_session
        } catch (_: Exception) {
        }
    }

    private suspend fun refreshNotificationSummary() {
        try {
            _unreadCount.value = api.notificationSummary().unread_count
        } catch (_: Exception) {
        }
    }

    fun forceRefresh() {
        viewModelScope.launch {
            coroutineScope {
                listOf(
                    async { refreshQuotes() },
                    async { refreshMarketStatus() },
                    async { refreshNotificationSummary() },
                ).forEach { it.await() }
            }
        }
    }

    fun clearGlobalError() {
        _globalError.value = null
    }

    /**
     * 推送 SDK 可能唤起厂商进程，只有登录且用户明确开启行情/研究通知后才初始化。
     * 未开启时独立版仅保留前台页面的轻量轮询。
     */
    fun enableOptionalPush() {
        viewModelScope.launch {
            if (!settings.islandEnabled.first()) return@launch
            PushManager.bindAuthenticatedDevice(app)
            if (pushEventsJob == null) {
                pushEventsJob = viewModelScope.launch {
                    PushManager.events.collect { notificationCenter.refresh() }
                }
            }
        }
    }

    private suspend fun disableOptionalPush() {
        pushEventsJob?.cancel()
        pushEventsJob = null
        PushManager.unbindAuthenticatedDevice(app)
    }

    fun disableOptionalPushForSettings() {
        viewModelScope.launch { disableOptionalPush() }
    }

    private fun initializeOptionalPush() = enableOptionalPush()

    // ---- 资产写操作 ----

    fun saveAssets(request: AssetStateRequest, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                _assets.value = api.saveAssets(request)
                restartPolling()
                onDone(null)
            } catch (e: Exception) {
                onDone(e.toUserMessage())
            }
        }
    }

    fun saveExitMonitor(request: ExitMonitorRequest, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                _assets.value = api.saveExitMonitor(newIdempotencyKey(), request)
                onDone(null)
            } catch (e: Exception) {
                onDone(e.toUserMessage())
            }
        }
    }

    fun saveForegroundRefreshInterval(seconds: Int, onDone: (String?) -> Unit) {
        if (seconds !in MarketRefreshIntervals.OPTIONS) {
            onDone("不支持的自动刷新间隔")
            return
        }
        viewModelScope.launch {
            try {
                settings.setForegroundMarketRefreshIntervalSeconds(seconds)
                _foregroundRefreshIntervalSeconds.value = seconds
                restartPolling()
                onDone(null)
            } catch (e: Exception) {
                onDone(e.toUserMessage())
            }
        }
    }
}
