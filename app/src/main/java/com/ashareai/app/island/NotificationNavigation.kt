package com.ashareai.app.island

import android.net.Uri
import com.ashareai.app.data.model.Notification
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object NotificationNavigation {
    private val symbolRoute = Regex("stock/\\d{6}\\.(SH|SZ|BJ)")
    private val date = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val safeId = Regex("[A-Za-z0-9_-]{1,128}")
    private val exactRoutes = setOf(
        "home", "market", "research_hub", "research", "reports", "backtest",
        "exit_advice", "notifications", "runs", "portfolio", "candidates",
    )

    fun forNotification(notification: Notification): String = when (notification.resource_type?.uppercase()) {
        "BACKTEST" -> "backtest"
        "EXIT_ADVICE" -> "exit_advice"
        "TRADE_PLAN" -> reportRoute(notification) ?: "reports"
        "RESEARCH", "RESEARCH_RUN", "REPORT" -> reportRoute(notification) ?: "reports"
        else -> "notifications"
    }

    /** Treats routes from Intents as untrusted even though normal PendingIntents are explicit. */
    fun sanitize(route: String?): String? {
        if (route == null) return null
        if (route in exactRoutes || symbolRoute.matches(route)) return route
        if (!route.startsWith("reports?")) return null
        val uri = Uri.parse("ashare://local/$route")
        val selectedDate = uri.getQueryParameter("date") ?: return null
        val runId = uri.getQueryParameter("run_id")
        if (!date.matches(selectedDate) || (runId != null && !safeId.matches(runId))) return null
        return "reports?date=$selectedDate" + (runId?.let { "&run_id=$it" } ?: "")
    }

    private fun reportRoute(notification: Notification): String? {
        notification.resource_url?.let { resourceUrl ->
            val uri = runCatching { Uri.parse(resourceUrl) }.getOrNull()
            val selectedDate = uri?.getQueryParameter("date")
            if (selectedDate != null) {
                val runId = uri.getQueryParameter("run_id")
                sanitize("reports?date=$selectedDate" + (runId?.let { "&run_id=$it" } ?: ""))?.let { return it }
            }
        }

        // The notification API historically omitted resource_url for research
        // notifications. Accept the date fields added to newer payloads while
        // retaining the report page fallback for older stored notifications.
        val selectedDate = listOf("trading_date", "date")
            .asSequence()
            .mapNotNull { (notification.payload[it] as? JsonPrimitive)?.contentOrNull }
            .firstOrNull { date.matches(it) }
            ?: return null
        val runId = (notification.payload["run_id"] as? JsonPrimitive)?.contentOrNull
            ?: notification.resource_id
        return sanitize("reports?date=$selectedDate" + (runId?.let { "&run_id=$it" } ?: ""))
    }
}
