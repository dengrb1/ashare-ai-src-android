package com.ashareai.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.*
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 会话级全局状态：登录态、资产、行情报价轮询、通知红点。
 * 行情按用户设置的 market_refresh_interval_seconds 轮询，页面退后台时暂停。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AShareApp
    val settings get() = app.settings
    private val api get() = ApiClient.api

    // ---- 登录态 ----
    sealed class AuthState {
        data object Loading : AuthState()
        data object LoggedOut : AuthState()
        data class LoggedIn(val user: UserResponse) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _assets = MutableStateFlow<AssetState?>(null)
    val assets: StateFlow<AssetState?> = _assets.asStateFlow()

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val _marketSession = MutableStateFlow<MarketSession?>(null)
    val marketSession: StateFlow<MarketSession?> = _marketSession.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _globalError = MutableStateFlow<String?>(null)
    val globalError: StateFlow<String?> = _globalError.asStateFlow()

    private var pollJob: Job? = null
    private var foreground = false

    init {
        ApiClient.onSessionExpired = {
            viewModelScope.launch {
                settings.clearTokens()
                _authState.value = AuthState.LoggedOut
            }
        }
        restoreSession()
    }

    private fun restoreSession() {
        viewModelScope.launch {
            val token = settings.currentAccessToken()
            if (token.isNullOrBlank()) {
                _authState.value = AuthState.LoggedOut
                return@launch
            }
            try {
                val user = api.me()
                _authState.value = AuthState.LoggedIn(user)
                loadAssets()
            } catch (e: Exception) {
                // token 失效但有 refresh 的场景由 Authenticator 兜底；这里直接判定登出
                _authState.value = AuthState.LoggedOut
            }
        }
    }

    fun login(username: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val tokens = api.token(LoginRequest(username, password))
                settings.saveTokens(tokens.access_token, tokens.refresh_token, tokens.expires_in, username)
                val user = api.me()
                _authState.value = AuthState.LoggedIn(user)
                loadAssets()
            } catch (e: Exception) {
                onError(e.toUserMessage())
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
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
            while (true) {
                refreshQuotes()
                if (sessionTick % 4 == 0) {
                    refreshMarketStatus()
                    refreshNotificationSummary()
                }
                sessionTick++
                val interval = (_assets.value?.market_refresh_interval_seconds ?: 15).coerceAtLeast(15)
                delay(interval * 1000L)
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
            refreshQuotes()
            refreshMarketStatus()
            refreshNotificationSummary()
        }
    }

    fun clearGlobalError() {
        _globalError.value = null
    }

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

    fun saveRefreshInterval(seconds: Int, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                _assets.value = api.saveMarketRefresh(newIdempotencyKey(), MarketRefreshRequest(seconds))
                restartPolling()
                onDone(null)
            } catch (e: Exception) {
                onDone(e.toUserMessage())
            }
        }
    }
}
