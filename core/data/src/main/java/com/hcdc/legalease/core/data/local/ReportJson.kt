package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.legal.ConfidenceScorer
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.Citation
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ConfidenceComponents
import com.hcdc.legalease.core.domain.model.ConfidenceScore
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.Reason
import com.hcdc.legalease.core.domain.model.ReasonCode
import com.hcdc.legalease.core.domain.model.RiskFlag
import com.hcdc.legalease.core.domain.model.RiskLevel
import com.hcdc.legalease.core.domain.model.UnclearReason
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Storage format for [AnalysisReport] in the encrypted local database, including every confidence
 * component for auditability. Stays on the device: reports contain verbatim quotes.
 */
internal object ReportJson {

    /** Bump when scoring changes so older stored scores are not compared with newer ones. */
    const val SCORING_VERSION = ConfidenceScorer.VERSION

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class Dto(
        val candidate: String? = null,
        val total: Float,
        val components: Components? = null,
        val summary: String,
        val contractTitle: String? = null,
        val contractType: String,
        val reasons: List<ReasonDto>,
        val missingElements: List<String>,
        val riskFlags: List<Flag>,
        val recommendations: List<String>,
        val unclear: List<String>,
        val pageCount: Int,
        val skippedPages: List<Int>,
        val poorQualityPages: List<Int>,
        val method: String,
        val generalCitations: List<CitationDto> = emptyList(),
    )

    @Serializable
    private data class Components(
        val agreement: Float,
        val selfConsistency: Float,
        val grounding: Float,
        val ocrQuality: Float,
        val completeness: Float,
    )

    @Serializable
    private data class ReasonDto(
        val finding: String,
        val quote: String? = null,
        val page: Int? = null,
        val code: String,
        val citations: List<CitationDto>,
    )

    @Serializable
    private data class CitationDto(val article: String, val description: String)

    @Serializable
    private data class Flag(val level: String, val description: String)

    fun encode(report: AnalysisReport): String = json.encodeToString(
        Dto(
            candidate = report.candidate?.name,
            total = report.confidence.total,
            components = report.confidence.components?.let {
                Components(it.agreement, it.selfConsistency, it.grounding, it.ocrQuality, it.completeness)
            },
            summary = report.summary,
            contractTitle = report.contractTitle,
            contractType = report.contractType.name,
            reasons = report.reasons.map { r ->
                ReasonDto(
                    r.finding, r.clauseQuote, r.page, r.code.name,
                    r.citations.map { CitationDto(it.article, it.description) },
                )
            },
            missingElements = report.missingElements,
            riskFlags = report.riskFlags.map { Flag(it.level.name, it.description) },
            recommendations = report.recommendations,
            unclear = report.unclear.map { it.name },
            pageCount = report.pageCount,
            skippedPages = report.skippedPages,
            poorQualityPages = report.poorQualityPages,
            method = report.method.name,
            generalCitations = report.generalCitations.map { CitationDto(it.article, it.description) },
        )
    )

    /** Returns null if the stored JSON cannot be read (it is then shown as an error, never guessed). */
    fun decode(raw: String): AnalysisReport? = try {
        val dto = json.decodeFromString<Dto>(raw)
        AnalysisReport(
            candidate = dto.candidate?.let { enumValueOf<Classification>(it) },
            confidence = ConfidenceScore(
                dto.total,
                dto.components?.let {
                    ConfidenceComponents(it.agreement, it.selfConsistency, it.grounding, it.ocrQuality, it.completeness)
                },
            ),
            summary = dto.summary,
            contractTitle = dto.contractTitle,
            contractType = enumValueOf<DetectedContractType>(dto.contractType),
            reasons = dto.reasons.map { r ->
                Reason(
                    r.finding, r.quote, r.page, enumValueOf<ReasonCode>(r.code),
                    r.citations.map { Citation(it.article, it.description) },
                )
            },
            missingElements = dto.missingElements,
            riskFlags = dto.riskFlags.map { RiskFlag(enumValueOf<RiskLevel>(it.level), it.description) },
            recommendations = dto.recommendations,
            unclear = dto.unclear.mapNotNull { name -> UnclearReason.entries.firstOrNull { it.name == name } },
            pageCount = dto.pageCount,
            skippedPages = dto.skippedPages,
            poorQualityPages = dto.poorQualityPages,
            method = enumValueOf<AnalysisMethod>(dto.method),
            generalCitations = dto.generalCitations.map { Citation(it.article, it.description) },
        )
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
}
