package com.hcdc.legalease.feature.scan

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object PdfRoute

@Serializable data object ImageRoute

fun NavGraphBuilder.scanScreens(onOpenResult: (analysisId: String) -> Unit, onBack: () -> Unit) {
    composable<PdfRoute> { PdfScreen(onOpenResult = onOpenResult, onBack = onBack) }
    composable<ImageRoute> { ImageScreen(onOpenResult = onOpenResult, onBack = onBack) }
}
