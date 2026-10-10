package com.hcdc.legalease.core.domain.engine

import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.result.AppResult

/** An optional clause proposed by the assistant. Unchecked: always pass it through the clause guard. */
data class ClauseSuggestion(val title: String, val text: String)

/**
 * The on-device language model in its drafting role. It may only propose optional clauses and reword
 * them; it never sees or edits the mandatory template text except as read-only context.
 */
interface ContractAssistant {
    suspend fun availability(): LlmAvailability

    /** Up to a few optional clauses that would suit [contractText]. */
    suspend fun suggestClauses(type: ContractType, contractText: String): AppResult<List<ClauseSuggestion>>

    /** [clause] reworded for clarity, with the same meaning. */
    suspend fun rewriteForClarity(type: ContractType, clause: String): AppResult<String>

    /** Frees the model's memory. */
    suspend fun release()
}
