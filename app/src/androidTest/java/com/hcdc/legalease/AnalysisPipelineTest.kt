package com.hcdc.legalease

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalysisRepository
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeAuditLog
import com.hcdc.legalease.core.domain.testing.FakeClassifier
import com.hcdc.legalease.core.domain.testing.FakeFeatureFlags
import com.hcdc.legalease.core.domain.testing.FakeIntegrityChecker
import com.hcdc.legalease.core.domain.testing.FakeLlmAnalyzer
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.FakeTracer
import com.hcdc.legalease.core.domain.usecase.AnalyzeContractUseCase
import com.hcdc.legalease.core.ml.MlKitTextExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Clock

/**
 * A 3-page contract goes through the real pipeline on a device: PdfRenderer, preprocessing, and
 * ML Kit OCR, then analysis. The classifier and the on-device LLM are faked as unavailable because
 * their models are delivered at runtime; the analysis then falls back to the rule-based report,
 * which is what a fresh offline install produces.
 */
@RunWith(AndroidJUnit4::class)
class AnalysisPipelineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aThreePagePdfIsReadAndAnalyzedOffline() = runBlocking {
        val pdf = writeSamplePdf(File(context.cacheDir, "sample-lease.pdf"))
        val repository = FakeAnalysisRepository()
        val id = (repository.create("sample-lease.pdf", SourceKind.PDF, listOf(pdf.path)) as AppResult.Success).value
        val analyze = AnalyzeContractUseCase(
            repository = repository,
            extractor = MlKitTextExtractor(Dispatchers.IO, FakeTracer()),
            classifier = FakeClassifier(AppResult.Failure(AppError.ModelUnavailable)),
            llm = FakeLlmAnalyzer(availability = LlmAvailability.UNAVAILABLE),
            featureFlags = FakeFeatureFlags(),
            syncScheduler = FakeSyncScheduler(),
            tracer = FakeTracer(),
            analytics = FakeAnalytics(),
            clock = Clock.systemUTC(),
            auditLog = FakeAuditLog(),
            integrity = FakeIntegrityChecker(),
        )

        val result = analyze(id)

        val report = (result as? AppResult.Success)?.value
        assertTrue("analysis failed: $result", report != null)
        assertEquals(PAGES.size, report!!.pageCount)
        assertEquals(AnalysisMethod.CLASSIFIER_ONLY, report.method)
        val text = repository.extractedText(id)!!
        assertTrue("OCR missed the title", text.fullText.contains("CONTRACT OF LEASE", ignoreCase = true))
        pdf.delete()
    }

    private fun writeSamplePdf(target: File): File {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = TEXT_SIZE
            isAntiAlias = true
        }
        val document = PdfDocument()
        PAGES.forEachIndexed { index, lines ->
            val page = document.startPage(PdfDocument.PageInfo.Builder(A4_WIDTH, A4_HEIGHT, index + 1).create())
            page.canvas.drawColor(Color.WHITE)
            lines.forEachIndexed { line, text -> page.canvas.drawText(text, MARGIN, MARGIN + line * LINE_HEIGHT, paint) }
            document.finishPage(page)
        }
        target.outputStream().use(document::writeTo)
        document.close()
        return target
    }

    private companion object {
        const val A4_WIDTH = 595
        const val A4_HEIGHT = 842
        const val MARGIN = 48f
        const val TEXT_SIZE = 14f
        const val LINE_HEIGHT = 24f
        val PAGES = listOf(
            listOf(
                "CONTRACT OF LEASE",
                "This Contract of Lease is made in Davao City by and between",
                "Juan dela Cruz, of legal age, Filipino, the LESSOR, and",
                "Maria Santos, of legal age, Filipino, the LESSEE.",
            ),
            listOf(
                "1. The LESSOR leases to the LESSEE the house at 12 Rizal Street.",
                "2. The monthly rent is FIFTEEN THOUSAND PESOS (PHP 15,000.00).",
                "3. The lease runs for one year from November 1, 2026.",
            ),
            listOf(
                "IN WITNESS WHEREOF, the parties sign this contract.",
                "Juan dela Cruz, LESSOR          Maria Santos, LESSEE",
                "Signed in the presence of: Pedro Reyes and Ana Lim",
            ),
        )
    }
}
