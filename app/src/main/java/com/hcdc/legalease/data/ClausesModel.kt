package com.hcdc.legalease.data

import kotlinx.serialization.Serializable

@Serializable
data class ClausesModel(
    val contractName: String,
    val summary: String,
    val classification: Classification, // ✅ enum instead of String
    val confidence: Float
) {
    init {
        require(confidence in 0f..1f) { "confidence must be within [0,1]: $confidence" }
    }
}
