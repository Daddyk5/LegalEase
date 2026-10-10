package com.hcdc.legalease.core.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfRasterizerTest {

    @Test
    fun `letter page longest side is capped at 2000px`() {
        assertEquals(RenderSize(1545, 2000), renderSizeFor(pageWidth = 612, pageHeight = 792))
    }

    @Test
    fun `landscape pages cap the width`() {
        assertEquals(RenderSize(2000, 1413), renderSizeFor(pageWidth = 842, pageHeight = 595))
    }

    @Test
    fun `oversized pages are scaled down`() {
        assertEquals(RenderSize(1000, 2000), renderSizeFor(pageWidth = 5000, pageHeight = 10000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `zero-area pages are rejected`() {
        renderSizeFor(pageWidth = 0, pageHeight = 792)
    }
}
