package com.hcdc.legalease.core.domain.legal

import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ConfidenceBand
import com.hcdc.legalease.core.domain.model.ConfidenceScore
import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.ReasonCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuoteVerifierTest {

    private val pages = listOf(
        "The LESSOR hereby leases unto the LESSEE the residential unit located at Unit 4B.",
        "The LESSEE shall pay a monthly rental of Fifteen Thousand Pesos (PHP 15,000.00) on or before the 5th day.",
    )

    @Test
    fun `exact quote on the claimed page matches`() {
        assertEquals(2, QuoteVerifier.verify("shall pay a monthly rental of Fifteen Thousand Pesos", pages, 2)?.page)
    }

    @Test
    fun `quote with OCR noise and punctuation differences still matches`() {
        val match = QuoteVerifier.verify("The LESSEE shal1 pay a monthly rentaI of Fifteen Thousand Pesos", pages, 2)
        assertNotNull(match)
        assertTrue(match!!.similarity >= 0.85)
    }

    @Test
    fun `wrong page is corrected`() {
        assertEquals(1, QuoteVerifier.verify("hereby leases unto the LESSEE", pages, claimedPage = 2)?.page)
    }

    @Test
    fun `invented text is rejected`() {
        assertNull(QuoteVerifier.verify("The LESSEE waives every right under the law", pages, 1))
    }

    @Test
    fun `quotes over 40 words are rejected`() {
        val page = List(60) { "word" }.joinToString(" ")
        assertNull(QuoteVerifier.verify(List(41) { "word" }.joinToString(" "), listOf(page), 1))
    }

    @Test
    fun `similarity is normalised Levenshtein`() {
        assertEquals(1.0, QuoteVerifier.similarity("abc", "abc"), 0.0)
        assertEquals(0.75, QuoteVerifier.similarity("abcd", "abce"), 1e-9)
    }
}

class ConfidenceScorerTest {

    private val perfect = ConfidenceScorer.Evidence(
        classifierProbabilityForLabel = 1f,
        passLabels = List(3) { Classification.VOID },
        passesAttempted = 3,
        reasonsTotal = 2,
        reasonsVerified = 2,
        pageQuality = listOf(1f, 1f),
        pagesAnalyzed = 2,
        pageCount = 2,
        consentLocated = true,
        objectLocated = true,
        causeLocated = true,
    )

    @Test
    fun `weights sum to one`() {
        val sum = ConfidenceScorer.W_SELF_CONSISTENCY + ConfidenceScorer.W_GROUNDING + ConfidenceScorer.W_AGREEMENT +
            ConfidenceScorer.W_OCR_QUALITY + ConfidenceScorer.W_COMPLETENESS
        assertEquals(1f, sum, 1e-6f)
    }

    @Test
    fun `perfect evidence scores one and is high confidence`() {
        val score = ConfidenceScorer.score(perfect)
        assertEquals(1f, score.total, 1e-6f)
        assertEquals(ConfidenceBand.HIGH, score.band)
    }

    @Test
    fun `components are computed from the evidence`() {
        val c = ConfidenceScorer.components(
            perfect.copy(
                passLabels = listOf(Classification.VOID, Classification.VOID, Classification.VOIDABLE),
                reasonsVerified = 1,
                pageQuality = listOf(0.5f, 1f),
                pagesAnalyzed = 1,
                causeLocated = false,
            )
        )
        assertEquals(2f / 3, c.selfConsistency, 1e-6f)
        assertEquals(0.5f, c.grounding, 1e-6f)
        assertEquals(0.75f, c.ocrQuality, 1e-6f)
        assertEquals(0.5f * 0.5f + 0.5f * (2f / 3), c.completeness, 1e-6f)
    }

    @Test
    fun `a failed pass counts against self-consistency`() {
        val c = ConfidenceScorer.components(perfect.copy(passLabels = List(2) { Classification.VOID }))
        assertEquals(2f / 3, c.selfConsistency, 1e-6f)
    }

    @Test
    fun `classifier-only evidence can never reach the display threshold`() {
        val best = perfect.copy(classifierProbabilityForLabel = null, passLabels = emptyList(), passesAttempted = 0)
        assertEquals(0.55f, ConfidenceScorer.score(best).total, 1e-6f)
        assertEquals(ConfidenceBand.INCONCLUSIVE, ConfidenceScorer.score(best).band)
    }

    @Test
    fun `bands follow the policy table`() {
        assertEquals(ConfidenceBand.HIGH, ConfidenceScore(0.90f, null).band)
        assertEquals(ConfidenceBand.CONFIDENT, ConfidenceScore(0.80f, null).band)
        assertEquals(ConfidenceBand.CONFIDENT, ConfidenceScore(0.89f, null).band)
        assertEquals(ConfidenceBand.INCONCLUSIVE, ConfidenceScore(0.7999f, null).band)
    }
}

class CivilCodeCitationsTest {

