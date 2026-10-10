package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ConfidenceScore
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.UnclearReason
import com.hcdc.legalease.core.domain.result.AppError

internal fun AnalysisEntity.toDomain(): Analysis {
    val status = enumValueOrNull<AnalysisStatus>(status) ?: AnalysisStatus.FAILED
    val report = if (status == AnalysisStatus.COMPLETED) storedReport() else null
    val unreadableReport = status == AnalysisStatus.COMPLETED && report == null
    return Analysis(
        id = id,
        documentName = documentName,
        sourceKind = enumValueOrNull<SourceKind>(sourceKind) ?: SourceKind.PDF,
        status = if (unreadableReport) AnalysisStatus.FAILED else status,
        createdAtMillis = createdAt,
        updatedAtMillis = updatedAt,
        pageCount = pageCount,
        currentPage = currentPage,
        unreadablePages = decodePageList(unreadablePages),
        report = report,
        error = when {
            errorCode != null -> AppError.fromCode(errorCode)
            unreadableReport -> AppError.Unexpected("unreadable_report")
            else -> null
        },
    )
}

/**
 * The report for a completed row:
 * - the full local report when present;
 * - a summary-only report for results synced from another device (the total score was computed by
 *   the same scoring version, so its band is valid; the reasons stay on the original device);
 * - a [AnalysisMethod.LEGACY] report for analyses from earlier versions, whose scores are not
 *   comparable and are never shown as a verdict.
 * Returns null when nothing trustworthy is stored.
 */
private fun AnalysisEntity.storedReport(): AnalysisReport? {
    reportJson?.let { return ReportJson.decode(it) }
    val total = confidence ?: return null
    val legacy = scoringVersion == null
    return AnalysisReport(
        candidate = classification?.let(Classification::fromLabelOrNull),
        confidence = ConfidenceScore(total, components = null),
        summary = summary.orEmpty(),
        contractTitle = null,
        contractType = contractType?.let { enumValueOrNull<DetectedContractType>(it) } ?: DetectedContractType.OTHER,
        reasons = emptyList(),
        missingElements = emptyList(),
        riskFlags = emptyList(),
        recommendations = emptyList(),
        unclear = if (legacy) listOf(UnclearReason.LIMITED_ANALYSIS) else emptyList(),
        pageCount = pageCount ?: 0,
        skippedPages = emptyList(),
        poorQualityPages = emptyList(),
        method = if (legacy) AnalysisMethod.LEGACY else AnalysisMethod.LANGUAGE_MODEL,
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
    // An unreadable draft only disables editing; the saved text is still shown.
    draft = draftJson?.let(DraftJson::decode),
    analysisId = analysisId,
)

internal fun encodePageList(pages: List<Int>): String = pages.joinToString(",")

internal fun decodePageList(encoded: String): List<Int> =
    encoded.split(',').mapNotNull { it.trim().toIntOrNull() }

/** Escapes LIKE wildcards so user input is matched literally (see [AnalysisDao.history]). */
internal fun escapeLike(query: String): String =
    query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

private inline fun <reified T : Enum<T>> enumValueOrNull(name: String): T? =
    enumValues<T>().firstOrNull { it.name == name }
