package com.hcdc.legalease.core.domain.model

/** How long analysis history is kept before it is deleted automatically. */
enum class RetentionPeriod(val days: Int?) {
    NEVER(null),
    DAYS_30(days = 30),
    DAYS_90(days = 90),
    DAYS_365(days = 365),
}

/**
 * The device's Play Integrity verdict. [COMPROMISED] (rooted, emulated, or a modified app) turns the
 * offline language model off; [UNKNOWN] (offline, check not set up, debug build) changes nothing.
 */
enum class DeviceIntegrity { TRUSTED, COMPROMISED, UNKNOWN }

/** What an audit entry records. */
enum class AuditEvent {
    ANALYSIS_COMPLETED,
    ANALYSIS_FAILED,
    CONTRACT_SAVED,
    CONTRACT_EXPORTED,
    DATA_EXPORTED,
    CLOUD_SYNC_ENABLED,
    CLOUD_SYNC_DISABLED,
}

/**
 * One local audit-trail entry. Never contains document text, names, or other content: only ids,
 * the model that ran, and the numbers behind a result.
 *
 * @property subjectId the analysis or contract version concerned, if any.
 * @property detail a short code, e.g. an error code or a paper size. Never free text.
 */
data class AuditEntry(
    val timestampMillis: Long,
    val event: AuditEvent,
    val subjectId: String? = null,
    val modelVersion: String? = null,
    val method: AnalysisMethod? = null,
    val confidence: ConfidenceScore? = null,
    val scoringVersion: Int? = null,
    val detail: String? = null,
)
