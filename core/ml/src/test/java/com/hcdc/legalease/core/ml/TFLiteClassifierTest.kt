package com.hcdc.legalease.core.ml

import org.junit.Assert.assertEquals
import org.junit.Test
import org.tensorflow.lite.DataType

class TFLiteClassifierTest {

    @Test
    fun `encodes int32 input`() {
        val buffer = TFLiteClassifier.encodeInput(intArrayOf(2, 3, 0), DataType.INT32)
        assertEquals(12, buffer.capacity())
        assertEquals(listOf(2, 3, 0), List(3) { buffer.int })
    }

    @Test
    fun `encodes float32 input`() {
        val buffer = TFLiteClassifier.encodeInput(intArrayOf(7, 1), DataType.FLOAT32)
        assertEquals(listOf(7f, 1f), List(2) { buffer.float })
    }
}
