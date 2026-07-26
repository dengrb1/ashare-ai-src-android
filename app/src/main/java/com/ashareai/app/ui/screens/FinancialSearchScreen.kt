package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.FinancialSearchResult
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.*
import kotlinx.coroutines.launch

private val exampleQueries = listOf(
    "浦发银行现在多少钱", "沪深300今年走势", "贵州茅台市盈率", "宁德时代最新财报",
)

/** 金融数据搜索：自然语言查询。 */
@Composable
fun FinancialSearchScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<FinancialSearchResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun search(q: String) {
        if (q.isBlank() || loading) return
        loading = true
        error = null
        scope.launch {
            try {
                result = ApiClient.api.financialSearch(q.trim())
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "金融搜索")

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("自然语言查询行情、估值、财务…") },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { search(query) }) {
                    Icon(Icons.Outlined.Search, contentDescription = "搜索")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        error?.let { Box(Modifier.padding(16.dp)) { ErrorBanner(it) { search(query) } } }

        if (loading) {
            LoadingBox()
        } else if (result == null) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("试试这些查询", style = MaterialTheme.typography.titleSmall)
                }
                items(exampleQueries) { q ->
                    SuggestionChip(onClick = { query = q; search(q) }, label = { Text(q) })
                }
            }
        } else {
            val r = result!!
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                r.interpretation?.let {
                    item {
                        AppCard {
                            Text("AI 解读", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (r.entities.isNotEmpty()) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            r.entities.take(4).forEach { e ->
                                TagPill("${e.name ?: ""} ${e.symbol ?: ""}".trim())
                            }
                        }
                    }
                }
                items(r.results) { item ->
                    AppCard {
                        item.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                        item.type?.let {
                            Spacer(Modifier.height(2.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        val body = item.content ?: item.description
                        body?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 8)
                        }
                    }
                }
                if (r.warnings.isNotEmpty()) {
                    item {
                        r.warnings.forEach {
                            Text("⚠ $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                item {
                    Text(
                        listOfNotNull(
                            r.provider?.let { "来源 $it" },
                            r.elapsed_ms?.let { "耗时 ${it}ms" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
