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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.AppCard
import com.ashareai.app.ui.navigation.Routes

private data class HubEntry(val title: String, val subtitle: String, val route: String)

private val hubEntries = listOf(
    HubEntry("每日研究", "发起 AI 研究任务，跟踪进度", Routes.RESEARCH),
    HubEntry("研究报告", "查看日报正文与逐股评分", "reports"),
    HubEntry("候选池", "确定性评分与风险过滤结果", Routes.CANDIDATES),
    HubEntry("模拟组合", "组合权重与调仓建议", Routes.PORTFOLIO),
    HubEntry("卖出建议", "盘中触发的 AI 退出方案", Routes.EXIT_ADVICE),
    HubEntry("金融搜索", "自然语言查询行情与财务", Routes.SEARCH),
    HubEntry("回测工作台", "固定快照上的事件回测", Routes.BACKTEST),
    HubEntry("运行与审计", "任务记录与审计时间线", Routes.RUNS),
)

/** 研究中心入口聚合页。 */
@Composable
fun ResearchHubScreen(appViewModel: AppViewModel, navController: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "研究中心")
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(hubEntries.size) { i ->
                val entry = hubEntries[i]
                AppCard(modifier = Modifier.clickable { navController.navigate(entry.route) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entry.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                entry.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
