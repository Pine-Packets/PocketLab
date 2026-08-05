package com.pineandpackets.pocketlab.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pineandpackets.pocketlab.feature.about.AboutScreen
import com.pineandpackets.pocketlab.feature.analysis.AnalysisScreen
import com.pineandpackets.pocketlab.feature.cases.CasesScreen
import com.pineandpackets.pocketlab.feature.home.HomeScreen
import com.pineandpackets.pocketlab.feature.intake.IntakeScreen
import com.pineandpackets.pocketlab.feature.onboarding.OnboardingScreen
import com.pineandpackets.pocketlab.feature.report.ReportScreen
import com.pineandpackets.pocketlab.feature.settings.SettingsScreen

@Composable
fun PocketLabNavHost(
    navController: NavHostController,
    startDestination: String = Screen.Home.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToCases = {
                    navController.navigate(Screen.Cases.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                onAnalyzeFile = { uri ->
                    navController.navigate(Screen.Intake.createRoute(uri.toString()))
                }
            )
        }
        
        composable(Screen.Cases.route) {
            CasesScreen(
                onNavigateToReport = { caseId ->
                    navController.navigate(Screen.Report.createRoute(caseId))
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.About.route) {
            AboutScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }
        
        composable(
            route = Screen.Intake.route,
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backStackEntry ->
            val uri = backStackEntry.arguments?.getString("uri") ?: return@composable
            IntakeScreen(
                uriString = uri,
                onAnalysisStarted = { caseId ->
                    navController.navigate(Screen.Analysis.createRoute(caseId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.Analysis.route,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getString("caseId") ?: return@composable
            AnalysisScreen(
                caseId = caseId,
                onReportReady = {
                    navController.navigate(Screen.Report.createRoute(caseId)) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(
            route = Screen.Report.route,
            arguments = listOf(navArgument("caseId") { type = NavType.StringType })
        ) { backStackEntry ->
            val caseId = backStackEntry.arguments?.getString("caseId") ?: return@composable
            ReportScreen(
                caseId = caseId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
