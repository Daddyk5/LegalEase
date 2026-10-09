package com.hcdc.legalease.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentRepositoryTest {

    @Test
    fun `saved documents are retrievable by id`() {
        val repository = DocumentRepository()
        val id = repository.save("lease.pdf", listOf("page one", "", "page three"), unreadablePages = listOf(1))
        val document = repository.get(id)!!
        assertEquals("lease.pdf", document.name)
        assertEquals(listOf(1), document.unreadablePages)
        assertEquals("page one\n\npage three", document.fullText)
    }

    @Test
    fun `ids are unique and unknown ids return null`() {
        val repository = DocumentRepository()
        assertNotEquals(repository.save("a", listOf("x"), emptyList()), repository.save("a", listOf("x"), emptyList()))
        assertNull(repository.get("missing"))
    }
}
