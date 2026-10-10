package com.hcdc.legalease.core.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AnalysisDao {

    @Query("SELECT * FROM analyses WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<AnalysisEntity?>

    @Query("SELECT * FROM analyses WHERE id = :id")
    suspend fun get(id: String): AnalysisEntity?

    @Upsert
    suspend fun upsert(entity: AnalysisEntity)

    @Query(
        "UPDATE analyses SET status = 'EXTRACTING_TEXT', currentPage = :currentPage, pageCount = :pageCount, " +
            "updatedAt = :now WHERE id = :id"
    )
    suspend fun markExtracting(id: String, currentPage: Int, pageCount: Int, now: Long)

    @Query("UPDATE analyses SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: String, status: String, now: Long)

    @Query(
        "UPDATE analyses SET status = 'COMPLETED', classification = :classification, confidence = :confidence, " +
            "summary = :summary, errorCode = NULL, currentPage = NULL, updatedAt = :now, needsSync = 1 WHERE id = :id"
    )
    suspend fun complete(id: String, classification: String, confidence: Float, summary: String, now: Long)

    @Query(
        "UPDATE analyses SET status = 'FAILED', errorCode = :errorCode, currentPage = NULL, " +
            "updatedAt = :now, needsSync = 1 WHERE id = :id"
    )
    suspend fun fail(id: String, errorCode: String, now: Long)

    @Query("UPDATE analyses SET pageCount = :pageCount, unreadablePages = :unreadablePages WHERE id = :id")
    suspend fun setPageInfo(id: String, pageCount: Int, unreadablePages: String)

    /**
     * Completed and failed analyses, newest first. [query] matches the document name or
     * classification label; `%` and `_` in it are matched literally.
     */
    @Query(
        "SELECT * FROM analyses WHERE deleted = 0 AND status IN ('COMPLETED', 'FAILED') AND (" +
            "documentName LIKE '%' || :query || '%' ESCAPE '\\' OR " +
            "classification LIKE '%' || :query || '%' ESCAPE '\\') ORDER BY createdAt DESC"
    )
    fun observeHistory(query: String): Flow<List<AnalysisEntity>>

    @Query(
        "UPDATE analyses SET deleted = 1, needsSync = 1, summary = NULL, documentName = '', " +
            "updatedAt = :now WHERE id = :id"
    )
    suspend fun markDeleted(id: String, now: Long)

    @Query("DELETE FROM analyses WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT * FROM analyses WHERE needsSync = 1")
    suspend fun pendingSync(): List<AnalysisEntity>

    /** Clears [AnalysisEntity.needsSync] only if the row has not changed since it was uploaded. */
    @Query("UPDATE analyses SET needsSync = 0, remoteUpdatedAt = :updatedAt WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}

@Dao
interface DocumentPageDao {

    @Query("SELECT * FROM document_pages WHERE analysisId = :analysisId ORDER BY pageIndex")
    suspend fun pages(analysisId: String): List<DocumentPageEntity>

    @Insert
    suspend fun insert(pages: List<DocumentPageEntity>)

    @Query("DELETE FROM document_pages WHERE analysisId = :analysisId")
    suspend fun delete(analysisId: String)

    @Transaction
    suspend fun replace(analysisId: String, pages: List<DocumentPageEntity>) {
        delete(analysisId)
        insert(pages)
    }
}

@Dao
interface GeneratedContractDao {

    @Insert
    suspend fun insert(entity: GeneratedContractEntity)

    @Upsert
    suspend fun upsert(entity: GeneratedContractEntity)

    @Query("SELECT MAX(version) FROM generated_contracts WHERE contractId = :contractId")
    suspend fun latestVersion(contractId: String): Int?

    @Query(
        "SELECT * FROM generated_contracts c WHERE version = " +
            "(SELECT MAX(version) FROM generated_contracts WHERE contractId = c.contractId) ORDER BY createdAt DESC"
    )
    fun observeLatest(): Flow<List<GeneratedContractEntity>>

    @Query("SELECT * FROM generated_contracts WHERE contractId = :contractId ORDER BY version DESC")
    fun observeVersions(contractId: String): Flow<List<GeneratedContractEntity>>

    @Query("SELECT * FROM generated_contracts WHERE id = :id")
    suspend fun get(id: String): GeneratedContractEntity?

    @Query("SELECT * FROM generated_contracts WHERE needsSync = 1")
    suspend fun pendingSync(): List<GeneratedContractEntity>

    @Query("UPDATE generated_contracts SET needsSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)
}
