package com.hcdc.legalease.core.data.remote

import com.hcdc.legalease.core.data.local.AnalysisEntity
import com.hcdc.legalease.core.data.local.GeneratedContractEntity
import com.hcdc.legalease.core.data.local.decodePageList
import com.hcdc.legalease.core.data.local.encodePageList

/**
 * Firestore document shapes for `users/{uid}/analyses/{id}` and `users/{uid}/contracts/{id}`.
 *
 * Only analysis results are uploaded. OCR page text (`document_pages`) and the uploaded
 * files never leave the device.
 */
internal object FirestoreSchema {
    const val SCHEMA_VERSION = 1L

    const val USERS = "users"
    const val ANALYSES = "analyses"
    const val CONTRACTS = "contracts"

    const val F_SCHEMA = "schemaVersion"
    const val F_UPDATED_AT = "updatedAt"
    const val F_DELETED = "deleted"
}

internal fun AnalysisEntity.toFirestore(): Map<String, Any?> = if (deleted) {
    // Tombstones carry no content: only what other devices need to delete their copy.
    mapOf(
        FirestoreSchema.F_SCHEMA to FirestoreSchema.SCHEMA_VERSION,
        FirestoreSchema.F_UPDATED_AT to updatedAt,
        FirestoreSchema.F_DELETED to true,
    )
} else {
    mapOf(
        FirestoreSchema.F_SCHEMA to FirestoreSchema.SCHEMA_VERSION,
        "documentName" to documentName,
        "sourceKind" to sourceKind,
        "status" to status,
        "createdAt" to createdAt,
        FirestoreSchema.F_UPDATED_AT to updatedAt,
        "pageCount" to pageCount?.toLong(),
        "unreadablePages" to decodePageList(unreadablePages).map(Int::toLong),
        "classification" to classification,
        "confidence" to confidence?.toDouble(),
        "summary" to summary,
        "errorCode" to errorCode,
        "contractType" to contractType,
        "scoringVersion" to scoringVersion?.toLong(),
        FirestoreSchema.F_DELETED to false,
    )
}

/** Returns null for malformed documents rather than importing partial data. */
@Suppress("ComplexCondition") // One null check per required field reads clearer than splitting it up.
internal fun analysisFromFirestore(id: String, data: Map<String, Any?>): AnalysisEntity? {
    val updatedAt = (data[FirestoreSchema.F_UPDATED_AT] as? Number)?.toLong() ?: return null
    if (data[FirestoreSchema.F_DELETED] == true) return tombstone(id, updatedAt)

    val documentName = data["documentName"] as? String
    val sourceKind = data["sourceKind"] as? String
    val status = data["status"] as? String
    val createdAt = (data["createdAt"] as? Number)?.toLong()
    if (documentName == null || sourceKind == null || status == null || createdAt == null) return null

    return AnalysisEntity(
        id = id,
        documentName = documentName,
        sourceKind = sourceKind,
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pageCount = (data["pageCount"] as? Number)?.toInt(),
        unreadablePages = encodePageList(
            (data["unreadablePages"] as? List<*>).orEmpty().mapNotNull { (it as? Number)?.toInt() }
        ),
        classification = data["classification"] as? String,
        confidence = (data["confidence"] as? Number)?.toFloat(),
        summary = data["summary"] as? String,
        errorCode = data["errorCode"] as? String,
        contractType = data["contractType"] as? String,
        scoringVersion = (data["scoringVersion"] as? Number)?.toInt(),
        needsSync = false,
        remoteUpdatedAt = updatedAt,
    )
}

private fun tombstone(id: String, updatedAt: Long) = AnalysisEntity(
    id = id,
    documentName = "",
    sourceKind = "",
    status = "",
    createdAt = 0,
    updatedAt = updatedAt,
    remoteUpdatedAt = updatedAt,
    deleted = true,
)

internal fun GeneratedContractEntity.toFirestore(): Map<String, Any?> = mapOf(
    FirestoreSchema.F_SCHEMA to FirestoreSchema.SCHEMA_VERSION,
    "contractId" to contractId,
    "version" to version.toLong(),
    "type" to type,
    "title" to title,
    "body" to body,
    "draft" to draftJson,
    "createdAt" to createdAt,
    FirestoreSchema.F_UPDATED_AT to createdAt,
)

internal fun contractFromFirestore(id: String, data: Map<String, Any?>): GeneratedContractEntity? =
    GeneratedContractEntity(
        id = id,
        contractId = data["contractId"] as? String ?: return null,
        version = (data["version"] as? Number)?.toInt() ?: return null,
        type = data["type"] as? String ?: return null,
        title = data["title"] as? String ?: return null,
        body = data["body"] as? String ?: return null,
        createdAt = (data["createdAt"] as? Number)?.toLong() ?: return null,
        needsSync = false,
        draftJson = data["draft"] as? String,
    )
