package com.hcdc.legalease.core.ml.llm

import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Strict JSON for the drafting assistant. Content checks happen in the domain's ClauseGuard. */
internal object DraftingJson {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
    }

    private const val MAX_SUGGESTIONS = 3

    @Serializable
    private data class ClauseDto(val title: String, val text: String)

    @Serializable
    private data class SuggestionsDto(val clauses: List<ClauseDto>)

    @Serializable
    private data class RewriteDto(val text: String)

    const val SUGGESTIONS_SCHEMA = """{"type":"object","properties":{
"clauses":{"type":"array","maxItems":3,"items":{"type":"object","properties":{
"title":{"type":"string"},"text":{"type":"string"}},"required":["title","text"]}}},
"required":["clauses"]}"""

    const val REWRITE_SCHEMA = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""

    fun parseSuggestions(raw: String): List<ClauseSuggestion>? {
        val dto = decode(raw) { json.decodeFromString(SuggestionsDto.serializer(), it) } ?: return null
        if (dto.clauses.size > MAX_SUGGESTIONS) return null
        return dto.clauses.map { ClauseSuggestion(it.title.trim(), it.text.trim()) }
    }

    fun parseRewrite(raw: String): String? =
        decode(raw) { json.decodeFromString(RewriteDto.serializer(), it) }?.text?.trim()?.takeIf { it.isNotEmpty() }

    /** Tolerates prose or code fences around the object, but nothing inside it. */
    private fun <T> decode(raw: String, parse: (String) -> T): T? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            parse(raw.substring(start, end + 1))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
