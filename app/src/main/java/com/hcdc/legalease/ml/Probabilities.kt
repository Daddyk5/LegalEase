package com.hcdc.legalease.ml

import kotlin.math.abs
import kotlin.math.exp

private const val PROBABILITY_SUM_TOLERANCE = 1e-3

/**
 * Converts raw model output to class probabilities.
 *
 * If the model already ends in a softmax layer (every value in [0, 1], summing to 1),
 * the values are returned unchanged; applying softmax twice would flatten the
 * distribution and understate the model's real confidence. Otherwise the values are
 * treated as logits and passed through a numerically stable softmax.
 */
fun toProbabilities(raw: FloatArray): FloatArray {
    require(raw.isNotEmpty()) { "Model output is empty" }
    require(raw.all { it.isFinite() }) { "Model output contains non-finite values" }
    if (isProbabilityDistribution(raw)) return raw.copyOf()

    val max = raw.max()
    val exps = DoubleArray(raw.size) { exp((raw[it] - max).toDouble()) }
    val sum = exps.sum()
    return FloatArray(raw.size) { (exps[it] / sum).toFloat() }
}

internal fun isProbabilityDistribution(values: FloatArray): Boolean =
    values.all { it in 0f..1f } && abs(values.sum() - 1f) <= PROBABILITY_SUM_TOLERANCE
