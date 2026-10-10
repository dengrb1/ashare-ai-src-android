package com.ashareai.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.LiquidGlassBottomBar
import com.ashareai.app.ui.components.LiquidGlassTab
import com.ashareai.app.ui.theme.LiquidGlassBackground
import com.ashareai.app.ui.screens.*
import com.ashareai.app.island.NotificationNavigation
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val MARKET = "market"
    const val RESEARCH_HUB = "research_hub"
    const val STRATEGY_EVOLUTION = "strategy_evolution"
    const val STRATEGY_SETTINGS = "strategy_settings"
    const val WORKSPACE_SYNC = "workspace_sync"
    const val AI_CHAT = "ai_chat"
    const val PROFILE = "profile"

    const val ASSETS = "assets"
    const val RESEARCH = "research"
    const val REPORTS = "reports"
    const val CANDIDATES = "candidates"
    const val PORTFOLIO = "portfolio"
    const val EXIT_ADVICE = "exit_advice"
    const val BACKTEST = "backtest"
    const val RUNS = "runs"
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val PERSONAL_DATA = "personal_data"
    const val SETTINGS = "settings"
    const val MODEL_SETTINGS = "model_settings"
    const val SYSTEM_SETTINGS = "system_settings"
    const val EDGE_GATEWAY = "edge_gateway"

    fun stockDetail(symbol: String) = "stock/$symbol"
    fun reportDetail(date: String, runId: String?) =
        "reports?date=$date" + (runId?.let { "&run_id=$it" } ?: "")
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "监控", Icons.Outlined.Home),
    BottomTab(Routes.RESEARCH_HUB, "研究 / 回测", Icons.Outlined.Science),
    BottomTab(Routes.AI_CHAT, "AI 诊断", Icons.AutoMirrored.Outlined.Chat),
    BottomTab(Routes.PROFILE, "设置", Icons.Outlined.AccountCircle),
)

@Composable
fun AppRoot(
    appViewModel: AppViewModel,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
    onSwitchToLocal: () -> Unit = {},
    initialRoute: String? = null,
    onRouteChanged: (String) -> Unit = {},
) {
    val authState by appViewModel.authState.collectAsState()

    when (val state = authState) {
        is AppViewModel.AuthState.Loading -> SplashScreen()
        is AppViewModel.AuthState.LoggedOut -> LoginScreen(appViewModel)
        is AppViewModel.AuthState.ConnectionFailed -> ConnectionFailedScreen(
            appViewModel = appViewModel,
            message = state.message,
            onRetry = appViewModel::retrySessionRestore,
            onReturnToLogin = appViewModel::showLogin,
        )
        is AppViewModel.AuthState.LoggedIn -> MainScaffold(
            appViewModel,
            pendingRoute,
            onRouteConsumed,
            onSwitchToLocal,
            initialRoute,
            onRouteChanged,
        )
    }
}

@Composable
private fun MainScaffold(
    appViewModel: AppViewModel,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
    onSwitchToLocal: () -> Unit,
    initialRoute: String?,
    onRouteChanged: (String) -> Unit,
) {
    val startRoute = initialRoute?.takeIf { it in bottomTabs.map(BottomTab::route) } ?: Routes.HOME
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val requestedRoute by pendingRoute.collectAsState()

    // 省电模式状态
    val isPowerSaveMode by appViewModel.isPowerSaveMode.collectAsState()

    LaunchedEffect(currentRoute) {
        currentRoute?.takeIf { it in bottomTabs.map(BottomTab::route) }?.let(onRouteChanged)
    }

    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { untrusted ->
            NotificationNavigation.sanitize(untrusted)?.let { route ->
                navController.navigate(route) { launchSingleTop = true }
            }
            onRouteConsumed()
        }
    }

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    // 全局液体玻璃动画背景
    LiquidGlassBackground(
        modifier = Modifier.fillMaxSize(),
        intensity = 0.18f,
    ) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                LiquidGlassBottomBar(
                    tabs = bottomTabs.map { LiquidGlassTab(it.route, it.label, it.icon) },
                    selectedKey = currentRoute ?: Routes.HOME,
                    powerSaveMode = isPowerSaveMode,
                    onSelect = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
        containerColor = Color.Transparent,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startRoute,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.HOME) { DashboardScreen(appViewModel, navController) }
            composable(Routes.MARKET) { MarketScreen(appViewModel, navController) }
            composable(Routes.RESEARCH_HUB) { ResearchHubScreen(appViewModel, navController) }
            composable(Routes.STRATEGY_EVOLUTION) { StrategyEvolutionScreen(appViewModel, navController) }
            composable(Routes.STRATEGY_SETTINGS) { StrategySettingsScreen(appViewModel, navController) }
            composable(Routes.AI_CHAT) { AIChatScreen(appViewModel) }
            composable(Routes.PROFILE) { ProfileScreen(appViewModel, navController, onSwitchToLocal) }

            composable(Routes.ASSETS) { AssetsScreen(appViewModel, navController) }
            composable(Routes.RESEARCH) { ResearchScreen(appViewModel, navController) }
            composable(
                "reports?date={date}&run_id={run_id}",
                arguments = listOf(
                    androidx.navigation.navArgument("date") { nullable = true; defaultValue = null },
                    androidx.navigation.navArgument("run_id") { nullable = true; defaultValue = null },
                ),
            ) { entry ->
                ReportsScreen(
                    appViewModel,
                    navController,
                    initialDate = entry.arguments?.getString("date"),
                    initialRunId = entry.arguments?.getString("run_id"),
                )
            }
            composable(Routes.CANDIDATES) { CandidatesScreen(appViewModel, navController) }
            composable(Routes.PORTFOLIO) { PortfolioScreen(appViewModel) }
            composable(Routes.EXIT_ADVICE) { ExitAdviceScreen(appViewModel) }
            composable(Routes.BACKTEST) { BacktestScreen(appViewModel) }
            composable(Routes.RUNS) { RunsScreen(appViewModel) }
            composable(Routes.SEARCH) { FinancialSearchScreen(appViewModel) }
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(appViewModel, navController) }
            composable(Routes.PERSONAL_DATA) { PersonalDataScreen(appViewModel) }
            composable(Routes.WORKSPACE_SYNC) { WorkspaceSyncScreen(appViewModel, navController) }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    appViewModel = appViewModel,
                    onBack = { navController.popBackStack() },
                    onOpenStrategySettings = { navController.navigate(Routes.STRATEGY_SETTINGS) },
                )
            }
            composable(Routes.MODEL_SETTINGS) { ModelSettingsScreen(appViewModel) }
            composable(Routes.SYSTEM_SETTINGS) { SystemSettingsScreen(appViewModel) }
            composable(Routes.EDGE_GATEWAY) { EdgeGatewayScreen(appViewModel) }
            composable("stock/{symbol}") { entry ->
                StockDetailScreen(
                    appViewModel,
                    navController,
                    symbol = entry.arguments?.getString("symbol") ?: "",
                )
            }
        }
    }
    }
}

fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
