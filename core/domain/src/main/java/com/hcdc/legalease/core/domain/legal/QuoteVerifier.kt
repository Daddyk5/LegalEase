package com.hcdc.legalease.core.domain.legal

/**
 * Checks that a quote the model attributes to the document really appears in the OCR text,
 * tolerating OCR noise. Quotes that fail are dropped, never shown.
 */
object QuoteVerifier {

    const val MIN_SIMILARITY = 0.85
    const val MAX_QUOTE_WORDS = 40

    /** Where a verified quote was found. */
    data class Match(val page: Int, val similarity: Double)

    /**
     * @param pages OCR text per page (index 0 = page 1).
     * @param claimedPage 1-based page the model cited; searched first.
     * @return the best match at or above [MIN_SIMILARITY], or null.
     */
    fun verify(quote: String, pages: List<String>, claimedPage: Int?): Match? {
        val quoteWords = words(quote)
        if (quoteWords.isEmpty() || quoteWords.size > MAX_QUOTE_WORDS) return null
        val order = buildList {
            claimedPage?.minus(1)?.takeIf { it in pages.indices }?.let(::add)
            addAll(pages.indices.filter { it != claimedPage?.minus(1) })
        }
        var best: Match? = null
        for (index in order) {
            val similarity = bestWindowSimilarity(quoteWords, words(pages[index]))
            if (similarity >= MIN_SIMILARITY && (best == null || similarity > best.similarity)) {
                best = Match(index + 1, similarity)
                if (similarity == 1.0) break
            }
        }
        return best
    }

    /** Similarity of the quote to its best-matching window of the page, in [0, 1]. */
    internal fun bestWindowSimilarity(quote: List<String>, page: List<String>): Double {
        if (page.isEmpty()) return 0.0
        val target = quote.joinToString(" ")
        if (page.joinToString(" ").contains(target)) return 1.0
        val quoteSet = quote.toSet()
        var best = 0.0
        val lengths = (quote.size - quote.size / LENGTH_SLACK_DIVISOR..quote.size + quote.size / LENGTH_SLACK_DIVISOR)
            .filter { it in 1..page.size }
        for (length in lengths) {
            for (start in 0..page.size - length) {
                val window = page.subList(start, start + length)
                // Cheap filter before the character-level comparison.
                if (window.count { it in quoteSet } * 2 < quote.size) continue
                best = maxOf(best, similarity(target, window.joinToString(" ")))
                if (best == 1.0) return best
            }
        }
        return best
    }

    /** 1 − normalised Levenshtein distance. */
    internal fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val longest = maxOf(a.length, b.length)
        if (longest == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / longest
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, previous[j - 1] + cost)
            }
            previous = current.also { current = previous }
        }
        return previous[b.length]
    }

    /** Lower-cased words with punctuation removed, so OCR spacing and quote marks do not matter. */
    internal fun words(text: String): List<String> =
        text.lowercase().replace(Regex("[^\\p{L}\\p{N}₱%]+"), " ").trim().split(' ').filter { it.isNotEmpty() }

    /** Windows may be up to 20% shorter or longer than the quote, to absorb split or merged OCR words. */
    private const val LENGTH_SLACK_DIVISOR = 5
}
