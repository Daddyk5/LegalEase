package com.hcdc.legalease.core.ml.llm

import android.util.Log
import com.google.ai.edge.litertlm.LiteRtLmJniException
import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import com.hcdc.legalease.core.domain.engine.ContractAssistant
import com.hcdc.legalease.core.domain.engine.LlmAnalyzer
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.generator.ContractTemplates
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.TimeoutCancellationException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * [ContractAssistant] on the same on-device model as the analyzer. Output is parsed strictly here
 * and checked for content by the domain's ClauseGuard before the user sees it.
 */
@Singleton
class OnDeviceContractAssistant @Inject constructor(
    private val engine: TextGenerator,
    private val analyzer: LlmAnalyzer,
) : ContractAssistant {

    override suspend fun availability(): LlmAvailability = analyzer.availability()

    override suspend fun suggestClauses(type: ContractType, contractText: String): AppResult<List<ClauseSuggestion>> {
        val promptChars = PromptVault.clauseSuggestSystem.length
        val budget = (engine.maxTokens() - SUGGEST_MAX_TOKENS) * CHARS_PER_TOKEN - promptChars
        if (budget <= 0) return AppResult.Failure(AppError.ModelUnavailable)
        val text = contractText.take(budget)
        return generate {
            DraftingJson.parseSuggestions(
                engine.generate(
                    Generation(
                        system = PromptVault.clauseSuggestSystem,
                        user = "Contract (${ContractTemplates.shortName(type)}):\n$text",
                        jsonSchema = DraftingJson.SUGGESTIONS_SCHEMA,
                        temperature = SUGGEST_TEMPERATURE,
                        maxOutputTokens = SUGGEST_MAX_TOKENS,
                        timeout = TIMEOUT,
                    )
                )
            )
        }
    }

    override suspend fun rewriteForClarity(type: ContractType, clause: String): AppResult<String> = generate {
        DraftingJson.parseRewrite(
            engine.generate(
                Generation(
                    system = PromptVault.clauseRewriteSystem,
                    user = "Clause from a ${ContractTemplates.shortName(type)}:\n$clause",
                    jsonSchema = DraftingJson.REWRITE_SCHEMA,
                    temperature = REWRITE_TEMPERATURE,
                    maxOutputTokens = REWRITE_MAX_TOKENS,
                    timeout = TIMEOUT,
                )
            )
        )
    }

    override suspend fun release() = engine.release()

    /** One retry on invalid JSON, as in the analyzer; runtime errors and timeouts fail at once. */
    private suspend fun <T : Any> generate(attempt: suspend () -> T?): AppResult<T> = try {
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
        const val TAG = "OnDeviceDrafting"
        const val ATTEMPTS = 2
        const val SUGGEST_TEMPERATURE = 0.3f
        const val REWRITE_TEMPERATURE = 0.1f
        const val SUGGEST_MAX_TOKENS = 512
        const val REWRITE_MAX_TOKENS = 384
        const val CHARS_PER_TOKEN = 3
        val TIMEOUT = 90.seconds
    }
}
