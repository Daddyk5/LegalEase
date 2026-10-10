package com.hcdc.legalease.core.data.local

import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.result.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {

    private val base = AnalysisEntity(
        id = "a1", documentName = "lease.pdf", sourceKind = "PDF", status = "COMPLETED",
        createdAt = 1, updatedAt = 2, pageCount = 3, unreadablePages = "1,2",
        classification = "Voidable", confidence = 0.83f, summary = "s",
    )

    @Test
    fun `completed row maps to a verdict with the stored confidence`() {
        val analysis = base.toDomain()
        assertEquals(AnalysisStatus.COMPLETED, analysis.status)
        assertEquals(Classification.VOIDABLE, analysis.verdict?.classification)
        assertEquals(0.83f, analysis.verdict?.confidence)
        assertEquals(listOf(1, 2), analysis.unreadablePages)
    }

    @Test
    fun `completed row with an unknown label is shown as failed, not given a guessed verdict`() {
        val analysis = base.copy(classification = "Valid").toDomain()
        assertEquals(AnalysisStatus.FAILED, analysis.status)
        assertNull(analysis.verdict)
        assertEquals(AppError.Unexpected("unreadable_verdict"), analysis.error)
    }

    @Test
    fun `completed row without confidence has no verdict`() {
        assertNull(base.copy(confidence = null).toDomain().verdict)
    }

    @Test
    fun `failed row carries its error`() {
        val analysis = base.copy(status = "FAILED", classification = null, errorCode = "no_text_found").toDomain()
        assertEquals(AppError.NoTextFound, analysis.error)
    }

    @Test
    fun `page lists round-trip and tolerate blanks`() {
        assertEquals(listOf(0, 4), decodePageList(encodePageList(listOf(0, 4))))
        assertEquals(emptyList<Int>(), decodePageList(""))
    }

    @Test
    fun `like wildcards are escaped`() {
        assertEquals("100\\% \\_draft\\\\", escapeLike("100% _draft\\"))
    }
}
