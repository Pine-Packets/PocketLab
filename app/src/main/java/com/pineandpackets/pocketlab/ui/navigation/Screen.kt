package com.pineandpackets.pocketlab.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector? = null
) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object Cases : Screen("cases", "Cases", Icons.Filled.Folder)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
    data object About : Screen("about", "About", Icons.Filled.Info)
    data object Onboarding : Screen("onboarding", "Welcome")
    data object Intake : Screen("intake/{uri}", "Analyze File") {
        fun createRoute(uri: String) = "intake/$uri"
    }
    data object Analysis : Screen("analysis/{caseId}", "Analysis") {
        fun createRoute(caseId: String) = "analysis/$caseId"
    }
    data object Report : Screen("report/{caseId}", "Report") {
        fun createRoute(caseId: String) = "report/$caseId"
    }
    
    companion object {
        val bottomNavItems = listOf(Home, Cases, Settings)
    }
}
