package com.hcdc.legalease.core.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * @property needsSync true when local changes have not been pushed to Firestore yet.
 * @property remoteUpdatedAt [updatedAt] of the last version Firestore has; null if it was never uploaded.
 * @property deleted tombstone kept until the deletion is pushed, so other devices learn of it.
 */
@Entity(tableName = "analyses", indices = [Index("createdAt"), Index("needsSync")])
data class AnalysisEntity(
    @PrimaryKey val id: String,
    val documentName: String,
    val sourceKind: String,
    val status: String,
    val createdAt: Long,
    val updatedAt: Long,
    val pageCount: Int? = null,
    val currentPage: Int? = null,
    /** Comma-separated zero-based page indexes. */
    val unreadablePages: String = "",
    val classification: String? = null,
    val confidence: Float? = null,
    val summary: String? = null,
    val errorCode: String? = null,
    val needsSync: Boolean = false,
    val remoteUpdatedAt: Long? = null,
    val deleted: Boolean = false,
)

/** OCR text per page. Stays on the device: never synced to Firestore. */
@Entity(
    tableName = "document_pages",
    primaryKeys = ["analysisId", "pageIndex"],
    foreignKeys = [
        ForeignKey(
            entity = AnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class DocumentPageEntity(
    val analysisId: String,
    val pageIndex: Int,
    val text: String,
)

/** One immutable version of a generated contract. */
@Entity(
    tableName = "generated_contracts",
    indices = [Index(value = ["contractId", "version"], unique = true), Index("needsSync")],
)
data class GeneratedContractEntity(
    @PrimaryKey val id: String,
    val contractId: String,
    val version: Int,
    val type: String,
    val title: String,
    val body: String,
    val createdAt: Long,
    val needsSync: Boolean = false,
)
