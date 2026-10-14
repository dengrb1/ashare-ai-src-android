package com.ashareai.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ashareai.app.ui.theme.LiquidGlassSurface
import com.ashareai.app.ui.theme.LiquidGlassDefaults
import com.ashareai.app.workspace.Workspace

/** Hosted inside the active workspace theme, instead of above it in the Activity. */
data class WorkspaceChrome(
    val workspace: Workspace,
    val onSelect: (Workspace) -> Unit,
    val enabled: Boolean = true,
    val onNotifications: (() -> Unit)? = null,
    val unread: Int = 0,
)
val LocalWorkspaceChrome = compositionLocalOf<WorkspaceChrome?> { null }

@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val chrome = LocalWorkspaceChrome.current
    LiquidGlassSurface(modifier.fillMaxWidth(), LiquidGlassDefaults.Light, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)) {
        Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                navigation?.invoke()
                Text(title, Modifier.weight(1f).testTag("chrome.title"), style = MaterialTheme.typography.titleLarge)
                actions()
            }
            if (chrome != null) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    WorkspaceSwitcher(chrome.workspace, chrome.onSelect, Modifier.testTag("chrome.workspace"), chrome.enabled)
                    Spacer(Modifier.weight(1f))
                    chrome.onNotifications?.let { onNotifications ->
                        BadgedBox(badge = { if (chrome.unread > 0) Badge { Text(if (chrome.unread > 99) "99+" else "${chrome.unread}") } }) {
                            IconButton(onClick = onNotifications, modifier = Modifier.testTag("chrome.notifications")) {
                                Icon(Icons.Outlined.Notifications, "通知中心")
                            }
                        }
                    }
                }
            }
        }
    }
}

enum class SettingsCategory(val id: String, val title: String, val description: String) {
    APPEARANCE("appearance", "外观与动效", "主题、强调色、液态玻璃与动画"),
    NOTIFICATIONS("notifications", "通知与后台", "权限、监控频率与资源状态"),
    SHARING("sharing", "工作区共享", "选择同步的设置与外观偏好"),
    AI("ai", "AI 配置", "模型、Provider 与解释授权"),
    DATA("data", "数据与档案", "档案导入导出与缓存管理"),
    CONNECTION("connection", "连接与服务", "服务器地址与基础设施状态"),
}

@Composable
fun SettingsOverview(onSelect: (SettingsCategory) -> Unit, connected: Boolean = false) {
    SettingsCategory.entries.filter { connected || it != SettingsCategory.CONNECTION }.forEach { category ->
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("settings.category.${category.id}").clickable { onSelect(category) },
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
            border = BorderStroke(.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(category.title, style = MaterialTheme.typography.titleMedium)
                    Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(start = 12.dp).size(20.dp))
            }
        }
    }
}

@Composable
fun SettingsCategoryHeading(category: SettingsCategory, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("settings.category.back")) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回设置总览") }
        Column(Modifier.weight(1f)) {
            Text(category.title, style = MaterialTheme.typography.titleLarge)
            Text(category.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Wrapping choices: no horizontal clipping at narrow widths or enlarged text. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GlassSegmentedControl(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    LiquidGlassSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (value, label) ->
                GlassFilterChip(selected == value, { onSelect(value) }, { Text(label) }, Modifier.testTag("appearance.choice.$value"))
            }
        }
    }
}

@Composable
fun AppearanceChoices(mode: String, accent: String, onMode: (String) -> Unit, onAccent: (String) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("主题", style = MaterialTheme.typography.titleMedium)
        GlassSegmentedControl(listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"), mode, onMode)
        Text("强调色", style = MaterialTheme.typography.titleMedium)
        GlassSegmentedControl(listOf("#006B5F" to "青绿", "#3D5AFE" to "靛蓝", "#B3261E" to "红棕", "#7A4EAB" to "紫灰"), accent.uppercase(), onAccent)
    }
}
