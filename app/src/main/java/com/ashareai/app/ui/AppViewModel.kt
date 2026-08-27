package com.ashareai.app.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ashareai.app.AShareApp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.ConnectionClassification
import com.ashareai.app.data.ConnectionProbe
import com.ashareai.app.data.model.UserResponse
import com.ashareai.app.data.toUserMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.ashareai.app.island.PushManager
import retrofit2.HttpException
import java.lang.ref.WeakReference

/**
 * Session and lifecycle owner for the connected app. Feature data and page
 * actions live in dedicated screen ViewModels.
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as AShareApp
    val appContext = application.applicationContext
    private var hostContext = WeakReference<Context>(null)
    val settings get() = app.settings
    val container get() = app.container

    // ---- 登录态 ----
    sealed class AuthState {
        data object Loading : AuthState()
        data object LoggedOut : AuthState()
        data class ConnectionFailed(val message: String) : AuthState()
        data class LoggedIn(val user: UserResponse) : AuthState()
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionProbe?>(null)
    val connection: StateFlow<ConnectionProbe?> = _connection.asStateFlow()

    private val _foreground = MutableStateFlow(false)
    val foreground: StateFlow<Boolean> = _foreground.asStateFlow()

    private var pushEventsJob: Job? = null

    fun attachHostContext(context: Context) {
        hostContext = WeakReference(context)
    }

    fun detachHostContext(context: Context) {
        if (hostContext.get() === context) hostContext.clear()
    }

    fun screenContext(): Context = hostContext.get() ?: appContext

    init {
        ApiClient.onSessionExpired = {
            viewModelScope.launch {
                disableOptionalPush()
                settings.clearTokens()
                _authState.value = AuthState.LoggedOut
            }
        }
        restoreSession()
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
                val probe = container.connectionRepository.probeConfiguredServer()
                _connection.value = probe
                if (!probe.canEstablishSession) {
                    if (probe.classification == ConnectionClassification.Unsupported) settings.clearTokens()
                    _authState.value = AuthState.ConnectionFailed(probe.message ?: "服务器无法建立研究会话。")
                    return@launch
                }
                val bootstrap = container.sessionRepository.restore()
                _authState.value = AuthState.LoggedIn(bootstrap.user)
                initializeOptionalPush()
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
        _authState.value = AuthState.LoggedOut
    }

    fun login(username: String, password: String, rememberPassword: Boolean, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val probe = container.connectionRepository.probeConfiguredServer()
                _connection.value = probe
                if (!probe.canEstablishSession) {
                    settings.clearTokens()
                    onError(probe.message ?: "服务器无法建立研究会话。")
                    return@launch
                }
                val bootstrap = container.sessionRepository.login(username, password, rememberPassword)
                _authState.value = AuthState.LoggedIn(bootstrap.user)
                initializeOptionalPush()
            } catch (e: Exception) {
                onError(e.toUserMessage())
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            disableOptionalPush()
            container.sessionRepository.logout()
            _authState.value = AuthState.LoggedOut
        }
    }

    fun onForeground() {
        _foreground.value = true
    }

    fun onBackground() {
        _foreground.value = false
    }

    /**
     * 推送 SDK 可能唤起厂商进程，只有登录且用户明确开启行情/研究通知后才初始化。
     * 未开启时独立版仅保留前台页面的轻量轮询。
     */
    fun enableOptionalPush() {
        viewModelScope.launch {
            if (!settings.islandEnabled.first()) return@launch
            PushManager.bindAuthenticatedDevice(app)
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

}
