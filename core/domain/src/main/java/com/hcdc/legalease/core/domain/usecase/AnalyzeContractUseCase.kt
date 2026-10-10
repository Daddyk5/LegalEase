package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.engine.LlmAnalyzer
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.engine.LlmReasoning
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.engine.TextExtractor
import com.hcdc.legalease.core.domain.legal.ReportBuilder
import com.hcdc.legalease.core.domain.legal.TextChunker
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.result.getOrNull
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
    data class AnalyzingPage(val page: Int, val pageCount: Int) : AnalysisProgress
    data class Reasoning(val pass: Int, val passes: Int) : AnalysisProgress
}

/**
 * The full offline pipeline for one queued analysis:
 * 1. OCR every page (reused from storage if a previous run got that far).
 * 2. A fast first pass with the classifier for a preliminary label.
 * 3. If the language model is ready: extract facts page by page, then reason over the facts only,
 *    [REASONING_PASSES] times for self-consistency.
 * 4. Ground quotes, attach Civil Code citations, score confidence ([ReportBuilder]).
 *
 * Without the language model, a rule-based explanation is used and the result is always "needs review".
 * Every page is processed unless the user cancels; pages that fail are listed, never silently dropped.
 */
class AnalyzeContractUseCase @Inject constructor(
    private val repository: AnalysisRepository,
    private val extractor: TextExtractor,
    private val classifier: ContractClassifier,
    private val llm: LlmAnalyzer,
    private val featureFlags: FeatureFlagsRepository,
    private val syncScheduler: SyncScheduler,
    private val tracer: PerformanceTracer,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
) {

    suspend operator fun invoke(
        analysisId: String,
        onProgress: suspend (AnalysisProgress) -> Unit = {},
    ): AppResult<AnalysisReport> = tracer.trace(TraceNames.ANALYSIS_END_TO_END) { trace ->
        val startedAt = clock.millis()
        val result = run(analysisId, onProgress)
        trace.putAttribute("outcome", if (result is AppResult.Success) "success" else "failure")
        when (result) {
            is AppResult.Success -> {
                val report = result.value
                trace.putAttribute("method", report.method.name)
                trace.putMetric("page_count", report.pageCount.toLong())
                analytics.log(
                    AnalyticsEvent.AnalysisCompleted(
                        classification = report.shownClassification,
                        confidenceBand = report.confidence.band,
                        method = report.method,
                        pageCount = report.pageCount,
                        durationMs = clock.millis() - startedAt,
                    )
                )
            }
            is AppResult.Failure -> analytics.log(AnalyticsEvent.AnalysisFailed(result.error.code))
        }
        result
    }

    private suspend fun run(
        analysisId: String,
        onProgress: suspend (AnalysisProgress) -> Unit,
    ): AppResult<AnalysisReport> {
        val analysis = repository.get(analysisId) ?: return AppResult.Failure(AppError.DocumentNotFound)

        val text = repository.extractedText(analysisId)
            ?: when (val extracted = extract(analysisId, analysis.sourceKind, onProgress)) {
                is AppResult.Failure -> return fail(analysisId, extracted.error)
                is AppResult.Success -> extracted.value
            }
        if (!text.hasText) return fail(analysisId, AppError.NoTextFound)

        repository.markClassifying(analysisId)
        onProgress(AnalysisProgress.Classifying)
        val prediction = tracer.trace(TraceNames.CLASSIFY) { classifier.classify(text.fullText) }.getOrNull()

        val report = analyzeWithLanguageModel(analysisId, text, prediction, onProgress)
            ?: ReportBuilder.fromRules(text, prediction)
        repository.complete(analysisId, report)
        syncScheduler.requestSync()
        return AppResult.Success(report)
    }

    /** Returns null when the language model is unavailable or produced no usable output. */
    private suspend fun analyzeWithLanguageModel(
        analysisId: String,
        text: ExtractedText,
        prediction: Prediction?,
        onProgress: suspend (AnalysisProgress) -> Unit,
    ): AnalysisReport? {
        if (!featureFlags.flags.value.llmEnabled || llm.availability() != LlmAvailability.READY) return null
        try {
            val facts = mutableListOf<ChunkFacts>()
            val failedChunksByPage = mutableMapOf<Int, Int>()
            val chunks = TextChunker.chunk(text.pages)
            for (chunk in chunks) {
                repository.markAnalyzingPage(analysisId, chunk.page, text.pages.size)
                onProgress(AnalysisProgress.AnalyzingPage(chunk.page, text.pages.size))
                when (val result = tracer.trace(TraceNames.LLM_EXTRACT) { llm.extractFacts(chunk) }) {
                    is AppResult.Success -> facts += result.value
                    is AppResult.Failure -> failedChunksByPage.merge(chunk.page, 1, Int::plus)
                }
            }
            // A page is skipped only if every one of its chunks failed.
            val chunksPerPage = chunks.groupingBy { it.page }.eachCount()
            val skipped = failedChunksByPage.filter { (page, failed) -> failed == chunksPerPage[page] }.keys.sorted()
            if (facts.isEmpty()) return null

            repository.markChecking(analysisId)
            val passes = mutableListOf<LlmReasoning>()
            for (pass in 1..REASONING_PASSES) {
                onProgress(AnalysisProgress.Reasoning(pass, REASONING_PASSES))
                tracer.trace(TraceNames.LLM_REASON) { llm.reason(facts, REASONING_TEMPERATURE) }
                    .getOrNull()?.let(passes::add)
            }
            if (passes.isEmpty()) return null
            return ReportBuilder.fromLanguageModel(text, prediction, facts, passes, REASONING_PASSES, skipped)
        } finally {
            llm.release()
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

    companion object {
        /** Self-consistency: the final reasoning pass runs this many times. */
        const val REASONING_PASSES = 3
        const val REASONING_TEMPERATURE = 0.3f
    }
}
