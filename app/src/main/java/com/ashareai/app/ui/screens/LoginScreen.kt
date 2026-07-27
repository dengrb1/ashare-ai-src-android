package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.normalizeServerUrl
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.ErrorBanner
import kotlinx.coroutines.launch

@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp)
    }
}

@Composable
fun ConnectionFailedScreen(
    appViewModel: AppViewModel,
    message: String,
    onRetry: () -> Unit,
    onReturnToLogin: () -> Unit,
) {
    var serverUrl by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { serverUrl = appViewModel.settings.currentBaseUrl() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Default.VisibilityOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(16.dp))
            Text("无法连接服务器", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (serverUrl.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("当前地址：$serverUrl", textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("重试") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReturnToLogin, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("返回登录页面") }
        }
    }
}

@Composable
fun LoginScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberPassword by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        serverUrl = appViewModel.settings.currentBaseUrl()
        username = appViewModel.settings.currentUsername().orEmpty()
        rememberPassword = appViewModel.settings.isRememberPasswordEnabled()
        if (rememberPassword) password = appViewModel.settings.currentRememberedPassword().orEmpty()
    }

    fun submit() {
        if (loading) return
        error = null
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
            error = "请填写服务器地址、用户名和密码"
            return
        }
        val normalizedUrl = normalizeServerUrl(serverUrl).getOrElse {
            error = it.message ?: "服务器地址格式不正确"
            return
        }
        serverUrl = normalizedUrl
        loading = true
        scope.launch {
            appViewModel.settings.setBaseUrl(normalizedUrl)
            com.ashareai.app.data.ApiClient.rebuild()
            appViewModel.login(username.trim(), password, rememberPassword) { msg ->
                error = msg
                loading = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "霁衡智研",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "A股 AI 投研 · 移动端",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))

            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.10:8000") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = if (passwordVisible) "隐藏密码" else "显示密码",
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberPassword,
                    onCheckedChange = { enabled ->
                        rememberPassword = enabled
                        if (!enabled) scope.launch { appViewModel.settings.clearRememberedPassword() }
                    },
                )
                Text("记住账号密码")
            }
            Spacer(Modifier.height(20.dp))

            error?.let {
                ErrorBanner(it)
                Spacer(Modifier.height(12.dp))
            }

            Button(
                onClick = { submit() },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("登录")
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "使用 Bearer 令牌认证 · 会话自动续期",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}
