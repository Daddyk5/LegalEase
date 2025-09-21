package com.hcdc.legalease.ml

fun preprocessTextToIds(
    text: String,
    vocab: Map<String, Int>,
    maxLength: Int = 100
): IntArray {
    val tokens = text.lowercase().split("\\s+".toRegex())
    val ids = IntArray(maxLength) { 0 }

    for (i in 0 until minOf(tokens.size, maxLength)) {
        ids[i] = vocab[tokens[i]] ?: 1 // 1 = OOV token
    }

    return ids
}
