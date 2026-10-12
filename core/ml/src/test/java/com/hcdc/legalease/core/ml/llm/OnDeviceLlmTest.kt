package com.hcdc.legalease.core.ml.llm

import com.hcdc.legalease.core.domain.engine.ChunkFacts
import com.hcdc.legalease.core.domain.engine.ClauseSuggestion
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.legal.TextChunk
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnDeviceLlmTest {

    private val generator = FakeGenerator()
    private val models = FakeModels()
    private val analyzer = OnDeviceLlmAnalyzer(generator, models)
    private val assistant = OnDeviceContractAssistant(generator, analyzer)

    @Test
    fun `availability follows the pinned spec and the provisioned file`() = runTest {
        models.spec = null
        assertEquals(LlmAvailability.UNAVAILABLE, analyzer.availability())
        models.spec = ModelCatalog.PRIMARY
        assertEquals("an unpinned model is never used", LlmAvailability.UNAVAILABLE, analyzer.availability())
        models.spec = PINNED
        assertEquals(LlmAvailability.PREPARING, analyzer.availability())
        assertNull(analyzer.modelVersion())
        models.provisioned = true
        assertEquals(LlmAvailability.READY, analyzer.availability())
        assertEquals("primary:${"a".repeat(12)}", analyzer.modelVersion())
    }

    @Test
    fun `facts are retried once on invalid JSON, then fail`() = runTest {
        generator.replies += listOf("not json", FACTS)
        val ok = analyzer.extractFacts(TextChunk(page = 3, part = 0, text = "text"))
        assertEquals(3, (ok as AppResult.Success).value.page)
        assertEquals(2, generator.requests.size)
        assertTrue(generator.requests.first().user.startsWith("Page 3 excerpt:"))

        generator.replies += listOf("bad", "still bad")
        val failed = analyzer.extractFacts(TextChunk(page = 1, part = 0, text = "text"))
        assertEquals(AppResult.Failure(AppError.Unexpected("llm_invalid_json")), failed)
    }

    @Test
    fun `runtime failures map to typed errors`() = runTest {
        generator.failure = { withTimeout(1) { delay(10) } }
        assertEquals(AppResult.Failure(AppError.Unexpected("llm_timeout")), analyzer.extractFacts(CHUNK))
        generator.failure = { error("engine closed") }
        assertEquals(AppResult.Failure(AppError.ModelUnavailable), analyzer.extractFacts(CHUNK))
    }

    @Test
    fun `reasoning reports pages that did not fit the context`() = runTest {
        val pages = (1..3).map { facts(page = it, party = "P".repeat(PAGE_TEXT)) }
        generator.maxTokens = tokensFor(pagesThatFit = 2, pages)
        generator.replies += REASONING
        val result = analyzer.reason(pages, temperature = 0.2f) as AppResult.Success
        assertEquals(Classification.VOIDABLE, result.value.classification)
        assertEquals(listOf(3), result.value.omittedPages)
    }

    @Test
    fun `reasoning fails rather than run with no facts`() = runTest {
        generator.maxTokens = 0
        val result = analyzer.reason(listOf(facts(page = 1, party = "A")), temperature = 0.2f)
        assertEquals(AppResult.Failure(AppError.Unexpected("facts_exceed_context")), result)
        assertTrue(generator.requests.isEmpty())
    }

    @Test
    fun `warm-up runs one self-test and always frees the model`() = runTest {
        models.spec = PINNED
        assertEquals(AppResult.Failure(AppError.ModelUnavailable), analyzer.warmUp())
        models.provisioned = true
        generator.replies += FACTS
        assertEquals(AppResult.Success(Unit), analyzer.warmUp())
        assertEquals(1, generator.released)
    }

    @Test
    fun `suggestions and rewrites are parsed strictly`() = runTest {
        generator.replies += """{"clauses":[{"title":"Pets","text":"No pets."}]}"""
        val suggestions = assistant.suggestClauses(ContractType.LEASE, "CONTRACT OF LEASE")
        assertEquals(AppResult.Success(listOf(ClauseSuggestion("Pets", "No pets."))), suggestions)

        generator.replies += listOf("""{"text":" "}""", """{"text":"Clear."}""")
        assertEquals(AppResult.Success("Clear."), assistant.rewriteForClarity(ContractType.LOAN, "clause"))
    }

    @Test
    fun `the assistant refuses when the model has no room`() = runTest {
        generator.maxTokens = 0
        val result = assistant.suggestClauses(ContractType.SALE, "text")
        assertEquals(AppResult.Failure(AppError.ModelUnavailable), result)
    }

    private fun tokensFor(pagesThatFit: Int, pages: List<ChunkFacts>): Int {
        val pageChars = LlmJson.encodeFacts(
            listOf(
                LlmJson.FactsForReasoning(
                    page = pages.first().page,
                    parties = pages.first().parties,
                    objectDescription = null,
                    consideration = null,
                    term = null,
                    signatureOrNotarization = emptyList(),
                    quotedClauses = emptyList(),
                )
            )
        ).length
        val promptChars = PromptVault.reasonSystem.length + PromptVault.civilCodeReference.length
        // Room for the prompts, [pagesThatFit] pages and half of the next one.
        val chars = promptChars + pageChars * pagesThatFit + pageChars / 2
        return REASONING_TOKENS + chars / CHARS_PER_TOKEN
    }

    private class FakeGenerator : TextGenerator {
        val replies = ArrayDeque<String>()
        val requests = mutableListOf<Generation>()
        var failure: (suspend () -> Unit)? = null
        var maxTokens = 4096
        var released = 0

        override suspend fun generate(request: Generation): String {
            requests += request
            failure?.invoke()
            return replies.removeFirst()
        }

        override fun maxTokens() = maxTokens

        override suspend fun release() {
            released++
        }
    }

    private class FakeModels : ModelAvailability {
        var spec: ModelSpec? = null
        var provisioned = false
        override fun specForDevice() = spec
        override suspend fun isProvisioned() = provisioned
    }

    private companion object {
        val PINNED = ModelCatalog.PRIMARY.copy(sha256 = "a".repeat(64), sizeBytes = 1)
        val CHUNK = TextChunk(page = 1, part = 0, text = "text")
        const val PAGE_TEXT = 500
        const val REASONING_TOKENS = 1024
        const val CHARS_PER_TOKEN = 3
        const val FACTS = """{"parties":["Lessor","Lessee"],"object":"house","consideration":"5,000",
            "term":null,"signatureOrNotarization":[],"unusualClauses":[]}"""
        const val REASONING = """{"contractName":"CONTRACT OF LEASE","contractType":"Lease",
            "classification":"Voidable","summary":"A lease.","reasons":[],"missingElements":[],
            "riskFlags":[],"recommendations":[]}"""

        fun facts(page: Int, party: String) = ChunkFacts(
            page = page,
            parties = listOf(party),
            objectDescription = null,
            consideration = null,
            term = null,
            signatureOrNotarization = emptyList(),
            unusualClauses = emptyList(),
        )
    }
}
