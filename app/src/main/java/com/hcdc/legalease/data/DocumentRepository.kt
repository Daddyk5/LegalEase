package com.hcdc.legalease.data

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds scanned document text so that navigation only carries an analysis ID.
 *
 * In-memory for now: contents do not survive process death, and callers must handle
 * a missing ID. Persistent, encrypted storage replaces this in the data layer.
 */
class DocumentRepository {

    private val documents = ConcurrentHashMap<String, ScannedDocument>()

    fun save(name: String, pages: List<String>, unreadablePages: List<Int>): String {
        val id = UUID.randomUUID().toString()
        documents[id] = ScannedDocument(id, name, pages, unreadablePages)
        return id
    }

    fun get(id: String): ScannedDocument? = documents[id]
}
