package com.hcdc.legalease.core.data.repository

import com.hcdc.legalease.core.data.local.AuditEntryEntity
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.AuditEntry
import com.hcdc.legalease.core.domain.model.AuditEvent
import com.hcdc.legalease.core.domain.model.ConfidenceComponents
import com.hcdc.legalease.core.domain.model.ConfidenceScore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RoomAuditLogTest {

    @Test
    fun `entries round-trip with every confidence component`() {
        val entry = AuditEntry(
            timestampMillis = 42,
            event = AuditEvent.ANALYSIS_COMPLETED,
            subjectId = "a1",
            modelVersion = "primary:0123456789ab",
            method = AnalysisMethod.LANGUAGE_MODEL,
            confidence = ConfidenceScore(0.86f, ConfidenceComponents(0.9f, 1f, 0.5f, 0.8f, 1f)),
            scoringVersion = 2,
        )
        assertEquals(entry, entry.toEntity().toDomain())
    }

    @Test
    fun `entries without scores or with unknown events are handled honestly`() {
        val saved = AuditEntry(1, AuditEvent.CONTRACT_SAVED, subjectId = "v1", detail = "LEASE")
        assertEquals(saved, saved.toEntity().toDomain())
        assertNull(AuditEntryEntity(timestamp = 1, event = "SOMETHING_NEW").toDomain())
    }
}
