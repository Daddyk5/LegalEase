package com.hcdc.legalease.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.hcdc.legalease.dashboard.DashboardScreen
import com.hcdc.legalease.feature.auth.IntroRoute
import com.hcdc.legalease.feature.auth.introScreen
import com.hcdc.legalease.feature.history.HistoryRoute
import com.hcdc.legalease.feature.history.historyScreen
import com.hcdc.legalease.feature.result.ResultRoute
import com.hcdc.legalease.feature.result.resultScreen
import com.hcdc.legalease.feature.scan.ImageRoute
import com.hcdc.legalease.feature.scan.PdfRoute
import com.hcdc.legalease.feature.scan.scanScreens

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = IntroRoute) {
        introScreen(onLoginSuccess = { navController.navigate(DashboardRoute) })
        composable<DashboardRoute> {
            DashboardScreen(
                onLogout = { navController.navigate(IntroRoute) },
                onUploadPdf = { navController.navigate(PdfRoute) },
                onUploadImage = { navController.navigate(ImageRoute) },
                onOpenHistory = { navController.navigate(HistoryRoute) },
            )
        }
        scanScreens(
            onOpenResult = { navController.navigate(ResultRoute(it)) },
            onBack = { navController.popBackStack() },
        )
        historyScreen(onOpenAnalysis = { navController.navigate(ResultRoute(it)) })
        resultScreen()
    }
}
