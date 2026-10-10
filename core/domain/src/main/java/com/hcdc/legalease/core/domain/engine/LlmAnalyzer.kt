package com.hcdc.legalease.core.domain.engine

import com.hcdc.legalease.core.domain.legal.TextChunk
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ReasonCode
import com.hcdc.legalease.core.domain.model.RiskLevel
import com.hcdc.legalease.core.domain.result.AppResult

/** Whether the on-device language model can be used right now. */
enum class LlmAvailability {
    READY,

    /** Still downloading or preparing; the classifier fallback is used meanwhile. */
    PREPARING,

    /** Not enough memory or storage on this device, or turned off remotely. */
    UNAVAILABLE,
}

/** A clause quoted by the model, with the page it came from. */
data class QuotedClause(val text: String, val page: Int)

/** Facts extracted from one chunk. Strings are the model's words except where marked as quotes. */
data class ChunkFacts(
    val page: Int,
    val parties: List<String>,
    val objectDescription: String?,
    val consideration: String?,
    val term: String?,
    val signatureOrNotarization: List<String>,
    val unusualClauses: List<QuotedClause>,
)

data class LlmReason(
    val finding: String,
    val clauseQuote: String?,
    val page: Int?,
    val code: ReasonCode,
)

/** One reasoning pass's strict-JSON output, already validated. */
data class LlmReasoning(
    val contractName: String?,
    val contractType: DetectedContractType,
    val classification: Classification,
    val summary: String,
    val reasons: List<LlmReason>,
    val missingElements: List<String>,
    val riskFlags: List<Pair<RiskLevel, String>>,
    val recommendations: List<String>,
    /** 1-based pages whose facts did not fit the model's context and were left out of this pass. */
    val omittedPages: List<Int> = emptyList(),
)

/**
 * The on-device language model. Implementations are app-scoped, initialise lazily on a background
 * dispatcher, and must be cancellable. Every call has its own timeout and fixed token budget.
 */
interface LlmAnalyzer {
    suspend fun availability(): LlmAvailability

    /** Extracts facts from one chunk. Fails rather than returning partially valid JSON. */
    suspend fun extractFacts(chunk: TextChunk): AppResult<ChunkFacts>

    /**
     * Reasons over extracted facts only, never the raw text. Implementations must not let the model
     * see the classifier's label, so their agreement remains independent evidence.
     */
    suspend fun reason(facts: List<ChunkFacts>, temperature: Float): AppResult<LlmReasoning>

    /** Frees the model's memory. The next call loads it again. */
    suspend fun release()

    /** Loads the model, runs one tiny generation as a self-test, and releases it. */
    suspend fun warmUp(): AppResult<Unit>
}

/** Delivers the encrypted model to the device (Play Asset Delivery or a one-time download). */
interface OfflineModelProvisioner {
    /** True once a verified, encrypted model for this device's tier is in place. */
    suspend fun isProvisioned(): Boolean

    /**
     * Fetches, verifies (SHA-256), and encrypts the model. On a hash mismatch the download is
     * deleted and the call fails so it can be retried.
     */
    suspend fun provision(onProgress: (fraction: Float) -> Unit = {}): AppResult<Unit>
}
