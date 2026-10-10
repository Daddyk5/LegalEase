package com.hcdc.legalease.core.domain.model

import com.hcdc.legalease.core.domain.result.AppError

/** How the document was uploaded. */
enum class SourceKind {
    PDF,
    IMAGES,

    /** A contract LegalEase generated: the text is exact, so there is no OCR and no source page images. */
    GENERATED,
}

enum class AnalysisStatus {
    QUEUED,
    EXTRACTING_TEXT,
    CLASSIFYING,

    /** The language model is reading page [Analysis.currentPage] of [Analysis.pageCount]. */
    ANALYZING_PAGES,

    /** The language model's final reasoning passes. */
    CHECKING,
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
 * @property report set only when [status] is [AnalysisStatus.COMPLETED].
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
    val report: AnalysisReport? = null,
    val error: AppError? = null,
)

/** Raw classifier output: top class, its probability, and the distribution in [Classification] order. */
data class Prediction(
    val classification: Classification,
    val probability: Float,
    val probabilities: List<Float>,
)

/**
 * OCR output: text per page in order (blank for unreadable pages).
 *
 * @property pageQuality OCR quality per page in [0, 1]; empty if not measured (older analyses).
 */
data class ExtractedText(
    val pages: List<String>,
    val unreadablePages: List<Int>,
    val pageQuality: List<Float> = emptyList(),
) {
    /** 1-based pages whose quality suggests rescanning. */
    val poorQualityPages: List<Int>
        get() = pageQuality.withIndex().filter { it.value < POOR_QUALITY }.map { it.index + 1 }

    val hasText: Boolean get() = pages.any { it.isNotBlank() }
    val fullText: String get() = pages.filter { it.isNotBlank() }.joinToString(separator = "\n\n")
}

/** Below this OCR quality a page is flagged for rescanning. */
const val POOR_QUALITY = 0.5f
