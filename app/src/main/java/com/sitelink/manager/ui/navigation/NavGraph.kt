package com.sitelink.manager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.sitelink.manager.SiteLinkApp
import com.sitelink.manager.ui.screens.dashboard.SiteDashboardScreen
import com.sitelink.manager.ui.screens.dashboard.SiteDashboardViewModel
import com.sitelink.manager.ui.screens.edit.AddEditSiteScreen
import com.sitelink.manager.ui.screens.edit.AddEditSiteViewModel
import com.sitelink.manager.ui.screens.home.HomeScreen
import com.sitelink.manager.ui.screens.home.HomeViewModel
import com.sitelink.manager.ui.screens.settings.SettingsScreen
import com.sitelink.manager.ui.screens.settings.SettingsViewModel
import com.sitelink.manager.ui.screens.terminal.TerminalScreen
import com.sitelink.manager.ui.screens.terminal.TerminalViewModel

@Composable
fun SiteLinkNavGraph(
    navController: NavHostController,
    app: SiteLinkApp
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            val viewModel: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(
                    siteRepository = app.siteRepository,
                    sshRepository = app.sshRepository,
                    keyStoreManager = app.keyStoreManager
                )
            )
            HomeScreen(
                viewModel = viewModel,
                onNavigateToAddSite = { navController.navigate(Screen.AddSite.route) },
                onNavigateToEditSite = { siteId -> navController.navigate(Screen.EditSite.createRoute(siteId)) },
                onNavigateToDashboard = { siteId -> navController.navigate(Screen.SiteDashboard.createRoute(siteId)) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.AddSite.route) {
            val viewModel: AddEditSiteViewModel = viewModel(
                factory = AddEditSiteViewModel.Factory(
                    siteRepository = app.siteRepository,
                    siteId = null
                )
            )
            AddEditSiteScreen(
                viewModel = viewModel,
                isEdit = false,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.EditSite.route,
            arguments = listOf(navArgument("siteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val siteId = backStackEntry.arguments?.getLong("siteId") ?: 0L
            val viewModel: AddEditSiteViewModel = viewModel(
                factory = AddEditSiteViewModel.Factory(
                    siteRepository = app.siteRepository,
                    siteId = siteId
                )
            )
            AddEditSiteScreen(
                viewModel = viewModel,
                isEdit = true,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.SiteDashboard.route,
            arguments = listOf(navArgument("siteId") { type = NavType.LongType })
        ) { backStackEntry ->
            val siteId = backStackEntry.arguments?.getLong("siteId") ?: 0L
            val viewModel: SiteDashboardViewModel = viewModel(
                factory = SiteDashboardViewModel.Factory(
                    siteRepository = app.siteRepository,
                    sshRepository = app.sshRepository,
                    keyStoreManager = app.keyStoreManager,
                    siteId = siteId
                )
            )
            SiteDashboardScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTerminal = { sId, type, port ->
                    navController.navigate(Screen.Terminal.createRoute(sId, type, port))
                },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(
            route = Screen.Terminal.route,
            arguments = listOf(
                navArgument("siteId") { type = NavType.LongType },
                navArgument("type") { type = NavType.StringType },
                navArgument("port") { type = NavType.IntType }
            )
        ) { backStackEntry ->
            val siteId = backStackEntry.arguments?.getLong("siteId") ?: 0L
            val type = backStackEntry.arguments?.getString("type") ?: "VM"
            val port = backStackEntry.arguments?.getInt("port") ?: 2222
            val viewModel: TerminalViewModel = viewModel(
                factory = TerminalViewModel.Factory(
                    siteRepository = app.siteRepository,
                    sshRepository = app.sshRepository,
                    siteId = siteId,
                    sessionType = type,
                    targetPort = port
                )
            )
            TerminalScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(
                    database = app.database,
                    keyStoreManager = app.keyStoreManager,
                    serverConfigManager = app.serverConfigManager,
                    sshRepository = app.sshRepository
                )
            )
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
