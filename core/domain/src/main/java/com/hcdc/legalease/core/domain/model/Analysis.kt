package com.hcdc.legalease.core.domain.model

import com.hcdc.legalease.core.domain.result.AppError

/** How the document was uploaded. */
enum class SourceKind { PDF, IMAGES }

enum class AnalysisStatus {
    QUEUED,
    EXTRACTING_TEXT,
    CLASSIFYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

/**
 * One scanned document and the state of its analysis.
 *
 * @property currentPage 1-based page being read while [status] is [AnalysisStatus.EXTRACTING_TEXT].
 * @property unreadablePages zero-based pages that yielded no text.
 * @property verdict set only when [status] is [AnalysisStatus.COMPLETED].
 * @property error set only when [status] is [AnalysisStatus.FAILED].
 */
data class Analysis(
    val id: String,
    val documentName: String,
    val sourceKind: SourceKind,
    val status: AnalysisStatus,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val pageCount: Int? = null,
    val currentPage: Int? = null,
    val unreadablePages: List<Int> = emptyList(),
    val verdict: Verdict? = null,
    val error: AppError? = null,
)

/**
 * The classifier's result. [confidence] is the model's own probability for [classification],
 * stored as produced; it is never rounded up, clamped, or substituted.
 */
data class Verdict(
    val classification: Classification,
    val confidence: Float,
    val summary: String,
)

/** Raw classifier output: top class, its probability, and the distribution in [Classification] order. */
data class Prediction(
    val classification: Classification,
    val probability: Float,
    val probabilities: List<Float>,
)

/** OCR output: text per page in order (blank for unreadable pages). */
data class ExtractedText(
    val pages: List<String>,
    val unreadablePages: List<Int>,
) {
    val hasText: Boolean get() = pages.any { it.isNotBlank() }
    val fullText: String get() = pages.filter { it.isNotBlank() }.joinToString(separator = "\n\n")
}
