package com.hcdc.legalease.core.domain.repository

import androidx.paging.PagingData
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.model.SourceKind
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

    /** Records a queued analysis of text LegalEase generated itself, one entry per page. */
    suspend fun createFromText(documentName: String, pages: List<String>): AppResult<String>

    fun observe(id: String): Flow<Analysis?>

    suspend fun get(id: String): Analysis?

    /** Paths of the private copies made by [create], in page order; empty once deleted. */
    suspend fun sourcePaths(id: String): List<String>

    suspend fun markExtracting(id: String, currentPage: Int, pageCount: Int)

    /** Persists OCR text. The private source copy is kept so the user can open cited pages. */
    suspend fun saveExtractedText(id: String, text: ExtractedText)

    suspend fun extractedText(id: String): ExtractedText?

    suspend fun markClassifying(id: String)

    suspend fun markAnalyzingPage(id: String, currentPage: Int, pageCount: Int)

    suspend fun markChecking(id: String)

    suspend fun complete(id: String, report: AnalysisReport)

    suspend fun fail(id: String, error: AppError)

    suspend fun markCancelled(id: String)

    /** Completed and failed analyses, newest first, filtered by name or classification, paged. */
    fun history(query: String): Flow<PagingData<Analysis>>

    suspend fun delete(id: String)
}

/** Versioned storage for generated contracts. */
interface ContractRepository {

    /** Saves a new version; pass null [contractId] to start a new contract. Returns the saved version. */
    suspend fun saveVersion(contractId: String?, draft: ContractDraft, title: String, body: String): GeneratedContract

    fun observeVersion(versionId: String): Flow<GeneratedContract?>

    /** Records which analysis checked this version. The text itself never changes. */
    suspend fun linkAnalysis(versionId: String, analysisId: String)

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

    /** How long analysis history is kept. Defaults to [RetentionPeriod.NEVER]. */
    val retention: Flow<RetentionPeriod>

    suspend fun setRetention(period: RetentionPeriod)

    /** Ask for biometrics or the device credential when the app opens. Defaults to false. */
    val appLockEnabled: Flow<Boolean>

    suspend fun setAppLockEnabled(enabled: Boolean)

    /** Version of the privacy notice the user accepted; 0 if none. */
    val acceptedPrivacyNoticeVersion: Flow<Int>

    suspend fun acceptPrivacyNotice(version: Int)

    /** Removes every setting, as part of deleting the user's data. */
    suspend fun clear()
}
