package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.Verdict
import com.hcdc.legalease.core.domain.result.AppError

internal fun AnalysisEntity.toDomain(): Analysis {
    val status = enumValueOrNull<AnalysisStatus>(status) ?: AnalysisStatus.FAILED
    val classification = classification?.let(Classification::fromLabelOrNull)
    // A completed row must carry both a known label and the stored confidence; never invent either.
    val verdict = if (status == AnalysisStatus.COMPLETED && classification != null && confidence != null) {
        Verdict(classification, confidence, summary.orEmpty())
    } else {
        null
    }
    return Analysis(
        id = id,
        documentName = documentName,
        sourceKind = enumValueOrNull<SourceKind>(sourceKind) ?: SourceKind.PDF,
        status = if (status == AnalysisStatus.COMPLETED && verdict == null) AnalysisStatus.FAILED else status,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        pageCount = pageCount,
        currentPage = currentPage,
        unreadablePages = decodePageList(unreadablePages),
        verdict = verdict,
        error = when {
            errorCode != null -> AppError.fromCode(errorCode)
            status == AnalysisStatus.COMPLETED && verdict == null -> AppError.Unexpected("unreadable_verdict")
            else -> null
        },
    )
}

internal fun GeneratedContractEntity.toDomain(): GeneratedContract = GeneratedContract(
    id = id,
    contractId = contractId,
    version = version,
    type = ContractType.valueOf(type),
    title = title,
    body = body,
    createdAtMillis = createdAt,
)

internal fun encodePageList(pages: List<Int>): String = pages.joinToString(",")

internal fun decodePageList(encoded: String): List<Int> =
    encoded.split(',').mapNotNull { it.trim().toIntOrNull() }

/** Escapes LIKE wildcards so user input is matched literally (see [AnalysisDao.observeHistory]). */
internal fun escapeLike(query: String): String =
    query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
