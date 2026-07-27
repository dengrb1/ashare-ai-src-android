package com.ashareai.app.island

import android.net.Uri
import com.ashareai.app.data.model.Notification

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
        "TRADE_PLAN" -> reportRoute(notification.resource_url) ?: "reports"
        "RESEARCH", "RESEARCH_RUN", "REPORT" -> reportRoute(notification.resource_url) ?: "research"
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

    private fun reportRoute(resourceUrl: String?): String? {
        val uri = runCatching { Uri.parse(resourceUrl) }.getOrNull() ?: return null
        val selectedDate = uri.getQueryParameter("date") ?: return null
        val runId = uri.getQueryParameter("run_id")
        return sanitize("reports?date=$selectedDate" + (runId?.let { "&run_id=$it" } ?: ""))
    }
}
