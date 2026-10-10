package com.hcdc.legalease.core.ml.ocr

/** One recognised line with ML Kit's confidence in [0, 1]. */
data class OcrLine(val text: String, val confidence: Float)

/**
 * Scores a page's OCR quality in [0, 1] from two signals:
 * - recognition confidence (70%): ML Kit's per-line confidence, weighted by line length;
 * - readable characters (30%): the share of characters expected in Philippine contracts
 *   (letters, digits, punctuation, ₱ and %); garbage from a bad scan lowers it.
 *
 * Pages below [com.hcdc.legalease.core.domain.model.POOR_QUALITY] are flagged for rescanning.
 */
object OcrQuality {

    private const val W_CONFIDENCE = 0.7f
    private const val W_READABLE = 0.3f
    private const val ALLOWED_SYMBOLS = ".,;:!?()[]'\"-–—/&₱%#@*+=°§"

    fun score(lines: List<OcrLine>): Float {
        val chars = lines.sumOf { line -> line.text.count { !it.isWhitespace() } }
        if (chars == 0) return 0f
        val confidence = lines.sumOf { line ->
            line.text.count { !it.isWhitespace() } * line.confidence.coerceIn(0f, 1f).toDouble()
        } / chars
        val readable = 1f - unreadableRatio(lines.joinToString("\n") { it.text })
        return W_CONFIDENCE * confidence.toFloat() + W_READABLE * readable
    }

    /** Share of non-space characters that are not letters, digits, or common contract punctuation. */
    fun unreadableRatio(text: String): Float {
        val chars = text.filterNot { it.isWhitespace() }
        if (chars.isEmpty()) return 1f
        val unreadable = chars.count { !it.isLetterOrDigit() && it !in ALLOWED_SYMBOLS }
        return unreadable.toFloat() / chars.length
    }
}
