package com.hcdc.legalease.data

import kotlinx.serialization.Serializable

@Serializable
enum class Classification(val label: String) {
    VOID("Void"),
    VOIDABLE("Voidable"),
    UNENFORCEABLE("Unenforceable"),
    RESCISSIBLE("Rescissible"),
    ENFORCEABLE("Enforceable");

    companion object {
        val LABELS: List<String> = entries.map { it.label }

        fun fromLabel(label: String): Classification =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: ENFORCEABLE
    }
}
