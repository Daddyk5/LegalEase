package com.hcdc.legalease.feature.history

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object HistoryRoute

fun NavGraphBuilder.historyScreen(onOpenAnalysis: (analysisId: String) -> Unit) {
    composable<HistoryRoute> { HistoryScreen(onOpenAnalysis = onOpenAnalysis) }
}
