package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeClassifier
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.FakeTextExtractor
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeTracer
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyzeContractUseCaseTest {

    private val repository = FakeAnalysisRepository()
    private val prediction = Prediction(Classification.VOIDABLE, 0.83f, listOf(0.05f, 0.83f, 0.04f, 0.04f, 0.04f))
    private val threePages = ExtractedText(listOf("Page one", "", "Page three"), unreadablePages = listOf(1))
    private val extractor = FakeTextExtractor(AppResult.Success(threePages))
    private val classifier = FakeClassifier(AppResult.Success(prediction))
    private val sync = FakeSyncScheduler()
    private val tracer = FakeTracer()
    private val analytics = FakeAnalytics()
    private val analyze = AnalyzeContractUseCase(
        repository, extractor, classifier, sync, tracer, analytics, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
    )

    private suspend fun queued(pages: Int = 3): String {
        val id = (repository.create("lease.pdf", SourceKind.PDF, List(pages) { "file$it" }) as AppResult.Success).value
        return id
    }

    @Test
    fun `reads all pages, classifies the joined text, and stores the model's own confidence`() = runTest {
        val id = queued()
        val progress = mutableListOf<AnalysisProgress>()

        val result = analyze(id) { progress += it }

        val stored = repository.get(id)!!
        assertEquals(AnalysisStatus.COMPLETED, stored.status)
        assertEquals(Classification.VOIDABLE, stored.verdict?.classification)
        assertEquals(0.83f, stored.verdict?.confidence)
        assertEquals(listOf("Page one\n\nPage three"), classifier.classifiedTexts)
        assertEquals(
            listOf(
                AnalysisProgress.ReadingPage(1, 3), AnalysisProgress.ReadingPage(2, 3),
                AnalysisProgress.ReadingPage(3, 3), AnalysisProgress.Classifying,
            ),
            progress,
        )
        assertEquals(stored.verdict, (result as AppResult.Success).value)
        assertEquals(1, sync.requests)
    }

    @Test
    fun `source copies are discarded once text is saved`() = runTest {
        val id = queued()
        analyze(id)
        assertEquals(emptyList<String>(), repository.sourcePaths(id))
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
        val id = queued(pages = 2)

        val result = analyze(id)

        assertEquals(AppResult.Failure(AppError.NoTextFound), result)
        assertEquals(AppError.NoTextFound, repository.get(id)?.error)
        assertEquals(emptyList<String>(), classifier.classifiedTexts)
    }

    @Test
    fun `unreadable file is recorded as a failure`() = runTest {
        extractor.result = AppResult.Failure(AppError.UnreadableFile)
        val id = queued()

        analyze(id)

        val stored = repository.get(id)!!
        assertEquals(AnalysisStatus.FAILED, stored.status)
        assertEquals(AppError.UnreadableFile, stored.error)
        assertNull(stored.verdict)
    }

    @Test
    fun `classifier failure is recorded, never replaced with a guessed verdict`() = runTest {
        classifier.result = AppResult.Failure(AppError.ModelUnavailable)
        val id = queued()

        val result = analyze(id)

        assertEquals(AppResult.Failure(AppError.ModelUnavailable), result)
        assertNull(repository.get(id)?.verdict)
    }

    @Test
    fun `unknown analysis id fails`() = runTest {
        assertEquals(AppResult.Failure(AppError.DocumentNotFound), analyze("missing"))
    }

    @Test
    fun `traces the run and logs only labels and counts`() = runTest {
        analyze(queued())

        assertEquals(listOf("analysis_end_to_end", "classify"), tracer.started.sorted())
        assertEquals(tracer.started.sorted(), tracer.stopped.sorted())
        val event = analytics.events.single()
        assertEquals("analysis_completed", event.name)
        assertEquals(
            mapOf(
                "classification" to "VOIDABLE", "confidence_band" to "confident",
                "page_count" to 3L, "duration_ms" to 0L,
            ),
            event.params,
        )
    }

    @Test
    fun `failures are logged by error code only`() = runTest {
        classifier.result = AppResult.Failure(AppError.ModelIncompatible)
        analyze(queued())
        assertEquals(mapOf("error_code" to "model_incompatible"), analytics.events.single().params)
        assertEquals("failure", tracer.attributes["outcome"])
    }
}
