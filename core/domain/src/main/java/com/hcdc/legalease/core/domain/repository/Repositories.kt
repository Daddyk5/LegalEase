package com.hcdc.legalease.core.domain.repository

import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.model.Verdict
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Local-first store of analyses. The local database is the source of truth. */
interface AnalysisRepository {

    /**
     * Copies [sourceUris] into app-private storage and records a queued analysis, so it can
     * run in the background even after the picker's URI grant expires.
     */
    suspend fun create(documentName: String, kind: SourceKind, sourceUris: List<String>): AppResult<String>

    fun observe(id: String): Flow<Analysis?>

    suspend fun get(id: String): Analysis?

    /** Paths of the private copies made by [create]; empty once they have been discarded. */
    suspend fun sourcePaths(id: String): List<String>

    suspend fun markExtracting(id: String, currentPage: Int, pageCount: Int)

    /** Persists OCR text and deletes the private source copies, which are no longer needed. */
    suspend fun saveExtractedText(id: String, text: ExtractedText)

    suspend fun extractedText(id: String): ExtractedText?

    suspend fun markClassifying(id: String)

    suspend fun complete(id: String, verdict: Verdict)

    suspend fun fail(id: String, error: AppError)

    suspend fun markCancelled(id: String)

    /** Completed and failed analyses, newest first, filtered by name or classification. */
    fun observeHistory(query: String): Flow<List<Analysis>>

    suspend fun delete(id: String)
}

/** Versioned storage for generated contracts. */
interface ContractRepository {

    /** Saves a new version; pass null [contractId] to start a new contract. Returns the saved version. */
    suspend fun saveVersion(contractId: String?, type: ContractType, title: String, body: String): GeneratedContract

    /** Latest version of every contract, newest first. */
    fun observeLatest(): Flow<List<GeneratedContract>>

    /** All versions of one contract, newest first. */
    fun observeVersions(contractId: String): Flow<List<GeneratedContract>>
}

/** The signed-in Firebase user, if any. */
interface UserSession {
    val currentUserId: Flow<String?>
}

/** App settings persisted with DataStore. */
interface PreferencesRepository {
    /** Cloud sync runs only after the user explicitly opts in. Defaults to false. */
    val cloudSyncEnabled: Flow<Boolean>

    suspend fun setCloudSyncEnabled(enabled: Boolean)
}
