package com.hcdc.legalease.core.domain.legal

import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.LlmReasoning
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.ConfidenceScore
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.model.Reason
import com.hcdc.legalease.core.domain.model.RiskFlag
import com.hcdc.legalease.core.domain.model.UnclearReason

/**
 * Assembles an [AnalysisReport] from pipeline outputs. Pure and deterministic, so every rule that
 * affects what the user sees (grounding, citations, scoring, inconclusive reasons) is unit-tested.
 */
object ReportBuilder {

    const val MAX_SUMMARY_WORDS = 120
    private const val DISAGREEMENT_THRESHOLD = 0.5f

    // Small models must not cite law; any article reference they write is removed.
    private val ARTICLE_REFERENCE = Regex(
        """(?i)\(?\b(arts?|articles?)\.?\s*\d+(\s*\(\d+\))?(\s*[–\-]\s*\d+)?""" +
            """(\s*(,|and)\s*\d+(\s*\(\d+\))?)*\)?(\s+of\b)?"""
    )

    /** Report from the language model path. [passes] are the successful reasoning passes. */
    @Suppress("LongParameterList") // One parameter per pipeline stage output.
    fun fromLanguageModel(
        text: ExtractedText,
        prediction: Prediction?,
        facts: List<ChunkFacts>,
        passes: List<LlmReasoning>,
        passesAttempted: Int,
        skippedPages: List<Int>,
    ): AnalysisReport {
        require(passes.isNotEmpty()) { "At least one successful reasoning pass is required" }
        val majority = passes.groupingBy { it.classification }.eachCount().maxBy { it.value }.key
        val chosen = passes.first { it.classification == majority }
        val skipped = (skippedPages + chosen.omittedPages).distinct().sorted()

        var verified = 0
        val reasons = chosen.reasons.map { reason ->
            val match = reason.clauseQuote?.let { QuoteVerifier.verify(it, text.pages, reason.page) }
            if (match != null) verified++
            Reason(
                finding = stripArticles(reason.finding),
                clauseQuote = reason.clauseQuote?.takeIf { match != null },
                page = match?.page,
                code = reason.code,
                citations = CivilCodeCitations.forReason(reason.code),
            )
        }
        val title = chosen.contractName?.takeIf { QuoteVerifier.verify(it, text.pages, null) != null }

        val score = ConfidenceScorer.score(
            ConfidenceScorer.Evidence(
                classifierProbabilityForLabel = prediction?.probabilities?.getOrNull(majority.ordinal),
                passLabels = passes.map { it.classification },
                passesAttempted = passesAttempted,
                reasonsTotal = reasons.size,
                reasonsVerified = verified,
                pageQuality = text.pageQuality,
                pagesAnalyzed = text.pages.size - skipped.size,
                pageCount = text.pages.size,
                consentLocated = facts.any { it.parties.isNotEmpty() } &&
                    facts.any { it.signatureOrNotarization.isNotEmpty() },
                objectLocated = facts.any { !it.objectDescription.isNullOrBlank() },
                causeLocated = facts.any { !it.consideration.isNullOrBlank() },
            )
        )
        val missing = chosen.missingElements.map(::stripArticles).filter { it.isNotBlank() }
        return AnalysisReport(
            candidate = majority,
            confidence = score,
            summary = limitWords(stripArticles(chosen.summary), MAX_SUMMARY_WORDS),
            contractTitle = title,
            contractType = chosen.contractType,
            reasons = reasons,
            missingElements = missing,
            riskFlags = chosen.riskFlags.map { (level, description) -> RiskFlag(level, stripArticles(description)) },
            recommendations = chosen.recommendations.map(::stripArticles).filter { it.isNotBlank() },
            unclear = unclear(text, skipped, missing) + modelUnclear(score, hasClassifier = prediction != null),
            pageCount = text.pages.size,
            skippedPages = skipped,
            poorQualityPages = text.poorQualityPages,
            method = AnalysisMethod.LANGUAGE_MODEL,
            generalCitations = listOf(CivilCodeCitations.forClassification(majority)) +
                CivilCodeCitations.forContractType(chosen.contractType),
        )
    }

    /** Report from the rule-based fallback. It cannot reach the display threshold by construction. */
    fun fromRules(text: ExtractedText, prediction: Prediction?): AnalysisReport {
        val rules = RuleBasedExplainer.explain(text.pages)
        val reasons = rules.findings.map {
            Reason(it.finding, it.quote, it.page, it.code, CivilCodeCitations.forReason(it.code))
        }
        val score = ConfidenceScorer.score(
            ConfidenceScorer.Evidence(
                classifierProbabilityForLabel = null,
                passLabels = emptyList(),
                passesAttempted = 0,
                reasonsTotal = reasons.size,
                reasonsVerified = reasons.count { it.clauseQuote != null },
                pageQuality = text.pageQuality,
                pagesAnalyzed = text.pages.size,
                pageCount = text.pages.size,
                consentLocated = rules.consentLocated,
                objectLocated = rules.objectLocated,
                causeLocated = rules.causeLocated,
            )
        )
        return AnalysisReport(
            candidate = prediction?.classification,
            confidence = score,
            summary = "Basic checks only: LegalEase read ${text.pages.size - text.unreadablePages.size} of " +
                "${text.pages.size} pages and looked for the essential elements of a contract (consent, " +
                "object, and cause). A full explanation needs offline analysis, which isn't available right now.",
            contractTitle = null,
            contractType = rules.contractType,
            reasons = reasons,
            missingElements = rules.missingElements,
            riskFlags = emptyList(),
            recommendations = emptyList(),
            unclear = unclear(text, emptyList(), rules.missingElements) + UnclearReason.LIMITED_ANALYSIS,
            pageCount = text.pages.size,
            skippedPages = emptyList(),
            poorQualityPages = text.poorQualityPages,
            method = AnalysisMethod.CLASSIFIER_ONLY,
            generalCitations = CivilCodeCitations.forContractType(rules.contractType),
        )
    }

    /** Reasons about the document itself, shared by both paths. */
    private fun unclear(text: ExtractedText, skippedPages: List<Int>, missing: List<String>): List<UnclearReason> =
        buildList {
            if (text.unreadablePages.isNotEmpty()) add(UnclearReason.UNREADABLE_PAGES)
            if (text.poorQualityPages.isNotEmpty()) add(UnclearReason.POOR_SCAN_QUALITY)
            if (skippedPages.isNotEmpty()) add(UnclearReason.SKIPPED_PAGES)
            if (missing.isNotEmpty()) add(UnclearReason.MISSING_ELEMENTS)
        }

    /** Reasons from the language model's own checks. */
    private fun modelUnclear(score: ConfidenceScore, hasClassifier: Boolean): List<UnclearReason> = buildList {
        val c = score.components ?: return@buildList
        if (c.selfConsistency < 1f) add(UnclearReason.CONFLICTING_RESULTS)
        if (c.grounding < 1f) add(UnclearReason.UNVERIFIED_QUOTES)
        if (hasClassifier && c.agreement < DISAGREEMENT_THRESHOLD) add(UnclearReason.MODEL_DISAGREEMENT)
    }

    internal fun stripArticles(text: String): String =
        text.replace(ARTICLE_REFERENCE, "").replace(Regex("\\s{2,}"), " ").replace(Regex("\\s+([.,;:])"), "$1").trim()

    internal fun limitWords(text: String, max: Int): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return if (words.size <= max) text.trim() else words.take(max).joinToString(" ") + "…"
    }
}
