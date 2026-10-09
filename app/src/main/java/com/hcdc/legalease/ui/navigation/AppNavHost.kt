package com.hcdc.legalease.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.hcdc.legalease.ui.screens.dashboard.DashboardScreen
import com.hcdc.legalease.ui.screens.history.HistoryScreen
import com.hcdc.legalease.ui.screens.intro.IntroScreen
import com.hcdc.legalease.ui.screens.result.ResultScreen
import com.hcdc.legalease.ui.screens.upload_image.ImageScreen
import com.hcdc.legalease.ui.screens.upload_pdf.PDFScreen

@Composable
fun AppNavHost(navController: NavHostController) {
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { IntroScreen(navController) }
        composable<DashboardRoute> { DashboardScreen(navController) }
        composable<PdfRoute> { PDFScreen(navController) }
        composable<ImageRoute> { ImageScreen(navController) }
        composable<HistoryRoute> { HistoryScreen(navController) }
        composable<ResultRoute> { backStackEntry ->
            ResultScreen(analysisId = backStackEntry.toRoute<ResultRoute>().analysisId)
        }
    }
}
