package com.ashareai.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.model.Candidate
import com.ashareai.app.ui.*
import com.ashareai.app.ui.components.*

/** 候选池：确定性评分排名。 */
@Composable
fun CandidatesScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val candidatesViewModel: CandidatesViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val candidatesState by candidatesViewModel.state.collectAsState()
    var date by remember { mutableStateOf(todayTradingDate()) }
    var sortOptionName by rememberSaveable { mutableStateOf(StockSortOption.SCORE_DESC.name) }
    val sortOption = StockSortOption.valueOf(sortOptionName)

    val candidates = when (val state = candidatesState) {
        is ScreenState.Content -> state.value
        is ScreenState.Error -> state.previous.orEmpty()
        ScreenState.Loading, ScreenState.Empty -> emptyList()
    }
    val loading = candidatesState is ScreenState.Loading
    val error = (candidatesState as? ScreenState.Error)?.message

    LaunchedEffect(date) { candidatesViewModel.load(date) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "候选池")

        DateSelectorField(
            value = date,
            onValueChange = { date = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { candidatesViewModel.retry(date) } } }

        if (loading) {
            LoadingBox()
        } else if (candidates.isEmpty()) {
            EmptyPlaceholder("该交易日暂无候选股")
        } else {
            val sortedCandidates = candidates.sortedForStockDisplay(
                option = sortOption,
                scoreOf = { it.total_score },
                rankOf = { it.rank },
                nameOf = { it.name },
                symbolOf = { it.symbol },
            )
            Column(Modifier.fillMaxSize()) {
                StockSortSelector(
                    selected = sortOption,
                    onSelected = { sortOptionName = it.name },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(sortedCandidates, key = { it.symbol }) { c ->
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
}