    @Test
    fun `every reason code maps to the specified articles`() {
        val expected = mapOf(
            ReasonCode.NO_CONSENT to listOf("Art. 1318"),
            ReasonCode.NO_OBJECT to listOf("Art. 1318", "Arts. 1409–1422"),
            ReasonCode.NO_CAUSE to listOf("Art. 1318", "Arts. 1409–1422"),
            ReasonCode.FORM_NOT_MET to listOf("Arts. 1356–1358"),
            ReasonCode.STATUTE_OF_FRAUDS to listOf("Art. 1403(2)"),
            ReasonCode.LESION to listOf("Arts. 1380–1389"),
            ReasonCode.FRAUD_ON_CREDITORS to listOf("Arts. 1380–1389"),
            ReasonCode.VITIATED_CONSENT to listOf("Arts. 1390–1402"),
            ReasonCode.INCAPACITY to listOf("Arts. 1390–1402"),
            ReasonCode.ILLEGAL_OBJECT to listOf("Arts. 1409–1422"),
            ReasonCode.SIMULATED to listOf("Arts. 1409–1422"),
            ReasonCode.NONE to emptyList(),
        )
        assertEquals(ReasonCode.entries.toSet(), expected.keys)
        expected.forEach { (code, articles) ->
            assertEquals(code.name, articles, CivilCodeCitations.forReason(code).map { it.article })
        }
    }

    @Test
    fun `contract types map to their titles in the Code`() {
        assertEquals("Arts. 1642–1688", CivilCodeCitations.forContractType(DetectedContractType.LEASE).single().article)
        assertEquals("Arts. 1933–1961", CivilCodeCitations.forContractType(DetectedContractType.LOAN).single().article)
        assertEquals("Arts. 1458–1637", CivilCodeCitations.forContractType(DetectedContractType.SALE).single().article)
        assertTrue(CivilCodeCitations.forContractType(DetectedContractType.OTHER).isEmpty())
    }
}

class TextChunkerTest {

    @Test
    fun `short pages become one chunk each and keep their page number`() {
        val chunks = TextChunker.chunk(listOf("a b c", "", "d e"))
        assertEquals(listOf(1, 3), chunks.map { it.page })
    }

    @Test
    fun `long pages are split with overlap and nothing is lost`() {
        val words = (1..25).map { "w$it" }
        val chunks = TextChunker.chunk(listOf(words.joinToString(" ")), maxWords = 10, overlapWords = 2)
        assertEquals(listOf(0, 1, 2), chunks.map { it.part })
        assertEquals("w9 w10", chunks[1].text.split(" ").take(2).joinToString(" "))
        assertEquals(words.toSet(), chunks.flatMap { it.text.split(" ") }.toSet())
    }
}

class RuleBasedExplainerTest {

    @Test
    fun `finds the essential elements with verbatim quotes`() {
        val result = RuleBasedExplainer.explain(
            listOf(
                "CONTRACT OF LEASE\nThe Lessor leases the house located at Lot 5, Davao City.",
                "Monthly rent: PHP 12,000.00\nIN WITNESS WHEREOF the parties have signed.",
            )
        )
        assertEquals(DetectedContractType.LEASE, result.contractType)
        assertTrue(result.consentLocated && result.objectLocated && result.causeLocated)
        assertTrue(result.missingElements.isEmpty())
        assertTrue(result.findings.all { f -> f.quote == null || f.quote.isNotBlank() })
    }

    @Test
    fun `reports what is missing instead of guessing`() {
        val result = RuleBasedExplainer.explain(listOf("This is an agreement between Juan and Maria."))
        assertFalse(result.causeLocated)
        assertEquals(3, result.missingElements.size)
    }

    @Test
    fun `flags an unnotarized sale of real property as a form issue, not a void contract`() {
        val result = RuleBasedExplainer.explain(
            listOf("DEED OF ABSOLUTE SALE of the land covered by TCT No. 123 for PHP 1,000,000. Signed.")
        )
        val form = result.findings.single { it.code == ReasonCode.FORM_NOT_MET }
        assertTrue(form.finding.contains("compel"))
    }

    @Test
    fun `notes a loan without a written interest stipulation`() {
        val result = RuleBasedExplainer.explain(
            listOf("LOAN AGREEMENT. The Borrower received PHP 50,000 from the Lender. Signed.")
        )
        assertEquals(DetectedContractType.LOAN, result.contractType)
        assertTrue(result.findings.any { it.finding.contains("interest") })
    }
}

class ReportBuilderTest {

    @Test
    fun `article references are stripped from model text`() {
        val stripped = ReportBuilder.stripArticles("Void under Article 1409 of the Civil Code.")
        assertEquals("Void under the Civil Code.", stripped)
        assertEquals("Voidable.", ReportBuilder.stripArticles("Voidable (Arts. 1390-1402)."))
    }

    @Test
    fun `summaries are limited to 120 words`() {
        val long = List(130) { "word" }.joinToString(" ")
        assertEquals(120, ReportBuilder.limitWords(long, 120).split(" ").size)
        assertTrue(ReportBuilder.limitWords(long, 120).endsWith("…"))
    }

    @Test
    fun `rule-based reports never show a verdict`() {
        val text = ExtractedText(
            listOf("CONTRACT OF LEASE. Lessor and Lessee. Rent PHP 5,000. Signed."),
            emptyList(),
            listOf(1f),
        )
        val report = ReportBuilder.fromRules(text, prediction = null)
        assertNull(report.shownClassification)
        assertTrue(report.confidence.total < ConfidenceScore.SHOW_THRESHOLD)
    }
}
