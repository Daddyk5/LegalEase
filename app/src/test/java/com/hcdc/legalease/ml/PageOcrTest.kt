package com.hcdc.legalease.ml

import com.google.mlkit.common.MlKitException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PageOcrTest {

    @Test
    fun `reads pages in order and records unreadable ones`() = runTest {
        val progress = mutableListOf<Int>()
        val result = ocrPages(pageCount = 3, onPage = { progress += it }) { index ->
            when (index) {
                1 -> throw MlKitException("failed", MlKitException.INTERNAL)
                else -> "page $index"
            }
        }
        assertEquals(listOf("page 0", "", "page 2"), result.pages)
        assertEquals(listOf(1), result.unreadablePages)
        assertEquals(listOf(1, 2, 3), progress)
    }

    @Test
    fun `document with only blank pages has no text`() = runTest {
        val result = ocrPages(pageCount = 2, onPage = {}) { " " }
        assertFalse(result.hasText)
        assertEquals(listOf(0, 1), result.unreadablePages)
    }
}
