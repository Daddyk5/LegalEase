package com.hcdc.legalease.core.domain.telemetry

import com.hcdc.legalease.core.domain.model.Classification

/**
 * Analytics events. Parameters are limited to enums, counts, and durations by construction:
 * there is no way to attach document names, text, or other content.
 */
sealed class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap()) {

    class ScanStarted(sourceKind: String) : AnalyticsEvent("scan_started", mapOf("source_kind" to sourceKind))

    class AnalysisCompleted(classification: Classification, confidenceBand: String, pageCount: Int, durationMs: Long) :
        AnalyticsEvent(
            "analysis_completed",
            mapOf(
                "classification" to classification.name,
                "confidence_band" to confidenceBand,
                "page_count" to pageCount.toLong(),
                "duration_ms" to durationMs,
            ),
        )

    class AnalysisFailed(errorCode: String) : AnalyticsEvent("analysis_failed", mapOf("error_code" to errorCode))

    data object AnalysisDeleted : AnalyticsEvent("analysis_deleted")

    class Login(method: String) : AnalyticsEvent("login", mapOf("method" to method))

    class SignUp(method: String) : AnalyticsEvent("sign_up", mapOf("method" to method))

    companion object {
        const val METHOD_EMAIL = "email"
        const val METHOD_GOOGLE = "google"

        /** Coarse band for analytics; never the raw probability. */
        fun confidenceBand(confidence: Float): String = when {
            confidence >= HIGH -> "high"
            confidence >= CONFIDENT -> "confident"
            else -> "below_threshold"
        }

        private const val HIGH = 0.90f
        private const val CONFIDENT = 0.80f
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
}
