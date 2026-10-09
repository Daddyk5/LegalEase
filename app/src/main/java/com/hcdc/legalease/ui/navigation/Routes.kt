package com.hcdc.legalease.ui.navigation

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute

@Serializable data object DashboardRoute

@Serializable data object PdfRoute

@Serializable data object ImageRoute

@Serializable data object HistoryRoute

/** Document text never travels through navigation; screens look it up by [analysisId]. */
@Serializable data class ResultRoute(val analysisId: String)
