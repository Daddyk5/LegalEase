package com.hcdc.legalease.core.data.remote

import com.hcdc.legalease.core.data.local.AnalysisEntity
import com.hcdc.legalease.core.data.local.GeneratedContractEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FirestoreMappersTest {

    private val entity = AnalysisEntity(
        id = "a1", documentName = "lease.pdf", sourceKind = "PDF", status = "COMPLETED",
        createdAt = 10, updatedAt = 20, pageCount = 3, unreadablePages = "2",
        classification = "Void", confidence = 0.91f, summary = "s", needsSync = true,
    )

    @Test
    fun `analysis round-trips and arrives marked as synced`() {
        val restored = analysisFromFirestore("a1", entity.toFirestore())!!
        assertEquals(entity.copy(needsSync = false, remoteUpdatedAt = 20, confidence = restored.confidence), restored)
        assertEquals(0.91f, restored.confidence!!, 1e-6f)
    }

    @Test
    fun `uploaded fields are an explicit allow-list`() {
        assertEquals(
            setOf(
                "schemaVersion", "documentName", "sourceKind", "status", "createdAt", "updatedAt", "pageCount",
                "unreadablePages", "classification", "confidence", "summary", "errorCode", "deleted",
            ),
            entity.toFirestore().keys,
        )
    }

    @Test
    fun `tombstones carry no document content`() {
        val tombstone = entity.copy(deleted = true).toFirestore()
        assertEquals(setOf("schemaVersion", "updatedAt", "deleted"), tombstone.keys)
        assertTrue(analysisFromFirestore("a1", tombstone)!!.deleted)
    }

    @Test
    fun `malformed documents are skipped`() {
        assertNull(analysisFromFirestore("a1", mapOf("updatedAt" to 5L)))
        assertNull(analysisFromFirestore("a1", entity.toFirestore() - "updatedAt"))
    }

    @Test
    fun `contract versions round-trip`() {
        val contract = GeneratedContractEntity("v1", "c1", 2, "LEASE", "Lease", "body", 30, needsSync = true)
        val restored = contractFromFirestore("v1", contract.toFirestore())!!
        assertEquals(contract.copy(needsSync = false), restored)
        assertFalse(restored.needsSync)
    }
}
