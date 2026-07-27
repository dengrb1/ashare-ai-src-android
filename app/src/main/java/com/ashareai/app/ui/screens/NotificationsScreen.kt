package com.ashareai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.model.Notification
import com.ashareai.app.island.NotificationNavigation
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.fmtTime

@Composable
fun NotificationsScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val center = appViewModel.notificationCenter
    val state by center.state.collectAsState()

    LaunchedEffect(Unit) { center.refresh() }

    Column(Modifier.fillMaxSize()) {
        CompactTopBar(
            title = "通知",
            actions = {
                IconButton(onClick = { center.refresh() }, enabled = !state.loading) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            },
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.unreadOnly,
                onClick = { center.setUnreadOnly(!state.unreadOnly) },
                label = { Text("仅未读") },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = center::markAllRead) { Text("全部已读") }
        }

        state.error?.let {
            Box(Modifier.padding(16.dp)) {
                ErrorBanner(it) { center.clearError(); center.refresh() }
            }
        }
        when {
            state.loading -> LoadingBox()
            state.items.isEmpty() -> EmptyPlaceholder(if (state.unreadOnly) "没有未读通知" else "暂无通知")
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.notification_id }) { notice ->
                    NotificationCard(notice) {
                        center.markRead(notice.notification_id)
                        val route = NotificationNavigation.forNotification(notice)
                        if (route != "notifications") navController.navigate(route) { launchSingleTop = true }
                    }
                }
                if (state.nextCursor != null) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = center::loadMore, enabled = !state.loadingMore) {
                                Text(if (state.loadingMore) "加载中" else "加载更多")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notice: Notification, onClick: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (notice.read_at == null) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                notice.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (notice.read_at == null) FontWeight.Bold else FontWeight.Normal,
            )
            TagPill(severityLabel(notice.severity), severityColor(notice.severity))
        }
        Spacer(Modifier.height(6.dp))
        Text(notice.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Text(
            notice.created_at.fmtTime(full = true),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun severityColor(severity: String) = when (severity.uppercase()) {
    "CRITICAL", "HIGH" -> MaterialTheme.colorScheme.error
    "WARNING" -> androidx.compose.ui.graphics.Color(0xFFFFA000)
    else -> MaterialTheme.colorScheme.primary
}

private fun severityLabel(severity: String): String = when (severity.uppercase()) {
    "CRITICAL" -> "紧急"
    "HIGH" -> "高"
    "WARNING" -> "警告"
    else -> "提示"
}
