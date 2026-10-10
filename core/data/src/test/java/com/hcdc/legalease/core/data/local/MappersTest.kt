package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.AnalysisStatus
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
import com.hcdc.legalease.core.domain.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    private val report = AnalysisReport(
        candidate = Classification.VOIDABLE,
        confidence = ConfidenceScore(0.86f, ConfidenceComponents(0.9f, 1f, 0.5f, 0.8f, 1f)),
        summary = "A lease.",
        contractTitle = "CONTRACT OF LEASE",
        contractType = DetectedContractType.LEASE,
        reasons = listOf(
            Reason("One-sided eviction", "may be evicted without notice", 2, ReasonCode.VITIATED_CONSENT,
                listOf(Citation("Arts. 1390–1402", "Voidable contracts."))),
        ),
        missingElements = listOf("Term"),
        riskFlags = listOf(RiskFlag(RiskLevel.HIGH, "Eviction without notice")),
        recommendations = listOf("Consult a lawyer."),
        unclear = listOf(UnclearReason.UNVERIFIED_QUOTES),
        pageCount = 3,
        skippedPages = emptyList(),
        poorQualityPages = listOf(3),
        method = AnalysisMethod.LANGUAGE_MODEL,
        generalCitations = listOf(Citation("Arts. 1642–1688", "Lease.")),
    )

    private val base = AnalysisEntity(
        id = "a1", documentName = "lease.pdf", sourceKind = "PDF", status = "COMPLETED",
        createdAt = 1, updatedAt = 2, pageCount = 3, unreadablePages = "1,2",
        classification = "Voidable", confidence = 0.86f, summary = "A lease.",
        reportJson = ReportJson.encode(report), contractType = "LEASE", scoringVersion = ReportJson.SCORING_VERSION,
    )

    @Test
    fun `the full report round-trips through storage, components included`() {
        assertEquals(report, ReportJson.decode(ReportJson.encode(report)))
        assertEquals(report, base.toDomain().report)
    }

    @Test
    fun `a corrupt stored report is shown as an error, never guessed`() {
        val analysis = base.copy(reportJson = "{broken").toDomain()
        assertEquals(AnalysisStatus.FAILED, analysis.status)
        assertNull(analysis.report)
        assertEquals(AppError.Unexpected("unreadable_report"), analysis.error)
    }

    @Test
    fun `a synced result without the local report keeps only the total score`() {
        val synced = base.copy(reportJson = null).toDomain().report!!
        assertEquals(0.86f, synced.confidence.total)
        assertNull(synced.confidence.components)
        assertEquals(Classification.VOIDABLE, synced.shownClassification)
    }

    @Test
    fun `results from earlier versions are legacy and never shown as a verdict`() {
        val legacy = base.copy(reportJson = null, scoringVersion = null, confidence = 0.97f).toDomain().report!!
        assertEquals(AnalysisMethod.LEGACY, legacy.method)
        assertNull(legacy.shownClassification)
    }

    @Test
    fun `failed row carries its error`() {
        val analysis = base.copy(status = "FAILED", reportJson = null, errorCode = "no_text_found").toDomain()
        assertEquals(AppError.NoTextFound, analysis.error)
    }

    @Test
    fun `page lists round-trip and tolerate blanks`() {
        assertEquals(listOf(0, 4), decodePageList(encodePageList(listOf(0, 4))))
        assertEquals(emptyList<Int>(), decodePageList(""))
    }

    @Test
    fun `like wildcards are escaped`() {
        assertEquals("100\\% \\_draft\\\\", escapeLike("100% _draft\\"))
    }
}
