package com.hcdc.legalease.data

import kotlinx.serialization.Serializable

@Serializable
data class ClausesModel(
    val contractName: String = "",
    val summary: String = "",
    val acceptable: List<String> = emptyList(),
    val moderateConcern: List<String> = emptyList(),
    val highRisk: List<String> = emptyList()
)