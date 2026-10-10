package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.engine.LlmReason
import com.hcdc.legalease.core.domain.engine.LlmReasoning
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.model.ReasonCode
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.UnclearReason
import com.hcdc.legalease.core.domain.repository.FeatureFlags
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeClassifier
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.FakeLlmAnalyzer
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.FakeTextExtractor
import com.hcdc.legalease.core.domain.testing.FakeTracer
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class AnalyzeContractUseCaseTest {

    private val pages = listOf(
        "CONTRACT OF LEASE. The Lessor leases to the Lessee the house located at 12 Rizal St, Davao City.",
        "The monthly rent is PHP 15,000. The Lessee may be evicted without notice at any time.",
        "IN WITNESS WHEREOF the parties signed this contract. Notary Public.",
    )
    private val repository = FakeAnalysisRepository()
    private val extractor = FakeTextExtractor(
        AppResult.Success(
            ExtractedText(pages, unreadablePages = emptyList(), pageQuality = listOf(0.95f, 0.95f, 0.95f))
        )
    )
    private val classifier = FakeClassifier(
        AppResult.Success(Prediction(Classification.VOIDABLE, 0.9f, listOf(0.02f, 0.9f, 0.04f, 0.02f, 0.02f)))
    )
    private val llm = FakeLlmAnalyzer(facts = { chunk ->
        AppResult.Success(
            ChunkFacts(
                page = chunk.page,
                parties = if (chunk.page == 1) listOf("Lessor", "Lessee") else emptyList(),
                objectDescription = if (chunk.page == 1) "house at 12 Rizal St" else null,
                consideration = if (chunk.page == 2) "PHP 15,000 monthly" else null,
                term = null,
                signatureOrNotarization = if (chunk.page == 3) listOf("signed", "Notary Public") else emptyList(),
                unusualClauses = emptyList(),
            )
        )
    })
    private val flags = FakeFeatureFlags()
    private val sync = FakeSyncScheduler()
    private val tracer = FakeTracer()
    private val analytics = FakeAnalytics()
    private val analyze = AnalyzeContractUseCase(
        repository, extractor, classifier, llm, flags, sync, tracer, analytics,
        Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
    )

    private fun reasoning(
        label: Classification,
        quote: String? = "The Lessee may be evicted without notice at any time",
    ) = AppResult.Success(
        LlmReasoning(
            contractName = "CONTRACT OF LEASE",
            contractType = DetectedContractType.LEASE,
            classification = label,
            summary = "A residential lease that lets the lessor evict without notice (Art. 1673).",
            reasons = listOf(
                LlmReason("Eviction without notice may be one-sided.", quote, 2, ReasonCode.VITIATED_CONSENT),
            ),
            missingElements = emptyList(),
            riskFlags = emptyList(),
            recommendations = listOf("Ask a lawyer to review the eviction clause."),
        )
    )

    private suspend fun queued(): String =
        (repository.create("lease.pdf", SourceKind.PDF, listOf("a", "b", "c")) as AppResult.Success).value

    @Test
    fun `consistent, grounded, agreeing evidence produces a shown verdict`() = runTest {
        repeat(3) { llm.reasoning += reasoning(Classification.VOIDABLE) }
        val id = queued()

        val report = (analyze(id) as AppResult.Success).value

        assertEquals(AnalysisMethod.LANGUAGE_MODEL, report.method)
        assertEquals(Classification.VOIDABLE, report.shownClassification)
        assertTrue("score ${report.confidence.total}", report.confidence.total >= 0.8f)
        assertEquals(1f, report.confidence.components!!.selfConsistency)
        assertEquals(1f, report.confidence.components!!.grounding)
        assertEquals("CONTRACT OF LEASE", report.contractTitle)
        assertEquals(AnalysisStatus.COMPLETED, repository.get(id)?.status)
        assertEquals(1, llm.released)
    }

    @Test
    fun `the model never cites articles, the app attaches them`() = runTest {
        repeat(3) { llm.reasoning += reasoning(Classification.VOIDABLE) }

        val report = (analyze(queued()) as AppResult.Success).value

        assertEquals("A residential lease that lets the lessor evict without notice.", report.summary)
        assertEquals(listOf("Arts. 1390–1402"), report.reasons.single().citations.map { it.article })
    }

    @Test
    fun `a fabricated quote is dropped and lowers the score`() = runTest {
        val waiver = reasoning(Classification.VOIDABLE, quote = "The lessee waives all rights forever")
        repeat(3) { llm.reasoning += waiver }

        val report = (analyze(queued()) as AppResult.Success).value

        assertNull(report.reasons.single().clauseQuote)
        assertEquals(0f, report.confidence.components!!.grounding)
        assertTrue(UnclearReason.UNVERIFIED_QUOTES in report.unclear)
        assertNull("below threshold must not show a verdict", report.shownClassification)
    }

    @Test
    fun `disagreeing passes are inconclusive`() = runTest {
        llm.reasoning += reasoning(Classification.VOIDABLE)
        llm.reasoning += reasoning(Classification.VOID)
        llm.reasoning += reasoning(Classification.ENFORCEABLE)

        val report = (analyze(queued()) as AppResult.Success).value

        assertTrue(UnclearReason.CONFLICTING_RESULTS in report.unclear)
        assertNull(report.shownClassification)
    }

    @Test
    fun `reasoning runs three times at temperature 0_3`() = runTest {
        repeat(3) { llm.reasoning += reasoning(Classification.VOIDABLE) }
        analyze(queued())
        assertEquals(listOf(0.3f, 0.3f, 0.3f), llm.reasoningTemperatures)
    }

    @Test
    fun `a page whose extraction fails is reported as skipped`() = runTest {
        repeat(3) { llm.reasoning += reasoning(Classification.VOIDABLE) }
        val working = llm.facts
        llm.facts = { if (it.page == 2) AppResult.Failure(AppError.Unexpected("timeout")) else working(it) }

        val report = (analyze(queued()) as AppResult.Success).value

        assertEquals(listOf(2), report.skippedPages)
        assertTrue(UnclearReason.SKIPPED_PAGES in report.unclear)
    }

    @Test
    fun `without the language model the rule-based fallback is always inconclusive`() = runTest {
        llm.availability = LlmAvailability.PREPARING

        val report = (analyze(queued()) as AppResult.Success).value

        assertEquals(AnalysisMethod.CLASSIFIER_ONLY, report.method)
        assertEquals(Classification.VOIDABLE, report.candidate)
        assertNull(report.shownClassification)
        assertTrue(UnclearReason.LIMITED_ANALYSIS in report.unclear)
        assertEquals(0, llm.released)
    }

    @Test
    fun `the remote kill switch turns the language model off`() = runTest {
        flags.state.value = FeatureFlags.DEFAULT.copy(llmEnabled = false)
        repeat(3) { llm.reasoning += reasoning(Classification.VOIDABLE) }

        val report = (analyze(queued()) as AppResult.Success).value

        assertEquals(AnalysisMethod.CLASSIFIER_ONLY, report.method)
    }

    @Test
    fun `all reasoning passes failing falls back instead of failing the analysis`() = runTest {
        val report = (analyze(queued()) as AppResult.Success).value
        assertEquals(AnalysisMethod.CLASSIFIER_ONLY, report.method)
        assertEquals(1, llm.released)
    }

    @Test
    fun `a missing classifier does not block the analysis`() = runTest {
        classifier.result = AppResult.Failure(AppError.ModelUnavailable)
        repeat(3) { llm.reasoning += reasoning(Classification.VOIDABLE) }

        val report = (analyze(queued()) as AppResult.Success).value

        assertEquals(0f, report.confidence.components!!.agreement)
        assertEquals(Classification.VOIDABLE, report.candidate)
    }

    @Test
    fun `reuses saved text after a restart instead of reading pages again`() = runTest {
        val id = queued()
        repository.saveExtractedText(id, ExtractedText(listOf("Saved text"), emptyList()))

        analyze(id)

        assertEquals(0, extractor.calls)
        assertEquals(listOf("Saved text"), classifier.classifiedTexts)
    }

    @Test
    fun `document with no text fails without classifying`() = runTest {
        extractor.result = AppResult.Success(ExtractedText(listOf(" ", ""), listOf(0, 1)))
        val id = queued()

        assertEquals(AppResult.Failure(AppError.NoTextFound), analyze(id))
        assertEquals(AppError.NoTextFound, repository.get(id)?.error)
        assertTrue(classifier.classifiedTexts.isEmpty())
    }

    @Test
    fun `unreadable file is recorded as a failure`() = runTest {
        extractor.result = AppResult.Failure(AppError.UnreadableFile)
        val id = queued()

        analyze(id)

        assertEquals(AnalysisStatus.FAILED, repository.get(id)?.status)
        assertNull(repository.get(id)?.report)
    }

    @Test
    fun `analytics carry only labels, bands, and counts`() = runTest {
        llm.availability = LlmAvailability.UNAVAILABLE
        analyze(queued())

        assertEquals(
            mapOf(
                "classification" to "INCONCLUSIVE", "confidence_band" to "inconclusive",
                "method" to "classifier_only", "page_count" to 3L, "duration_ms" to 0L,
            ),
            analytics.events.single().params,
        )
    }
}
