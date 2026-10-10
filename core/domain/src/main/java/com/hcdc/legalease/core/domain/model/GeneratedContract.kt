package com.hcdc.legalease.core.domain.model

/** The four contract templates LegalEase generates. */
enum class ContractType { LEASE, LOAN, SALE, LEASE_OF_PERSONAL_PROPERTY }

/**
 * One immutable version of a generated contract. Edits create a new version with the
 * same [contractId] and [version] + 1; earlier versions are never overwritten.
 */
data class GeneratedContract(
    val id: String,
    val contractId: String,
    val version: Int,
    val type: ContractType,
    val title: String,
    val body: String,
    val createdAtMillis: Long,
)
