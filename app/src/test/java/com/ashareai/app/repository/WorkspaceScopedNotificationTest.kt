package com.ashareai.app.repository

import com.ashareai.app.workspace.Workspace
import org.junit.Assert.*
import org.junit.Test

/**
 * 工作区通知隔离测试。
 *
 * 验证通知来源标识、通道隔离、深链路由。
 */
class WorkspaceScopedNotificationTest {

    @Test
    fun localNotification_hasCorrectWorkspace() {
        val notification = localNotification(
            id = "test-1",
            title = "本地研究完成",
            body = "3 只候选",
            priority = NotificationPriority.DEFAULT,
            channel = "local_progress",
            route = "reports?date=2026-08-27",
        )

        assertEquals(Workspace.LOCAL, notification.workspace)
    }

    @Test
    fun localNotification_hasCorrectDeepLink() {
        val notification = localNotification(
            id = "test-1",
            title = "本地研究完成",
            body = "3 只候选",
            priority = NotificationPriority.DEFAULT,
            channel = "local_progress",
            route = "reports?date=2026-08-27",
        )

        assertEquals("local/reports?date=2026-08-27", notification.deepLink)
    }

    @Test
    fun fusionNotification_hasCorrectWorkspace() {
        val notification = fusionNotification(
            id = "test-2",
            title = "卖出建议",
            body = "600519 触发止损",
            priority = NotificationPriority.HIGH,
            channel = "fusion_alert",
            route = "exit_advice",
        )

        assertEquals(Workspace.FUSION, notification.workspace)
    }

    @Test
    fun fusionNotification_hasCorrectDeepLink() {
        val notification = fusionNotification(
            id = "test-2",
            title = "卖出建议",
            body = "600519 触发止损",
            priority = NotificationPriority.HIGH,
            channel = "fusion_alert",
            route = "exit_advice",
        )

        assertEquals("fusion/exit_advice", notification.deepLink)
    }

    @Test
    fun notification_channelIsolation() {
        val localNormal = localNotification(
            id = "1",
            title = "本地",
            body = "正常",
            priority = NotificationPriority.DEFAULT,
            channel = "local_normal",
            route = "overview",
        )

        val localAlert = localNotification(
            id = "2",
            title = "本地",
            body = "警告",
            priority = NotificationPriority.HIGH,
            channel = "local_alert",
            route = "alerts",
        )

        val fusionMonitor = fusionNotification(
            id = "3",
            title = "Fusion",
            body = "监控",
            priority = NotificationPriority.DEFAULT,
            channel = "fusion_monitor",
            route = "home",
        )

        // 验证通道隔离
        assertEquals("local_normal", localNormal.channel)
        assertEquals("local_alert", localAlert.channel)
        assertEquals("fusion_monitor", fusionMonitor.channel)

        // 验证工作区隔离
        assertEquals(Workspace.LOCAL, localNormal.workspace)
        assertEquals(Workspace.LOCAL, localAlert.workspace)
        assertEquals(Workspace.FUSION, fusionMonitor.workspace)
    }

    @Test
    fun notification_priorityMapping() {
        val low = localNotification(
            id = "1",
            title = "低优先级",
            body = "测试",
            priority = NotificationPriority.LOW,
            channel = "local_progress",
            route = "overview",
        )

        val high = fusionNotification(
            id = "2",
            title = "高优先级",
            body = "测试",
            priority = NotificationPriority.HIGH,
            channel = "fusion_alert",
            route = "home",
        )

        assertEquals(NotificationPriority.LOW, low.priority)
        assertEquals(NotificationPriority.HIGH, high.priority)
    }

    @Test
    fun notification_timestampGenerated() {
        val before = System.currentTimeMillis()
        val notification = localNotification(
            id = "1",
            title = "测试",
            body = "时间戳",
            priority = NotificationPriority.DEFAULT,
            channel = "local_normal",
            route = "overview",
        )
        val after = System.currentTimeMillis()

        assertTrue(notification.timestamp >= before)
        assertTrue(notification.timestamp <= after)
    }

    @Test
    fun notification_deepLinkWithParameters() {
        val localWithParams = localNotification(
            id = "1",
            title = "报告",
            body = "查看",
            priority = NotificationPriority.DEFAULT,
            channel = "local_progress",
            route = "reports?date=2026-08-27&runId=abc123",
        )

        val fusionWithParams = fusionNotification(
            id = "2",
            title = "候选",
            body = "查看",
            priority = NotificationPriority.DEFAULT,
            channel = "fusion_progress",
            route = "candidates?date=2026-08-27",
        )

        assertEquals("local/reports?date=2026-08-27&runId=abc123", localWithParams.deepLink)
        assertEquals("fusion/candidates?date=2026-08-27", fusionWithParams.deepLink)
    }
}
