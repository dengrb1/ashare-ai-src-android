package com.ashareai.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.Candidate
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.launch

/** 候选池：确定性评分排名。 */
@Composable
fun CandidatesScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(todayTradingDate()) }
    var candidates by remember { mutableStateOf<List<Candidate>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        error = null
        try {
            candidates = ApiClient.api.candidates(date)
        } catch (e: Exception) {
            error = e.toUserMessage()
            candidates = emptyList()
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "候选池")

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("交易日") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { scope.launch { load() } }) { Text("查询") }
        }

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { scope.launch { load() } } } }

        if (loading) {
            LoadingBox()
        } else if (candidates.isEmpty()) {
            EmptyPlaceholder("该交易日暂无候选股")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(candidates, key = { it.symbol }) { c ->
                    AppCard(
                        modifier = Modifier.let { m ->
                            m
                        },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "#${c.rank ?: "-"}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(48.dp),
                            )
                            Column(Modifier.weight(1f)) {
                                Text(c.name ?: c.symbol, style = MaterialTheme.typography.titleSmall)
                                Text(c.symbol, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(c.total_score.fmt2(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("最终分", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        c.total_score?.let { score ->
                            LinearProgressIndicator(
                                progress = { (score / 100.0).coerceIn(0.0, 1.0).toFloat() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            c.industry_name?.let { TagPill(it, MaterialTheme.colorScheme.secondary) }
                            c.prediction_percentile?.let { TagPill("预测分位 ${it.fmt2()}") }
                            c.dividend_bonus?.takeIf { it > 0 }?.let { TagPill("分红 +${it.fmt2()}") }
                            c.event_risk_multiplier?.takeIf { it < 1.0 }?.let {
                                TagPill("风险 ×${it.fmt2()}", MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row {
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    navController.navigate("reports?date=$date")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                            ) { Text("查看报告") }
                        }
                    }
                }
            }
        }
    }
}
