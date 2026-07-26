package com.ashareai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.ResearchSettings
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.CompactTopBar
import com.ashareai.app.ui.components.SectionTitle
import com.ashareai.app.ui.navigation.Routes

private data class HubEntry(val title: String, val subtitle: String, val route: String, val icon: ImageVector)

private val primaryResearchEntries = listOf(
    HubEntry("每日研究", "手动任务、自动 A/B 报告与实时进度", Routes.RESEARCH, Icons.Outlined.Science),
    HubEntry("研究报告", "日报正文、逐股评分与研究结论", "reports", Icons.AutoMirrored.Outlined.Article),
    HubEntry("候选池", "确定性评分、排名与风险过滤", Routes.CANDIDATES, Icons.Outlined.Leaderboard),
    HubEntry("模拟组合", "权重、现金比例与调仓建议", Routes.PORTFOLIO, Icons.Outlined.PieChart),
)

private val toolEntries = listOf(
    HubEntry("卖出建议", "盘中触发的退出与止损方案", Routes.EXIT_ADVICE, Icons.AutoMirrored.Outlined.ExitToApp),
    HubEntry("金融搜索", "按公司、证券和财务问题检索", Routes.SEARCH, Icons.Outlined.Search),
    HubEntry("回测工作台", "在版本快照上执行事件回测", Routes.BACKTEST, Icons.Outlined.Timeline),
    HubEntry("运行与审计", "任务记录、失败原因和审计时间线", Routes.RUNS, Icons.Outlined.History),
)

@Composable
fun ResearchHubScreen(appViewModel: AppViewModel, navController: NavHostController) {
    var settings by remember { mutableStateOf<ResearchSettings?>(null) }
    LaunchedEffect(Unit) { settings = runCatching { ApiClient.api.researchSettings() }.getOrNull() }

    Column(Modifier.fillMaxSize()) {
        CompactTopBar(title = "研究中心")
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(Routes.RESEARCH) },
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("自动每日研究", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (settings?.auto_enabled == true) "${settings?.automatic_reports?.count { it.enabled }} 个报告已开启 · 15:05 检查" else "当前未启用 · 点击配置报告 A/B",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
                    }
                }
            }
            item { SectionTitle("研究产出") }
            item { HubGroup(primaryResearchEntries, navController) }
            item { SectionTitle("分析工具") }
            item { HubGroup(toolEntries, navController) }
        }
    }
}

@Composable
private fun HubGroup(entries: List<HubEntry>, navController: NavHostController) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            entries.forEachIndexed { index, entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { navController.navigate(entry.route) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(entry.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(21.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.titleSmall)
                        Text(entry.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (index != entries.lastIndex) HorizontalDivider(Modifier.padding(start = 45.dp), color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
