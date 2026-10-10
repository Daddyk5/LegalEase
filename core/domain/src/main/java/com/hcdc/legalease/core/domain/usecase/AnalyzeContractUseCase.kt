package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.engine.TextExtractor
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.Verdict
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.telemetry.AnalyticsEvent
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import com.hcdc.legalease.core.domain.telemetry.PerformanceTracer
import com.hcdc.legalease.core.domain.telemetry.TraceNames
import com.hcdc.legalease.core.domain.telemetry.trace
import java.time.Clock
import javax.inject.Inject

/** Progress reported while [AnalyzeContractUseCase] runs. */
sealed interface AnalysisProgress {
    data class ReadingPage(val page: Int, val pageCount: Int) : AnalysisProgress
    data object Classifying : AnalysisProgress
}

/**
 * The full offline pipeline for one queued analysis: OCR every page, then classify.
 *
 * Idempotent across restarts: if the background worker is stopped after OCR finished,
 * the saved text is reused instead of reading the pages again.
 */
class AnalyzeContractUseCase @Inject constructor(
    private val repository: AnalysisRepository,
    private val extractor: TextExtractor,
    private val classifier: ContractClassifier,
    private val syncScheduler: SyncScheduler,
    private val tracer: PerformanceTracer,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        analysisId: String,
        onProgress: suspend (AnalysisProgress) -> Unit = {},
    ): AppResult<Verdict> = tracer.trace(TraceNames.ANALYSIS_END_TO_END) { trace ->
        val startedAt = clock.millis()
        val result = run(analysisId, onProgress)
        trace.putAttribute("outcome", if (result is AppResult.Success) "success" else "failure")
        when (result) {
            is AppResult.Success -> {
                val pageCount = repository.extractedText(analysisId)?.pages?.size ?: 0
                trace.putMetric("page_count", pageCount.toLong())
                analytics.log(
                    AnalyticsEvent.AnalysisCompleted(
                        classification = result.value.classification,
                        confidenceBand = AnalyticsEvent.confidenceBand(result.value.confidence),
                        pageCount = pageCount,
                        durationMs = clock.millis() - startedAt,
                    )
                )
            }
            is AppResult.Failure -> analytics.log(AnalyticsEvent.AnalysisFailed(result.error.code))
        }
        result
    }

    private suspend fun run(analysisId: String, onProgress: suspend (AnalysisProgress) -> Unit): AppResult<Verdict> {
        val analysis = repository.get(analysisId) ?: return AppResult.Failure(AppError.DocumentNotFound)

        val text = repository.extractedText(analysisId)
            ?: when (val extracted = extract(analysisId, analysis.sourceKind, onProgress)) {
                is AppResult.Failure -> return fail(analysisId, extracted.error)
                is AppResult.Success -> extracted.value
            }
        if (!text.hasText) return fail(analysisId, AppError.NoTextFound)

        repository.markClassifying(analysisId)
        onProgress(AnalysisProgress.Classifying)
        val fullText = text.fullText
        val classified = tracer.trace(TraceNames.CLASSIFY) { classifier.classify(fullText) }
        return when (val prediction = classified) {
            is AppResult.Failure -> fail(analysisId, prediction.error)
            is AppResult.Success -> {
                val verdict = Verdict(
                    classification = prediction.value.classification,
                    confidence = prediction.value.probability,
                    summary = fullText.take(SUMMARY_PREVIEW_CHARS),
                )
                repository.complete(analysisId, verdict)
                syncScheduler.requestSync()
                AppResult.Success(verdict)
            }
        }
    }

    private suspend fun extract(
        analysisId: String,
        kind: SourceKind,
        onProgress: suspend (AnalysisProgress) -> Unit,
    ): AppResult<ExtractedText> {
        val paths = repository.sourcePaths(analysisId)
        if (paths.isEmpty()) return AppResult.Failure(AppError.DocumentNotFound)
        val result = extractor.extract(kind, paths) { page, pageCount ->
            repository.markExtracting(analysisId, page, pageCount)
            onProgress(AnalysisProgress.ReadingPage(page, pageCount))
        }
        if (result is AppResult.Success) repository.saveExtractedText(analysisId, result.value)
        return result
    }

    private suspend fun fail(analysisId: String, error: AppError): AppResult.Failure {
        repository.fail(analysisId, error)
        syncScheduler.requestSync()
        return AppResult.Failure(error)
    }

    private companion object {
        /** Until on-device summarisation lands, the "summary" is the opening of the OCR text. */
        const val SUMMARY_PREVIEW_CHARS = 300
    }
}
