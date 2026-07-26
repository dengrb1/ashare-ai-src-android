package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.model.Run
import com.ashareai.app.ui.fmtTime
import com.ashareai.app.ui.isActiveStatus
import com.ashareai.app.ui.statusLabel
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.components.StatusChip
import com.ashareai.app.ui.components.statusColor

@Composable
fun RunCard(run: Run, onCancel: (() -> Unit)? = null, onOpenReport: (() -> Unit)? = null) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${run.trading_date ?: "待确定"} · ${scopeLabel(run.research_scope)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    listOfNotNull(
                        run.trigger_source?.let { if (it == "AUTO") "自动日研" else "手动研究" },
                        run.automatic_report_slot?.let { "报告 $it" },
                        run.run_id.take(14),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusChip(statusLabel(run.status), statusColor(run.status))
        }
        if (isActiveStatus(run.status)) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { ((run.progress ?: 0).coerceIn(0, 100)) / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
            Text("${run.phase ?: "等待流水线更新"} · ${run.progress ?: 0}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (run.status.equals("DATA_READINESS_WAITING", true)) {
            Text("等待基准数据同步，下次重试 ${run.next_retry_at.fmtTime()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        run.error_message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        run.reason_message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (onCancel != null || onOpenReport != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                onCancel?.let { TextButton(onClick = it) { Text("停止", color = MaterialTheme.colorScheme.error) } }
                onOpenReport?.let { TextButton(onClick = it) { Text("查看报告") } }
            }
        }
    }
}

private fun scopeLabel(scope: String?): String = when (scope?.uppercase()) {
    "MARKET" -> "全市场"
    "WATCHLIST" -> "自选与持仓"
    "CUSTOM" -> "指定股票"
    else -> scope ?: "研究"
}
