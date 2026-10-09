package com.hcdc.legalease.data

import kotlinx.serialization.Serializable

/**
 * Contract classifications under the Civil Code of the Philippines.
 *
 * Declaration order is the classifier's output index order and must match the
 * label order the TFLite model was trained with.
 */
@Serializable
enum class Classification(val label: String) {
    VOID("Void"),
    VOIDABLE("Voidable"),
    UNENFORCEABLE("Unenforceable"),
    RESCISSIBLE("Rescissible"),
    ENFORCEABLE("Enforceable");

    companion object {
        /** Returns null for an unrecognised label rather than guessing a classification. */
        fun fromLabelOrNull(label: String): Classification? =
            entries.firstOrNull { it.label.equals(label.trim(), ignoreCase = true) }

        /** Maps a model output index to its classification, or null if out of range. */
        fun fromIndexOrNull(index: Int): Classification? = entries.getOrNull(index)
    }
}
