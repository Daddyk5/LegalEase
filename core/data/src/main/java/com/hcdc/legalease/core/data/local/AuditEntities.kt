package com.hcdc.legalease.core.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * One audit-trail row. There is deliberately no column for document text, names, or quotes: only
 * ids, model identity, and the numbers behind a result.
 */
@Entity(tableName = "audit_log", indices = [Index("timestamp")])
data class AuditEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val event: String,
    val subjectId: String? = null,
    val modelVersion: String? = null,
    val method: String? = null,
    val confidence: Float? = null,
    val agreement: Float? = null,
    val selfConsistency: Float? = null,
    val grounding: Float? = null,
    val ocrQuality: Float? = null,
    val completeness: Float? = null,
    val scoringVersion: Int? = null,
    val detail: String? = null,
)

@Dao
interface AuditDao {
    @Insert
    suspend fun insert(entry: AuditEntryEntity)

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC")
    fun observe(): Flow<List<AuditEntryEntity>>

    @Query("SELECT * FROM audit_log ORDER BY timestamp DESC, id DESC")
    suspend fun all(): List<AuditEntryEntity>
}
