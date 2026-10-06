package com.ashareai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.navigation.Routes

/** 我的页：用户信息 + 设置入口 + 退出登录。 */
@Composable
fun ProfileScreen(
    appViewModel: AppViewModel,
    navController: NavHostController,
    onSwitchToLocal: () -> Unit = {},
) {
    val authState by appViewModel.authState.collectAsState()
    val user = (authState as? AppViewModel.AuthState.LoggedIn)?.user
    var confirmLogout by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "我的")

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text(user?.username ?: "--", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (user?.role?.equals("ADMIN", ignoreCase = true) == true || user?.is_admin_account == true) "管理员" else "普通用户",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { ProfileEntry("通知中心") { navController.navigate(Routes.NOTIFICATIONS) } }
            item { ProfileEntry("个人档案（导出/导入）") { navController.navigate(Routes.PERSONAL_DATA) } }
            item { ProfileEntry("双工作区同步") { navController.navigate(Routes.WORKSPACE_SYNC) } }
            item { ProfileEntry("切换到独立版") { onSwitchToLocal() } }
            item { ProfileEntry("设置") { navController.navigate(Routes.SETTINGS) } }
            item { ProfileEntry("策略设置与智能训练") { navController.navigate(Routes.STRATEGY_SETTINGS) } }
            if (user?.role?.equals("ADMIN", ignoreCase = true) == true || user?.is_admin_account == true) {
                item {
                    Text(
                        "管理员控制台",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                    )
                }
                item { ProfileEntry("AI Agent 设置") { navController.navigate(Routes.MODEL_SETTINGS) } }
                item { ProfileEntry("系统资源与运行配置") { navController.navigate(Routes.SYSTEM_SETTINGS) } }
                item { ProfileEntry("Edge Gateway") { navController.navigate(Routes.EDGE_GATEWAY) } }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("退出登录") }
            }

            item {
                Text(
                    "本应用仅提供研究、模拟组合与观察建议，不执行真实交易。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    appViewModel.logout()
                }) { Text("退出", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProfileEntry(label: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
