package com.ashareai.app.island

import com.ashareai.app.data.model.Notification
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationNavigationTest {
    @Test
    fun researchNotificationWithoutResourceUrlOpensReports() {
        val notification = Notification(
            notification_id = "n1",
            resource_type = "RESEARCH_RUN",
            resource_id = "run-1",
        )

        assertEquals("reports", NotificationNavigation.forNotification(notification))
    }
}
