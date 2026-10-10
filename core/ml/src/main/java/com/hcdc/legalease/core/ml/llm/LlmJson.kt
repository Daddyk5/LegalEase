package com.hcdc.legalease.core.ml.llm

import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.LlmReason
import com.hcdc.legalease.core.domain.engine.LlmReasoning
import com.hcdc.legalease.core.domain.engine.QuotedClause
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ReasonCode
import com.hcdc.legalease.core.domain.model.RiskLevel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The model's strict-JSON contract. Output is decoded with kotlinx.serialization and then
 * validated field by field; anything outside the allowed values is rejected, never coerced.
 */
internal object LlmJson {

    private val json = Json {
        ignoreUnknownKeys = false
        isLenient = false
        explicitNulls = false
    }

    @Serializable
    data class FactsDto(
        val parties: List<String>,
        @SerialName("object") val objectDescription: String? = null,
        val consideration: String? = null,
        val term: String? = null,
        val signatureOrNotarization: List<String>,
        val unusualClauses: List<String>,
    )

    @Serializable
    data class ReasonDto(
        val finding: String,
        val clauseQuote: String? = null,
        val page: Int? = null,
        val reasonCode: String,
    )

    @Serializable
    data class RiskFlagDto(val level: String, val description: String)

    @Serializable
    data class ReasoningDto(
        val contractName: String? = null,
        val contractType: String,
        val classification: String,
        val summary: String,
        val reasons: List<ReasonDto>,
        val missingElements: List<String>,
        val riskFlags: List<RiskFlagDto>,
        val recommendations: List<String>,
    )

    /** Compact facts sent to the reasoning pass. Quoted clauses carry their page numbers. */
    @Serializable
    data class FactsForReasoning(
        val page: Int,
        val parties: List<String> = emptyList(),
        @SerialName("object") val objectDescription: String? = null,
        val consideration: String? = null,
        val term: String? = null,
        val signatureOrNotarization: List<String> = emptyList(),
        val quotedClauses: List<String> = emptyList(),
    )

    private const val MAX_LIST = 12
    private const val MAX_FIELD_CHARS = 600

    private val CONTRACT_TYPES = mapOf(
        "Lease" to DetectedContractType.LEASE,
        "Loan" to DetectedContractType.LOAN,
        "Sale" to DetectedContractType.SALE,
        "LeaseOfPersonalProperty" to DetectedContractType.LEASE_OF_PERSONAL_PROPERTY,
        "Other" to DetectedContractType.OTHER,
    )
    private val RISK_LEVELS = mapOf("High" to RiskLevel.HIGH, "Medium" to RiskLevel.MEDIUM, "Low" to RiskLevel.LOW)

    /** Parses extraction output. Returns null for malformed JSON or out-of-contract values. */
    fun parseFacts(raw: String, page: Int): ChunkFacts? {
        val dto = decode<FactsDto>(raw) ?: return null
        val lists = listOf(dto.parties, dto.signatureOrNotarization, dto.unusualClauses)
        if (lists.any { it.size > MAX_LIST } || lists.flatten().any { it.length > MAX_FIELD_CHARS }) return null
        return ChunkFacts(
            page = page,
            parties = dto.parties.clean(),
            objectDescription = dto.objectDescription?.trim()?.takeIf { it.isNotEmpty() },
            consideration = dto.consideration?.trim()?.takeIf { it.isNotEmpty() },
            term = dto.term?.trim()?.takeIf { it.isNotEmpty() },
            signatureOrNotarization = dto.signatureOrNotarization.clean(),
            unusualClauses = dto.unusualClauses.clean().map { QuotedClause(it, page) },
        )
    }

    /** Parses a reasoning pass. Returns null unless every enum and required field is valid. */
    fun parseReasoning(raw: String): LlmReasoning? {
        val dto = decode<ReasoningDto>(raw) ?: return null
        val classification = Classification.entries.firstOrNull { it.label == dto.classification } ?: return null
        val contractType = CONTRACT_TYPES[dto.contractType] ?: return null
        if (dto.summary.isBlank() || dto.reasons.size > MAX_LIST) return null
        val reasons = dto.reasons.map { r ->
            val code = ReasonCode.entries.firstOrNull { it.name == r.reasonCode } ?: return null
            if (r.finding.isBlank()) return null
            LlmReason(r.finding.trim(), r.clauseQuote?.trim()?.takeIf { it.isNotEmpty() }, r.page, code)
        }
        val flags = dto.riskFlags.map { (RISK_LEVELS[it.level] ?: return null) to it.description.trim() }
        return LlmReasoning(
            contractName = dto.contractName?.trim()?.takeIf { it.isNotEmpty() },
            contractType = contractType,
            classification = classification,
            summary = dto.summary.trim(),
            reasons = reasons,
            missingElements = dto.missingElements.clean(),
            riskFlags = flags.filter { it.second.isNotEmpty() },
            recommendations = dto.recommendations.clean(),
        )
    }

    fun encodeFacts(facts: List<FactsForReasoning>): String = json.encodeToString(facts)

    /** Tolerates prose or code fences around the object, but nothing inside it. */
    private inline fun <reified T> decode(raw: String): T? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return try {
            json.decodeFromString<T>(raw.substring(start, end + 1))
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun List<String>.clean() = map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    // JSON Schemas for constrained decoding. The parser above enforces the same contract.
    const val FACTS_SCHEMA = """{"type":"object","properties":{
"parties":{"type":"array","items":{"type":"string"}},
"object":{"type":["string","null"]},
"consideration":{"type":["string","null"]},
"term":{"type":["string","null"]},
"signatureOrNotarization":{"type":"array","items":{"type":"string"}},
"unusualClauses":{"type":"array","items":{"type":"string"},"maxItems":3}},
"required":["parties","object","consideration","term","signatureOrNotarization","unusualClauses"]}"""

    const val REASONING_SCHEMA = """{"type":"object","properties":{
"contractName":{"type":"string"},
"contractType":{"enum":["Lease","Loan","Sale","LeaseOfPersonalProperty","Other"]},
"classification":{"enum":["Void","Voidable","Unenforceable","Rescissible","Enforceable"]},
"summary":{"type":"string"},
"reasons":{"type":"array","maxItems":6,"items":{"type":"object","properties":{
"finding":{"type":"string"},
"clauseQuote":{"type":["string","null"]},
"page":{"type":["integer","null"]},
"reasonCode":{"enum":["NO_CONSENT","NO_OBJECT","NO_CAUSE","FORM_NOT_MET","STATUTE_OF_FRAUDS","LESION",
"VITIATED_CONSENT","INCAPACITY","ILLEGAL_OBJECT","SIMULATED","FRAUD_ON_CREDITORS","NONE"]}},
"required":["finding","clauseQuote","page","reasonCode"]}},
"missingElements":{"type":"array","items":{"type":"string"}},
"riskFlags":{"type":"array","items":{"type":"object","properties":{
"level":{"enum":["High","Medium","Low"]},"description":{"type":"string"}},"required":["level","description"]}},
"recommendations":{"type":"array","items":{"type":"string"}}},
"required":["contractName","contractType","classification","summary","reasons","missingElements","riskFlags",
"recommendations"]}"""
}
