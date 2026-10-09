package com.hcdc.legalease.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class TextPreprocessorTest {

    private val vocab = mapOf("lease" to 2, "contract" to 3)

    @Test
    fun `maps known tokens case-insensitively and unknown tokens to OOV`() {
        val ids = preprocessTextToIds("Lease  CONTRACT unknown", vocab, maxLength = 5)
        assertArrayEquals(intArrayOf(2, 3, 1, 0, 0), ids)
    }

    @Test
    fun `truncates to maxLength`() {
        val ids = preprocessTextToIds("lease contract lease contract", vocab, maxLength = 2)
        assertArrayEquals(intArrayOf(2, 3), ids)
    }

    @Test
    fun `blank text is all padding`() {
        assertArrayEquals(intArrayOf(0, 0, 0), preprocessTextToIds("   \n ", vocab, maxLength = 3))
    }
}
