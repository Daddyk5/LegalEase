package com.hcdc.legalease.core.ml.llm

import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ReasonCode
import com.hcdc.legalease.core.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmJsonTest {

    private val valid = """
        {"contractName":"CONTRACT OF LEASE","contractType":"Lease","classification":"Voidable",
         "summary":"A lease with a one-sided eviction clause.",
         "reasons":[{"finding":"Eviction without notice","clauseQuote":"may be evicted without notice","page":2,
                     "reasonCode":"VITIATED_CONSENT"}],
         "missingElements":[],"riskFlags":[{"level":"High","description":"Eviction without notice"}],
         "recommendations":["Consult a lawyer."]}
    """.trimIndent()

    @Test
    fun `valid reasoning parses into domain types`() {
        val r = LlmJson.parseReasoning(valid)!!
        assertEquals(Classification.VOIDABLE, r.classification)
        assertEquals(DetectedContractType.LEASE, r.contractType)
        assertEquals(ReasonCode.VITIATED_CONSENT, r.reasons.single().code)
        assertEquals(RiskLevel.HIGH, r.riskFlags.single().first)
    }

    @Test
    fun `prose and code fences around the object are tolerated`() {
        assertNotNull(LlmJson.parseReasoning("Here you go:\n```json\n$valid\n```"))
    }

    @Test
    fun `malformed JSON is rejected`() {
        assertNull(LlmJson.parseReasoning("""{"contractType":"Lease","classification":"Void","""))
        assertNull(LlmJson.parseReasoning("no json here"))
    }

    @Test
    fun `missing required fields are rejected`() {
        assertNull(LlmJson.parseReasoning("""{"contractType":"Lease","classification":"Void"}"""))
    }

    @Test
    fun `values outside the contract are rejected, not coerced`() {
        assertNull(LlmJson.parseReasoning(valid.replace("\"Voidable\"", "\"Valid\"")))
        assertNull(LlmJson.parseReasoning(valid.replace("\"VITIATED_CONSENT\"", "\"BAD_FAITH\"")))
        assertNull(LlmJson.parseReasoning(valid.replace("\"Lease\"", "\"Lease agreement\"")))
        assertNull(LlmJson.parseReasoning(valid.replace("\"High\"", "\"Severe\"")))
    }

    @Test
    fun `unknown fields are rejected`() {
        assertNull(LlmJson.parseReasoning(valid.replace("\"summary\"", "\"articles\":[\"1390\"],\"summary\"")))
    }

    @Test
    fun `blank quotes become null`() {
        val r = LlmJson.parseReasoning(valid.replace("\"may be evicted without notice\"", "\"  \""))!!
        assertNull(r.reasons.single().clauseQuote)
    }

    @Test
    fun `facts parse with the chunk's page and clean lists`() {
        val facts = LlmJson.parseFacts(
            """{"parties":["Lessor"," Lessor ","Lessee"],"object":"house","consideration":null,"term":null,
               "signatureOrNotarization":[],"unusualClauses":["may be evicted without notice"]}""",
            page = 2,
        )!!
        assertEquals(listOf("Lessor", "Lessee"), facts.parties)
        assertEquals(2, facts.unusualClauses.single().page)
        assertNull(facts.consideration)
    }

    @Test
    fun `facts with oversized fields are rejected`() {
        val long = "x".repeat(601)
        assertNull(
            LlmJson.parseFacts(
                """{"parties":["$long"],"signatureOrNotarization":[],"unusualClauses":[]}""",
                page = 1,
            )
        )
    }

    @Test
    fun `schemas are valid JSON listing every code`() {
        ReasonCode.entries.forEach { assertTrue(it.name, LlmJson.REASONING_SCHEMA.contains("\"${it.name}\"")) }
        Classification.entries.forEach { assertTrue(LlmJson.REASONING_SCHEMA.contains("\"${it.label}\"")) }
        assertNotNull(kotlinx.serialization.json.Json.parseToJsonElement(LlmJson.REASONING_SCHEMA))
        assertNotNull(kotlinx.serialization.json.Json.parseToJsonElement(LlmJson.FACTS_SCHEMA))
    }
}

class ModelSelectionTest {

    private val gib = 1L shl 30

    @Test
    fun `six gigabyte phones get the primary tier`() {
        assertEquals(ModelTier.PRIMARY, DeviceCapability.tierFor(11 * gib / 2))
        assertEquals(ModelTier.PRIMARY, DeviceCapability.tierFor(8 * gib))
    }

    @Test
    fun `smaller phones get the lite tier, very small ones none`() {
        assertEquals(ModelTier.LITE, DeviceCapability.tierFor(4 * gib))
        assertEquals(ModelTier.LITE, DeviceCapability.tierFor(3 * gib))
        assertNull(DeviceCapability.tierFor(2 * gib))
    }

    @Test
    fun `models are unusable until their hash and size are pinned`() {
        assertFalse(ModelCatalog.PRIMARY.copy(sha256 = "", sizeBytes = 1).isPinned)
        assertFalse(ModelCatalog.PRIMARY.copy(sha256 = "ABC", sizeBytes = 1).isPinned)
        assertTrue(ModelCatalog.PRIMARY.copy(sha256 = "a".repeat(64), sizeBytes = 1).isPinned)
    }
}
