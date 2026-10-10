package com.hcdc.legalease.core.domain.generator

/** Why an assistant edit was withheld. */
enum class ClauseRejection {
    EMPTY,
    TOO_LONG,

    /** It refers to a statute, article, case, or "the law". The assistant may never supply law. */
    CITES_LAW,

    /** A suggested clause states a peso amount or a date; those belong to the mandatory terms. */
    STATES_AMOUNT_OR_DATE,

    /** A rewrite changed, added, or dropped a number. Rewrites may only change wording. */
    CHANGES_NUMBERS,

    /** A rewrite changed which parties (LESSOR, LESSEE, ...) the clause mentions. */
    CHANGES_PARTIES,
}

/**
 * Checks assistant output before the user ever sees it. The assistant may only suggest optional
 * clauses and reword them; anything that looks like law, money, dates, or a change of substance is
 * withheld, and the user is told how many suggestions were withheld.
 */
object ClauseGuard {

    const val MAX_TITLE_CHARS = 60
    const val MAX_CLAUSE_WORDS = 150
    private const val REWRITE_GROWTH_WORDS = 20

    private val LAW = Regex(
        """(?i)\b(?:arts?\.?\s*\d+|articles?\s+\d+|civil\s+code|family\s+code|labor\s+code|republic\s+act|""" +
            """r\.\s?a\.|presidential\s+decree|p\.\s?d\.\s*(no\.?\s*)?\d+|batas\s+pambansa|""" +
            """rules\s+of\s+court|supreme\s+court|g\.\s?r\.\s*no|jurisprudence|statute|""" +
            """(under|by|of)\s+(the\s+)?(applicable\s+)?law|as\s+provided\s+by\s+law)(?!\w)"""
    )
    private val MONEY_OR_DATE = Regex(
        """(?i)(\bphp\b|₱|\bpesos?\b|\bp\s?\d[\d,]*(\.\d+)?\b|\b\d{4}-\d{2}-\d{2}\b|""" +
            """\b(january|february|march|april|may|june|july|august|september|october|november|december)\s+\d{1,2}\b)"""
    )
    private val NUMBER = Regex("""\d+([.,]\d+)*""")
    private val ROLE = Regex("""\b(LESSOR|LESSEE|LENDER|BORROWER|VENDOR|VENDEE)\b""")
    private val WHITESPACE = Regex("""\s+""")

    fun checkSuggestion(title: String, text: String): ClauseRejection? = when {
        title.isBlank() || text.isBlank() -> ClauseRejection.EMPTY
        title.length > MAX_TITLE_CHARS || wordCount(text) > MAX_CLAUSE_WORDS -> ClauseRejection.TOO_LONG
        LAW.containsMatchIn(title) || LAW.containsMatchIn(text) -> ClauseRejection.CITES_LAW
        MONEY_OR_DATE.containsMatchIn(text) -> ClauseRejection.STATES_AMOUNT_OR_DATE
        else -> null
    }

    fun checkRewrite(original: String, rewritten: String): ClauseRejection? = when {
        rewritten.isBlank() -> ClauseRejection.EMPTY
        wordCount(rewritten) > maxOf(MAX_CLAUSE_WORDS, wordCount(original) + REWRITE_GROWTH_WORDS) ->
            ClauseRejection.TOO_LONG
        LAW.containsMatchIn(rewritten) && !LAW.containsMatchIn(original) -> ClauseRejection.CITES_LAW
        numbers(original) != numbers(rewritten) -> ClauseRejection.CHANGES_NUMBERS
        roles(original) != roles(rewritten) -> ClauseRejection.CHANGES_PARTIES
        else -> null
    }

    private fun wordCount(text: String) = text.trim().split(WHITESPACE).count { it.isNotEmpty() }

    private fun numbers(text: String) = NUMBER.findAll(text).map { it.value.replace(",", "") }.sorted().toList()

    private fun roles(text: String) = ROLE.findAll(text).map { it.value }.toSet()
}
