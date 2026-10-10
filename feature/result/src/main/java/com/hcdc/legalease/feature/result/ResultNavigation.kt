package com.hcdc.legalease.feature.result

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Document text never travels through navigation; the screen looks it up by [analysisId]. */
@Serializable
data class ResultRoute(val analysisId: String) {
    companion object {
        /** Must match the property name above: type-safe routes store arguments under it. */
        const val ARG_ANALYSIS_ID = "analysisId"
    }
}

fun NavGraphBuilder.resultScreen() {
    composable<ResultRoute> { ResultScreen() }
}
