package com.hcdc.legalease.ml

fun preprocessTextToFloatArray(text: String): FloatArray {
    val maxLength = 100
    val normalizedAscii = text
        .take(maxLength)
        .map { it.code.coerceIn(32, 126).toFloat() / 126f }

    val result = FloatArray(maxLength) { 0f }
    for (i in normalizedAscii.indices) {
        result[i] = normalizedAscii[i]
    }

    return result
}
