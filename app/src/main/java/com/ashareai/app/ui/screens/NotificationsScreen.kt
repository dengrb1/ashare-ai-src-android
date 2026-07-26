package com.ashareai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.Notification
import com.ashareai.app.data.model.NotificationReadRequest
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.fmtTime
import kotlinx.coroutines.launch

/** 通知中心：分页 + 单条/全部已读。 */
@Composable
fun NotificationsScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<Notification>>(emptyList()) }
    var cursor by remember { mutableStateOf<String?>(null) }
    var unreadOnly by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load(reset: Boolean) {
        if (reset) {
            loading = true
            cursor = null
        }
        try {
            val page = ApiClient.api.notifications(
                limit = 30,
                cursor = if (reset) null else cursor,
                unreadOnly = if (unreadOnly) true else null,
            )
            items = if (reset) page.items else items + page.items
            cursor = page.next_cursor
            error = null
        } catch (e: Exception) {
            error = e.toUserMessage()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(unreadOnly) { load(reset = true) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "通知")

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = unreadOnly, onClick = { unreadOnly = !unreadOnly }, label = { Text("仅未读") })
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                scope.launch {
                    try {
                        ApiClient.api.markAllRead(newIdempotencyKey())
                        load(reset = true)
                        appViewModel.forceRefresh()
                    } catch (e: Exception) {
                        error = e.toUserMessage()
                    }
                }
            }) { Text("全部已读") }
        }

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load(true) } } } }

        if (loading) {
            LoadingBox()
        } else if (items.isEmpty()) {
            EmptyPlaceholder("暂无通知")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items, key = { it.notification_id }) { n ->
                    NotificationCard(n) {
                        if (n.read_at == null) {
                            scope.launch {
                                try {
                                    ApiClient.api.markRead(
                                        newIdempotencyKey(),
                                        NotificationReadRequest(listOf(n.notification_id)),
                                    )
                                    items = items.map {
                                        if (it.notification_id == n.notification_id) it.copy(read_at = "read") else it
                                    }
                                    appViewModel.forceRefresh()
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                }
                            }
                        }
                    }
                }
                if (cursor != null) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { scope.launch { load(reset = false) } }) { Text("加载更多") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(n: Notification, onMarkRead: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onMarkRead)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (n.read_at == null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    n.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (n.read_at == null) FontWeight.Bold else FontWeight.Normal,
                )
            }
            TagPill(
                severityLabel(n.severity),
                when (n.severity.uppercase()) {
                    "CRITICAL", "HIGH" -> MaterialTheme.colorScheme.error
                    "WARNING" -> androidx.compose.ui.graphics.Color(0xFFFFA000)
                    else -> MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(n.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            n.created_at.fmtTime(full = true),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun severityLabel(severity: String): String = when (severity.uppercase()) {
    "CRITICAL" -> "紧急"
    "HIGH" -> "高"
    "WARNING" -> "警告"
    else -> "提示"
}
