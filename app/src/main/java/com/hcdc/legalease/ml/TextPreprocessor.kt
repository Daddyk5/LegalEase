package com.hcdc.legalease.ml

import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * PAD = 0, OOV = 1. Keep maxLength in sync with model input shape [1, maxLength].
 */
fun preprocessTextToIds(
    text: String,
    vocab: Map<String, Int>,
    maxLength: Int = 100,
    padId: Int = 0,
    oovId: Int = 1
): IntArray {
    val tokens = text
        .lowercase(Locale.ROOT)
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotEmpty() }

    val ids = tokens.map { tok -> vocab[tok] ?: oovId }
    val out = IntArray(maxLength) { padId }
    val n = ids.size.coerceAtMost(maxLength)
    for (i in 0 until n) out[i] = ids[i]
    return out
}
