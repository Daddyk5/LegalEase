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

/** One page of an analysed document, opened from a quoted clause. [page] is 1-based. */
@Serializable
data class PageRoute(val analysisId: String, val page: Int) {
    companion object {
        const val ARG_ANALYSIS_ID = "analysisId"
        const val ARG_PAGE = "page"
    }
}

fun NavGraphBuilder.resultScreens(onOpenPage: (analysisId: String, page: Int) -> Unit) {
    composable<ResultRoute> { ResultScreen(onOpenPage = onOpenPage) }
    composable<PageRoute> { PageScreen() }
}
