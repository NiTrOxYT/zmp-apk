package com.sitelink.manager.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddSite : Screen("add_site")
    data object EditSite : Screen("edit_site/{siteId}") {
        fun createRoute(siteId: Long): String = "edit_site/$siteId"
    }
    data object SiteDashboard : Screen("dashboard/{siteId}") {
        fun createRoute(siteId: Long): String = "dashboard/$siteId"
    }
    data object Terminal : Screen("terminal/{siteId}/{type}/{port}") {
        fun createRoute(siteId: Long, type: String, port: Int): String = "terminal/$siteId/$type/$port"
    }
    data object Settings : Screen("settings")
}
