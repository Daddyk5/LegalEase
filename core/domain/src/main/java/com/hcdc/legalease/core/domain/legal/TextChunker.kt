package com.hcdc.legalease.core.domain.legal

/** A slice of one page sent to the model for clause extraction. */
data class TextChunk(val page: Int, val part: Int, val text: String)

/**
 * Splits pages into model-sized chunks. Chunks never span pages, so every extracted clause keeps
 * its page number; long pages are split with overlap so a clause on a boundary is seen whole.
 *
 * Token counts are estimated at about 1.33 tokens per English word, so the defaults of 750 words
 * with 75 words of overlap give chunks of roughly 1,000 tokens.
 */
object TextChunker {
    const val DEFAULT_MAX_WORDS = 750
    const val DEFAULT_OVERLAP_WORDS = 75

    fun chunk(
        pages: List<String>,
        maxWords: Int = DEFAULT_MAX_WORDS,
        overlapWords: Int = DEFAULT_OVERLAP_WORDS,
    ): List<TextChunk> {
        require(maxWords > overlapWords && overlapWords >= 0) { "Overlap must be smaller than the chunk size" }
        return pages.flatMapIndexed { index, text ->
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.isEmpty()) return@flatMapIndexed emptyList()
            val step = maxWords - overlapWords
            buildList {
                var start = 0
                var part = 0
                while (true) {
                    val end = minOf(start + maxWords, words.size)
                    add(TextChunk(page = index + 1, part = part++, text = words.subList(start, end).joinToString(" ")))
                    if (end == words.size) break
                    start += step
                }
            }
        }
    }
}
