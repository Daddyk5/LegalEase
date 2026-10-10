package com.hcdc.legalease.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

/**
 * Remote-controlled feature flags and kill switches. [DEFAULT] is what the app uses until a fetch
 * succeeds, and matches the bundled Remote Config defaults.
 */
data class FeatureFlags(
    val generatorEnabled: Boolean,
    val llmEnabled: Boolean,
    /** Explanatory text shown next to the confidence band, e.g. "Verdicts are shown only at ≥ 80% confidence." */
    val confidenceThresholdText: String,
) {
    companion object {
        val DEFAULT = FeatureFlags(
            generatorEnabled = true,
            llmEnabled = true,
            confidenceThresholdText = "A classification is shown only when LegalEase is at least 80% confident.",
        )
    }
}

interface FeatureFlagsRepository {
    val flags: StateFlow<FeatureFlags>

    /** Fetches and activates the latest values; failures keep the current values. */
    suspend fun refresh()
}
