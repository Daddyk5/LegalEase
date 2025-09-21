package com.hcdc.legalease.data

import kotlinx.serialization.Serializable

/**
 * Represents a contract and its classification result
 * based on the Civil Code categories.
 */
@Serializable
data class ClausesModel(
    val contractName: String,   // e.g., "Loan Agreement"
    val summary: String,        // Short description or extracted text
    val classification: String, // One of: Void, Voidable, Unenforceable, Rescissible, Enforceable
    val confidence: Float       // Model confidence (0.0–1.0)
)
