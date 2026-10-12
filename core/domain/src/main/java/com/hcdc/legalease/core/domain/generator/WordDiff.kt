package com.hcdc.legalease.core.domain.generator

/** One run of a diff. [text] keeps the original spacing between its words. */
data class DiffSegment(val type: Type, val text: String) {
    enum class Type { SAME, ADDED, REMOVED }
}

/** Word-level diff (longest common subsequence), for showing an assistant edit before the user accepts it. */
object WordDiff {

    fun diff(before: String, after: String): List<DiffSegment> {
        val a = words(before)
        val b = words(after)
        // lcs[i][j] = length of the LCS of a[i..] and b[j..]
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) {
            for (j in b.indices.reversed()) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }
        val out = mutableListOf<DiffSegment>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> {
                    out.add(DiffSegment.Type.SAME, a[i])
                    i++
                    j++
                }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> {
                    out.add(DiffSegment.Type.ADDED, b[j])
                    j++
                }
                else -> {
                    out.add(DiffSegment.Type.REMOVED, a[i])
                    i++
                }
            }
        }
        return out
    }

    /** True when the two texts differ in more than spacing. */
    fun changes(before: String, after: String) = words(before) != words(after)

    private fun words(text: String) = text.trim().split(WHITESPACE).filter { it.isNotEmpty() }

    /** Appends a word, merging it into the previous segment when the type matches. */
    private fun MutableList<DiffSegment>.add(type: DiffSegment.Type, word: String) {
        val last = lastOrNull()
        if (last?.type == type) {
            this[lastIndex] = last.copy(text = last.text + " " + word)
        } else {
            add(DiffSegment(type, word))
        }
    }

    private val WHITESPACE = Regex("""\s+""")
}
