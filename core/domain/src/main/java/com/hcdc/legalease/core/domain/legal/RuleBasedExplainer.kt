package com.hcdc.legalease.core.domain.legal

import com.hcdc.legalease.core.domain.model.DetectedContractType
import com.hcdc.legalease.core.domain.model.ReasonCode

/**
 * Explanation used when the language model cannot run. It only reports what is visibly present or
 * absent in the OCR text, quoting the document verbatim; it does not decide validity. With this path
 * the confidence score cannot reach the display threshold, so the result is always "needs review".
 */
object RuleBasedExplainer {

    data class Finding(val finding: String, val quote: String?, val page: Int?, val code: ReasonCode)

    data class Result(
        val contractType: DetectedContractType,
        val findings: List<Finding>,
        val missingElements: List<String>,
        val consentLocated: Boolean,
        val objectLocated: Boolean,
        val causeLocated: Boolean,
        val notarized: Boolean,
    )

    private val SIGNATURE = Regex("""(?i)\b(in witness whereof|signed|signature|conforme|subscribed)\b""")
    private val NOTARY = Regex("""(?i)\b(notary public|acknowledg(e)?ment|doc\.?\s*no\.?|subscribed and sworn)\b""")
    private val MONEY = Regex("""(?i)(₱|\bphp\b|\bp\b)\s?\d[\d,]*(\.\d{2})?|\bpesos\b""")
    private val OBJECT = Regex(
        """(?i)\b(property|premises|parcel|lot|unit|located at|transfer certificate|tct|vehicle|plate no|""" +
            """motor|equipment|machine|principal|the sum of)\b"""
    )
    private val INTEREST = Regex(
        """(?i)\binterest\b[^.\n]{0,60}\d+(\.\d+)?\s?%|\d+(\.\d+)?\s?%[^.\n]{0,60}\binterest\b"""
    )
    private val REAL_PROPERTY = Regex(
        """(?i)\b(land|lot|house|building|condominium|transfer certificate|tct|real property)\b"""
    )

    fun explain(pages: List<String>): Result {
        val all = pages.joinToString("\n")
        val type = detectType(all)
        val consent = firstLine(pages, SIGNATURE)
        val objectLine = firstLine(pages, OBJECT)
        val cause = firstLine(pages, MONEY)
        val notary = firstLine(pages, NOTARY)

        val findings = buildList {
            fun located(finding: String, at: Pair<Int, String>?) {
                at?.let { add(Finding(finding, it.second, it.first, ReasonCode.NONE)) }
            }
            located("Signature or conformity wording found.", consent)
            located("A description of the object was found.", objectLine)
            located("A price, rent, or amount was found.", cause)
            located("A notarial acknowledgment was found.", notary)
            if (type == DetectedContractType.SALE && REAL_PROPERTY.containsMatchIn(all) && notary == null) {
                add(
                    Finding(
                        "This appears to be a sale of real property, but no notarial acknowledgment was found. " +
                            "Such a sale must appear in a public document, though the parties may compel each " +
                            "other to execute one.",
                        null, null, ReasonCode.FORM_NOT_MET,
                    )
                )
            }
            if (type == DetectedContractType.LOAN && !INTEREST.containsMatchIn(all)) {
                add(
                    Finding(
                        "No written interest stipulation was found; interest is due only if expressly " +
                            "stipulated in writing.",
                        null, null, ReasonCode.NONE,
                    )
                )
            }
        }
        val missing = buildList {
            if (consent == null) add("Signatures or other evidence of the parties' consent")
            if (objectLine == null) add("A clear description of the object of the contract")
            if (cause == null) add("The price, rent, loan amount, or other cause")
        }
        return Result(type, findings, missing, consent != null, objectLine != null, cause != null, notary != null)
    }

    internal fun detectType(text: String): DetectedContractType {
        val t = text.lowercase()
        val lease = Regex("""\b(lease|lessor|lessee|rent(al)?)\b""").containsMatchIn(t)
        val personal = Regex("""\b(vehicle|motor|equipment|machine|car|truck)\b""").containsMatchIn(t)
        return when {
            Regex("""\b(loan|borrower|lender|mutuum|promissory)\b""").containsMatchIn(t) -> DetectedContractType.LOAN
            Regex("""\b(deed of (absolute )?sale|vendor|vendee)\b""").containsMatchIn(t) -> DetectedContractType.SALE
            lease && personal -> DetectedContractType.LEASE_OF_PERSONAL_PROPERTY
            lease -> DetectedContractType.LEASE
            else -> DetectedContractType.OTHER
        }
    }

    /** First line matching [pattern], as (1-based page, verbatim line trimmed to the quote limit). */
    private fun firstLine(pages: List<String>, pattern: Regex): Pair<Int, String>? {
        pages.forEachIndexed { index, page ->
            page.lineSequence().firstOrNull { pattern.containsMatchIn(it) }?.let { line ->
                val words = line.trim().split(Regex("\\s+"))
                return index + 1 to words.take(QuoteVerifier.MAX_QUOTE_WORDS).joinToString(" ")
            }
        }
        return null
    }
}
