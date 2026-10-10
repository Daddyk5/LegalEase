package com.hcdc.legalease.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hcdc.legalease.dashboard.DashboardScreen
import com.hcdc.legalease.feature.auth.IntroRoute
import com.hcdc.legalease.feature.auth.introScreen
import com.hcdc.legalease.feature.generator.GeneratorRoute
import com.hcdc.legalease.feature.generator.generatorScreens
import com.hcdc.legalease.feature.history.HistoryRoute
import com.hcdc.legalease.feature.settings.PrivacyNoticeRoute
import com.hcdc.legalease.feature.settings.SettingsRoute
import com.hcdc.legalease.feature.settings.settingsScreens
import com.hcdc.legalease.feature.history.historyScreen
import com.hcdc.legalease.feature.result.ResultRoute
import com.hcdc.legalease.feature.result.PageRoute
import com.hcdc.legalease.feature.result.resultScreens
import com.hcdc.legalease.feature.scan.ImageRoute
import com.hcdc.legalease.feature.scan.PdfRoute
import com.hcdc.legalease.feature.scan.scanScreens

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = IntroRoute) {
        introScreen(onLoginSuccess = { navController.navigate(DashboardRoute) })
        composable<DashboardRoute> {
            DashboardScreen(
                onLoggedOut = {
                    navController.navigate(IntroRoute) { popUpTo(navController.graph.id) { inclusive = true } }
                },
                onUploadPdf = { navController.navigate(PdfRoute) },
                onUploadImage = { navController.navigate(ImageRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
                onCreateContract = { navController.navigate(GeneratorRoute) },
                onOpenSettings = { navController.navigate(SettingsRoute) },
            )
        }
        scanScreens(
            onOpenResult = { navController.navigate(ResultRoute(it)) },
            onBack = { navController.popBackStack() },
        )
        historyScreen(onOpenAnalysis = { navController.navigate(ResultRoute(it)) })
        resultScreens(onOpenPage = { id, page -> navController.navigate(PageRoute(id, page)) })
        settingsScreens(
            onOpenNotice = { navController.navigate(PrivacyNoticeRoute) },
            onSignedOut = {
                navController.navigate(IntroRoute) { popUpTo(navController.graph.id) { inclusive = true } }
            },
            onBack = { navController.popBackStack() },
        )
        generatorScreens(
            onOpenEditor = { navController.navigate(it) },
            onOpenAnalysis = { navController.navigate(ResultRoute(it)) },
            onBack = { navController.popBackStack() },
        )
    }
}
