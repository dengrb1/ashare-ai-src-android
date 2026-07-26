package com.ashareai.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CandlestickChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.screens.*
import com.ashareai.app.island.NotificationNavigation
import kotlinx.coroutines.flow.StateFlow

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val MARKET = "market"
    const val RESEARCH_HUB = "research_hub"
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

    fun stockDetail(symbol: String) = "stock/$symbol"
    fun reportDetail(date: String, runId: String?) =
        "reports?date=$date" + (runId?.let { "&run_id=$it" } ?: "")
}

private data class BottomTab(val route: String, val label: String, val icon: ImageVector)

private val bottomTabs = listOf(
    BottomTab(Routes.HOME, "首页", Icons.Outlined.Home),
    BottomTab(Routes.MARKET, "行情", Icons.Outlined.CandlestickChart),
    BottomTab(Routes.RESEARCH_HUB, "研究", Icons.Outlined.Science),
    BottomTab(Routes.AI_CHAT, "问答", Icons.AutoMirrored.Outlined.Chat),
    BottomTab(Routes.PROFILE, "我的", Icons.Outlined.AccountCircle),
)

@Composable
fun AppRoot(
    appViewModel: AppViewModel,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
) {
    val authState by appViewModel.authState.collectAsState()

    when (authState) {
        is AppViewModel.AuthState.Loading -> SplashScreen()
        is AppViewModel.AuthState.LoggedOut -> LoginScreen(appViewModel)
        is AppViewModel.AuthState.LoggedIn -> MainScaffold(appViewModel, pendingRoute, onRouteConsumed)
    }
}

@Composable
private fun MainScaffold(
    appViewModel: AppViewModel,
    pendingRoute: StateFlow<String?>,
    onRouteConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val requestedRoute by pendingRoute.collectAsState()

    LaunchedEffect(requestedRoute) {
        requestedRoute?.let { untrusted ->
            NotificationNavigation.sanitize(untrusted)?.let { route ->
                navController.navigate(route) { launchSingleTop = true }
            }
            onRouteConsumed()
        }
    }

    val showBottomBar = currentRoute in bottomTabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(modifier = Modifier.height(64.dp)) {
                    bottomTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                            alwaysShowLabel = false,
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(Routes.HOME) { DashboardScreen(appViewModel, navController) }
            composable(Routes.MARKET) { MarketScreen(appViewModel, navController) }
            composable(Routes.RESEARCH_HUB) { ResearchHubScreen(appViewModel, navController) }
            composable(Routes.AI_CHAT) { AIChatScreen(appViewModel) }
            composable(Routes.PROFILE) { ProfileScreen(appViewModel, navController) }

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
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(appViewModel) }
            composable(Routes.PERSONAL_DATA) { PersonalDataScreen(appViewModel) }
            composable(Routes.SETTINGS) { SettingsScreen(appViewModel) }
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

fun NavHostController.navigateSingleTop(route: String) {
    navigate(route) { launchSingleTop = true }
}
