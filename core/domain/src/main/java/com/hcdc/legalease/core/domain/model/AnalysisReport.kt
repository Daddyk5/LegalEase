package com.hcdc.legalease.core.domain.model

/** Why a clause matters legally. The app, not the model, maps each code to Civil Code articles. */
enum class ReasonCode {
    NO_CONSENT, NO_OBJECT, NO_CAUSE, FORM_NOT_MET, STATUTE_OF_FRAUDS, LESION, VITIATED_CONSENT,
    INCAPACITY, ILLEGAL_OBJECT, SIMULATED, FRAUD_ON_CREDITORS, NONE,
}

enum class DetectedContractType { LEASE, LOAN, SALE, LEASE_OF_PERSONAL_PROPERTY, OTHER }

enum class RiskLevel { HIGH, MEDIUM, LOW }

/** A Civil Code reference attached by the app's lookup table. */
data class Citation(val article: String, val description: String)

/**
 * One reason supporting the classification.
 *
 * @property clauseQuote verbatim text from the document, or null if none could be verified against the OCR text.
 * @property page 1-based page the quote was found on.
 */
data class Reason(
    val finding: String,
    val clauseQuote: String?,
    val page: Int?,
    val code: ReasonCode,
    val citations: List<Citation>,
)

data class RiskFlag(val level: RiskLevel, val description: String)

enum class ConfidenceBand { HIGH, CONFIDENT, INCONCLUSIVE }

/** Evidence behind a confidence score, each in [0, 1]. Stored with the result for auditability. */
data class ConfidenceComponents(
    /** Classifier probability for the language model's label; 0 when either did not run. */
    val agreement: Float,
    /** Share of repeated reasoning passes that reached the majority label. */
    val selfConsistency: Float,
    /** Share of reasons whose quote was found in the OCR text. */
    val grounding: Float,
    /** Mean OCR quality across pages. */
    val ocrQuality: Float,
    /** Pages processed and essential elements (consent, object, cause) located. */
    val completeness: Float,
)

/**
 * @property components null when the score came from another device (only the total is synced).
 */
data class ConfidenceScore(val total: Float, val components: ConfidenceComponents?) {
    val band: ConfidenceBand
        get() = when {
            total >= HIGH_THRESHOLD -> ConfidenceBand.HIGH
            total >= SHOW_THRESHOLD -> ConfidenceBand.CONFIDENT
            else -> ConfidenceBand.INCONCLUSIVE
        }

    companion object {
        /** A classification is shown only at or above this score. Tighten weights, never this display rule. */
        const val SHOW_THRESHOLD = 0.80f
        const val HIGH_THRESHOLD = 0.90f
    }
}

/** What made an analysis less certain. Shown to the user when the result is inconclusive. */
enum class UnclearReason {
    UNREADABLE_PAGES,
    POOR_SCAN_QUALITY,
    SKIPPED_PAGES,
    MISSING_ELEMENTS,
    CONFLICTING_RESULTS,
    UNVERIFIED_QUOTES,
    MODEL_DISAGREEMENT,
    LIMITED_ANALYSIS,
}

enum class AnalysisMethod {
    LANGUAGE_MODEL,
    CLASSIFIER_ONLY,

    /** Analysed by an earlier app version whose scores are not comparable; never shown as a verdict. */
    LEGACY,
}

/**
 * The full result of analysing one contract.
 *
 * @property candidate the best-supported classification; shown only via [shownClassification].
 * @property contractTitle the document's own title, only if it was found in the OCR text.
 * @property skippedPages 1-based pages that could not be analysed (never dropped silently).
 * @property poorQualityPages 1-based pages whose OCR quality is low enough to suggest a rescan.
 */
data class AnalysisReport(
    val candidate: Classification?,
    val confidence: ConfidenceScore,
    val summary: String,
    val contractTitle: String?,
    val contractType: DetectedContractType,
    val reasons: List<Reason>,
    val missingElements: List<String>,
    val riskFlags: List<RiskFlag>,
    val recommendations: List<String>,
    val unclear: List<UnclearReason>,
    val pageCount: Int,
    val skippedPages: List<Int>,
    val poorQualityPages: List<Int>,
    val method: AnalysisMethod,
    /** Civil Code context for the classification and contract type, attached by the app. */
    val generalCitations: List<Citation> = emptyList(),
) {
    /** The verdict the user may see: null unless confidence is at least [ConfidenceScore.SHOW_THRESHOLD]. */
    val shownClassification: Classification?
        get() = candidate?.takeIf { method != AnalysisMethod.LEGACY && confidence.band != ConfidenceBand.INCONCLUSIVE }
}
