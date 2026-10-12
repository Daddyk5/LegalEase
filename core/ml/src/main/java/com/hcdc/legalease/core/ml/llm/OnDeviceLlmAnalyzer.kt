package com.hcdc.legalease.core.ml.llm

import android.util.Log
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.LlmAnalyzer
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.engine.LlmReasoning
import com.hcdc.legalease.core.domain.legal.TextChunk
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.TimeoutCancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * [LlmAnalyzer] on the bundled on-device model. Each step gets a fresh conversation, a fixed
 * token budget, a timeout, and one retry if the JSON is invalid; then it fails and the pipeline
 * records the page as skipped or falls back.
 */
@Singleton
class OnDeviceLlmAnalyzer @Inject constructor(
    private val engine: TextGenerator,
    private val provisioner: ModelAvailability,
) : LlmAnalyzer {

    override suspend fun availability(): LlmAvailability {
        val spec = provisioner.specForDevice() ?: return LlmAvailability.UNAVAILABLE
        if (!spec.isPinned) return LlmAvailability.UNAVAILABLE
        return if (provisioner.isProvisioned()) LlmAvailability.READY else LlmAvailability.PREPARING
    }

    override suspend fun extractFacts(chunk: TextChunk): AppResult<ChunkFacts> = withRetry {
        val raw = engine.generate(
            Generation(
                system = PromptVault.extractSystem,
                user = "Page ${chunk.page} excerpt:\n${chunk.text}",
                jsonSchema = LlmJson.FACTS_SCHEMA,
                temperature = EXTRACTION_TEMPERATURE,
                maxOutputTokens = EXTRACTION_MAX_TOKENS,
                timeout = EXTRACTION_TIMEOUT,
            )
        )
        LlmJson.parseFacts(raw, chunk.page)
    }

    override suspend fun reason(facts: List<ChunkFacts>, temperature: Float): AppResult<LlmReasoning> {
        val (included, omitted) = fitToBudget(facts)
        if (included.isEmpty()) return AppResult.Failure(AppError.Unexpected("facts_exceed_context"))
        return withRetry {
            val raw = engine.generate(
                Generation(
                    system = PromptVault.reasonSystem,
                    user = PromptVault.civilCodeReference +
                        "\n\nExtracted facts (JSON):\n" + LlmJson.encodeFacts(included),
                    jsonSchema = LlmJson.REASONING_SCHEMA,
                    temperature = temperature,
                    maxOutputTokens = REASONING_MAX_TOKENS,
                    timeout = REASONING_TIMEOUT,
                )
            )
            LlmJson.parseReasoning(raw)?.copy(omittedPages = omitted)
        }
    }

    override suspend fun release() = engine.release()

    override suspend fun modelVersion(): String? {
        val spec = provisioner.specForDevice()?.takeIf { it.isPinned } ?: return null
        if (!provisioner.isProvisioned()) return null
        return "${spec.tier.name.lowercase()}:${spec.sha256.take(HASH_PREFIX)}"
    }

    override suspend fun warmUp(): AppResult<Unit> {
        if (availability() != LlmAvailability.READY) return AppResult.Failure(AppError.ModelUnavailable)
        return try {
            when (val result = extractFacts(TextChunk(page = 1, part = 0, text = SELF_TEST_TEXT))) {
                is AppResult.Success -> AppResult.Success(Unit)
                is AppResult.Failure -> result
            }
        } finally {
            release()
        }
    }

    /**
     * Keeps whole pages of facts, in page order, while they fit in the context window after the
     * prompts and output budget. Pages left out are reported, never dropped silently.
     */
    private fun fitToBudget(facts: List<ChunkFacts>): Pair<List<LlmJson.FactsForReasoning>, List<Int>> {
        val byPage = facts.groupBy { it.page }.toSortedMap().map { (page, chunks) ->
            LlmJson.FactsForReasoning(
                page = page,
                parties = chunks.flatMap { it.parties }.distinct(),
                objectDescription = chunks.firstNotNullOfOrNull { it.objectDescription },
                consideration = chunks.firstNotNullOfOrNull { it.consideration },
                term = chunks.firstNotNullOfOrNull { it.term },
                signatureOrNotarization = chunks.flatMap { it.signatureOrNotarization }.distinct(),
                quotedClauses = chunks.flatMap { c -> c.unusualClauses.map { it.text } }.distinct(),
            )
        }
        val promptChars = PromptVault.reasonSystem.length + PromptVault.civilCodeReference.length
        val budgetChars = (engine.maxTokens() - REASONING_MAX_TOKENS) * CHARS_PER_TOKEN - promptChars
        val included = mutableListOf<LlmJson.FactsForReasoning>()
        val omitted = mutableListOf<Int>()
        var used = 0
        for (page in byPage) {
            val size = LlmJson.encodeFacts(listOf(page)).length
            if (used + size <= budgetChars) {
                included += page
                used += size
            } else {
                omitted += page.page
            }
        }
        return included to omitted
    }

    /** One retry on invalid JSON, as specified; runtime errors and timeouts fail the step. */
    private suspend fun <T : Any> withRetry(attempt: suspend () -> T?): AppResult<T> = try {
        (1..ATTEMPTS).firstNotNullOfOrNull { attempt() }
            ?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.Unexpected("llm_invalid_json"))
    } catch (_: TimeoutCancellationException) {
        AppResult.Failure(AppError.Unexpected("llm_timeout"))
    } catch (e: LiteRtLmJniException) {
        Log.w(TAG, "Model runtime error", e)
        AppResult.Failure(AppError.ModelIncompatible)
    } catch (e: IllegalStateException) {
        Log.w(TAG, "Model unavailable", e)
        AppResult.Failure(AppError.ModelUnavailable)
    }

    private companion object {
        const val TAG = "OnDeviceLlm"
        const val HASH_PREFIX = 12
        const val SELF_TEST_TEXT =
            "This Contract of Lease is made between Juan dela Cruz, Lessor, and Maria Santos, Lessee."
        const val ATTEMPTS = 2
        const val EXTRACTION_TEMPERATURE = 0.1f
        const val EXTRACTION_MAX_TOKENS = 512
        const val REASONING_MAX_TOKENS = 1024
        const val CHARS_PER_TOKEN = 3
        val EXTRACTION_TIMEOUT = 90.seconds
        val REASONING_TIMEOUT = 180.seconds
    }
}
