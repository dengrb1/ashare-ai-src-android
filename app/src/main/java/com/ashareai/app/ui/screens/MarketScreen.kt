package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.AssetStateRequest
import com.ashareai.app.data.normalizeSymbol
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.EmptyPlaceholder
import com.ashareai.app.ui.components.ErrorBanner
import com.ashareai.app.ui.navigation.Routes
import kotlinx.coroutines.launch

/** 行情页：自选列表 + 添加自选（支持代码或名称搜索）。 */
@Composable
fun MarketScreen(appViewModel: AppViewModel, navController: NavHostController) {
    val assets by appViewModel.assets.collectAsState()
    val quotes by appViewModel.quotes.collectAsState()
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "行情")

        error?.let {
            Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) { error = null } }
        }

        val watchlist = assets?.watchlist ?: emptyList()
        if (watchlist.isEmpty()) {
            EmptyPlaceholder("暂无自选股\n点击右下角添加")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(watchlist, key = { it }) { symbol ->
                    QuoteRow(symbol = symbol, quote = quotes[symbol]) {
                        navController.navigate(Routes.stockDetail(symbol))
                    }
                }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(androidx.compose.ui.Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "添加自选")
        }
    }

    if (showAddDialog) {
        AddSymbolDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { symbol ->
                showAddDialog = false
                val current = assets ?: return@AddSymbolDialog
                if (symbol in current.watchlist) return@AddSymbolDialog
                if (current.watchlist.size >= 100) {
                    error = "自选股最多 100 只"
                    return@AddSymbolDialog
                }
                appViewModel.saveAssets(
                    AssetStateRequest(
                        watchlist = current.watchlist + symbol,
                        positions = current.positions,
                        total_assets = current.total_assets,
                        exit_monitor_enabled = current.exit_monitor_enabled,
                        default_profit_trigger = current.default_profit_trigger,
                        stop_loss_monitor_enabled = current.stop_loss_monitor_enabled,
                        buy_monitor_enabled = current.buy_monitor_enabled,
                        market_refresh_interval_seconds = current.market_refresh_interval_seconds,
                    )
                ) { msg -> if (msg != null) error = msg else appViewModel.forceRefresh() }
            },
        )
    }
}

/** 添加自选对话框：输入 6 位代码自动补后缀；也可名称搜索（securities/resolve）。 */
@Composable
fun AddSymbolDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var searching by remember { mutableStateOf(false) }
    var candidates by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun search() {
        val normalized = normalizeSymbol(input)
        if (normalized != null) {
            onAdd(normalized)
            return
        }
        if (input.isBlank()) return
        searching = true
        message = null
        scope.launch {
            try {
                val resp = ApiClient.api.resolveSecurity(input.trim())
                val found = buildList {
                    resp.match?.let { m -> if (m.symbol != null) add(m.symbol to (m.name ?: "")) }
                    resp.candidates.forEach { c -> if (c.symbol != null) add(c.symbol to (c.name ?: "")) }
                }.distinctBy { it.first }
                if (found.isEmpty()) {
                    message = "未找到匹配的证券"
                } else if (found.size == 1) {
                    onAdd(found.first().first)
                } else {
                    candidates = found
                }
            } catch (e: Exception) {
                message = e.toUserMessage()
            } finally {
                searching = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加自选") },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; candidates = emptyList(); message = null },
                    label = { Text("代码或名称") },
                    placeholder = { Text("600000 或 浦发银行") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { search() }, enabled = !searching) {
                            Icon(Icons.Outlined.Search, contentDescription = "搜索")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (candidates.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    candidates.take(6).forEach { (symbol, name) ->
                        TextButton(onClick = { onAdd(symbol) }, modifier = Modifier.fillMaxWidth()) {
                            Text("$name  $symbol")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { search() }, enabled = !searching && input.isNotBlank()) {
                if (searching) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text("确定")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
