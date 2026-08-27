package com.ashareai.app.repository

import com.ashareai.app.workspace.Workspace

/**
 * 工作区作用域通知。
 *
 * 携带工作区标识，确保通知来源清晰，深链路由正确。
 */
data class WorkspaceScopedNotification(
    val workspace: Workspace,
    val id: String,
    val title: String,
    val body: String,
    val priority: NotificationPriority,
    val channel: String,
    val deepLink: String,
    val timestamp: Long = System.currentTimeMillis(),
)

enum class NotificationPriority {
    LOW,
    DEFAULT,
    HIGH,
}

/**
 * 构建本地工作区通知。
 */
fun localNotification(
    id: String,
    title: String,
    body: String,
    priority: NotificationPriority,
    channel: String,
    route: String,
): WorkspaceScopedNotification = WorkspaceScopedNotification(
    workspace = Workspace.LOCAL,
    id = id,
    title = title,
    body = body,
    priority = priority,
    channel = channel,
    deepLink = "local/$route",
)

/**
 * 构建 Fusion 工作区通知。
 */
fun fusionNotification(
    id: String,
    title: String,
    body: String,
    priority: NotificationPriority,
    channel: String,
    route: String,
): WorkspaceScopedNotification = WorkspaceScopedNotification(
    workspace = Workspace.FUSION,
    id = id,
    title = title,
    body = body,
    priority = priority,
    channel = channel,
    deepLink = "fusion/$route",
)
