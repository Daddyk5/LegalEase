package com.hcdc.legalease.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClassificationTest {

    @Test
    fun `labels map case-insensitively`() {
        assertEquals(Classification.VOIDABLE, Classification.fromLabelOrNull(" voidable "))
        assertEquals(Classification.RESCISSIBLE, Classification.fromLabelOrNull("RESCISSIBLE"))
    }

    @Test
    fun `unknown labels are not silently treated as enforceable`() {
        assertNull(Classification.fromLabelOrNull("Valid"))
        assertNull(Classification.fromLabelOrNull(""))
    }

    @Test
    fun `model output indexes follow declaration order`() {
        assertEquals(
            listOf("Void", "Voidable", "Unenforceable", "Rescissible", "Enforceable"),
            (0..4).map { Classification.fromIndexOrNull(it)?.label }
        )
        assertNull(Classification.fromIndexOrNull(5))
        assertNull(Classification.fromIndexOrNull(-1))
    }
}
