package com.hcdc.legalease.feature.generator

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Template picker and saved contracts. */
@Serializable
data object GeneratorRoute

/**
 * The contract editor. Pass [type] (a ContractType name) to start a new contract, or [versionId]
 * to open a saved version; editing a saved version saves the next version.
 */
@Serializable
data class ContractEditorRoute(val type: String? = null, val versionId: String? = null) {
    companion object {
        const val ARG_TYPE = "type"
        const val ARG_VERSION_ID = "versionId"
    }
}

fun NavGraphBuilder.generatorScreens(
    onOpenEditor: (ContractEditorRoute) -> Unit,
    onOpenAnalysis: (analysisId: String) -> Unit,
    onBack: () -> Unit,
) {
    composable<GeneratorRoute> { GeneratorHomeScreen(onOpenEditor = onOpenEditor) }
    composable<ContractEditorRoute> { ContractEditorScreen(onOpenAnalysis = onOpenAnalysis, onBack = onBack) }
}
