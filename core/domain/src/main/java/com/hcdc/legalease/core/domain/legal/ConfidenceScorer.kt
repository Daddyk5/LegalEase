package com.hcdc.legalease.core.domain.legal

import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ConfidenceComponents
import com.hcdc.legalease.core.domain.model.ConfidenceScore

/**
 * Turns evidence into a confidence score. The score is a weighted sum of five components, each in [0, 1]:
 *
 * | Component        | Weight | Measures |
 * |------------------|--------|----------|
 * | selfConsistency  | 0.30   | majority-label share across 3 reasoning passes at temperature 0.3 |
 * | grounding        | 0.25   | share of reasons whose quote was verified in the OCR text |
 * | agreement        | 0.15   | classifier probability for the language model's label |
 * | ocrQuality       | 0.15   | mean OCR quality across pages |
 * | completeness     | 0.15   | ½ pages processed + ½ of {consent, object, cause} located |
 *
 * Consequences, by design:
 * - Without the language model, selfConsistency is 0 and agreement is 0, so the maximum is 0.55 and the
 *   classifier alone can never produce a shown verdict (threshold 0.80).
 * - Even with perfect model evidence, poor scans or missing elements can pull a result below 0.80.
 *
 * If calibration (TESTING.md) shows shown verdicts are less than 80% accurate, tighten these weights;
 * never lower [ConfidenceScore.SHOW_THRESHOLD].
 */
object ConfidenceScorer {

    /** Bump whenever the formula or weights change, so scores from different versions are never compared. */
    const val VERSION = 2

    const val W_SELF_CONSISTENCY = 0.30f
    const val W_GROUNDING = 0.25f
    const val W_AGREEMENT = 0.15f
    const val W_OCR_QUALITY = 0.15f
    const val W_COMPLETENESS = 0.15f

    /** Inputs gathered by the analysis pipeline. */
    data class Evidence(
        /** Classifier probability for the label being scored; null if the classifier or model did not run. */
        val classifierProbabilityForLabel: Float?,
        /** Labels from each reasoning pass that produced valid output; empty without the language model. */
        val passLabels: List<Classification>,
        /** Reasoning passes attempted (failed passes count against consistency). */
        val passesAttempted: Int,
        val reasonsTotal: Int,
        val reasonsVerified: Int,
        /** OCR quality per page in [0, 1]. */
        val pageQuality: List<Float>,
        val pagesAnalyzed: Int,
        val pageCount: Int,
        val consentLocated: Boolean,
        val objectLocated: Boolean,
        val causeLocated: Boolean,
    )

    fun score(evidence: Evidence): ConfidenceScore {
        val components = components(evidence)
        val total = W_SELF_CONSISTENCY * components.selfConsistency +
            W_GROUNDING * components.grounding +
            W_AGREEMENT * components.agreement +
            W_OCR_QUALITY * components.ocrQuality +
            W_COMPLETENESS * components.completeness
        return ConfidenceScore(total, components)
    }

    fun components(e: Evidence): ConfidenceComponents {
        val majority = e.passLabels.groupingBy { it }.eachCount().maxOfOrNull { it.value } ?: 0
        val selfConsistency = if (e.passesAttempted == 0) 0f else majority.toFloat() / e.passesAttempted
        val grounding = if (e.reasonsTotal == 0) 0f else e.reasonsVerified.toFloat() / e.reasonsTotal
        val ocrQuality = if (e.pageQuality.isEmpty()) 0f else e.pageQuality.average().toFloat()
        val elements = listOf(e.consentLocated, e.objectLocated, e.causeLocated).count { it } / ELEMENTS
        val pages = if (e.pageCount == 0) 0f else e.pagesAnalyzed.toFloat() / e.pageCount
        return ConfidenceComponents(
            agreement = e.classifierProbabilityForLabel ?: 0f,
            selfConsistency = selfConsistency,
            grounding = grounding,
            ocrQuality = ocrQuality,
            completeness = HALF * pages + HALF * elements,
        )
    }

    private const val ELEMENTS = 3f
    private const val HALF = 0.5f
}
