package com.hcdc.legalease.core.domain.telemetry

import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ConfidenceBand

/**
 * Analytics events. Parameters are limited to enums, counts, and durations by construction:
 * there is no way to attach document names, text, or other content.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    class ScanStarted(sourceKind: String) : AnalyticsEvent("scan_started", mapOf("source_kind" to sourceKind))

    class AnalysisCompleted(
        classification: Classification?,
        confidenceBand: ConfidenceBand,
        method: AnalysisMethod,
        pageCount: Int,
        durationMs: Long,
    ) : AnalyticsEvent(
        "analysis_completed",
        mapOf(
            // Only a verdict the user was shown; otherwise "inconclusive".
            "classification" to (classification?.name ?: "INCONCLUSIVE"),
            "confidence_band" to confidenceBand.name.lowercase(),
            "method" to method.name.lowercase(),
            "page_count" to pageCount.toLong(),
            "duration_ms" to durationMs,
        ),
    )

    class AnalysisFailed(errorCode: String) : AnalyticsEvent("analysis_failed", mapOf("error_code" to errorCode))

    data object AnalysisDeleted : AnalyticsEvent("analysis_deleted")

    class ContractSaved(type: String, version: Int) :
        AnalyticsEvent("contract_saved", mapOf("contract_type" to type, "version" to version.toLong()))

    class ContractExported(type: String, paper: String) :
        AnalyticsEvent("contract_exported", mapOf("contract_type" to type, "paper_size" to paper))

    /** [outcome] is "offered", "accepted", or "withheld"; [count] how many. */
    class ClauseAssist(action: String, outcome: String, count: Int) :
        AnalyticsEvent("clause_assist", mapOf("action" to action, "outcome" to outcome, "count" to count.toLong()))

    class Login(method: String) : AnalyticsEvent("login", mapOf("method" to method))

    class SignUp(method: String) : AnalyticsEvent("sign_up", mapOf("method" to method))

    companion object {
        const val METHOD_EMAIL = "email"
        const val METHOD_GOOGLE = "google"

    }
}

interface AnalyticsTracker {
    fun log(event: AnalyticsEvent)
}

/** A running performance trace. Attribute values must never contain document content. */
interface PerformanceTrace {
    fun putAttribute(name: String, value: String)
    fun putMetric(name: String, value: Long)
    fun stop()
}

interface PerformanceTracer {
    fun start(name: String): PerformanceTrace
}

/** Runs [block] inside a trace that is stopped even if [block] throws or is cancelled. */
inline fun <T> PerformanceTracer.trace(name: String, block: (PerformanceTrace) -> T): T {
    val trace = start(name)
    try {
        return block(trace)
    } finally {
        trace.stop()
    }
}

interface CrashReporter {
    /** Records a caught exception. Messages must not contain document content. */
    fun recordNonFatal(throwable: Throwable)
}

object TraceNames {
    const val OCR_PAGE = "ocr_page"
    const val ANALYSIS_END_TO_END = "analysis_end_to_end"
    const val CLASSIFY = "classify"
    const val LLM_EXTRACT = "llm_extract"
    const val LLM_REASON = "llm_reason"
}
