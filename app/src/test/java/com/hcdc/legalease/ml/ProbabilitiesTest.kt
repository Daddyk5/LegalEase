package com.hcdc.legalease.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbabilitiesTest {

    @Test
    fun `softmax output is passed through unchanged`() {
        val probs = floatArrayOf(0.7f, 0.1f, 0.1f, 0.05f, 0.05f)
        assertArrayEquals(probs, toProbabilities(probs), 0f)
    }

    @Test
    fun `logits are normalised with softmax`() {
        val probs = toProbabilities(floatArrayOf(2f, 1f, 0f))
        assertEquals(1f, probs.sum(), 1e-5f)
        assertEquals(0.6652f, probs[0], 1e-4f)
        assertEquals(0.2447f, probs[1], 1e-4f)
    }

    @Test
    fun `large logits do not overflow`() {
        val probs = toProbabilities(floatArrayOf(1000f, 999f))
        assertTrue(probs.all { it.isFinite() })
        assertEquals(1f, probs.sum(), 1e-5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `non-finite output is rejected`() {
        toProbabilities(floatArrayOf(Float.NaN, 1f))
    }
}
