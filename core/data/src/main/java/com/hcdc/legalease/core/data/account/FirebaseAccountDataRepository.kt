package com.hcdc.legalease.core.data.account

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.storage.FirebaseStorage
import com.hcdc.legalease.core.data.local.AnalysisDao
import com.hcdc.legalease.core.data.local.LegalEaseDatabase
import com.hcdc.legalease.core.data.remote.FirestoreSyncDataSource
import com.hcdc.legalease.core.data.storage.ScanSourceStore
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.engine.AccountDataRepository
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.repository.PreferencesRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Export, retention, and account deletion over everything LegalEase stores. */
@Singleton
internal class FirebaseAccountDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: LegalEaseDatabase,
    private val analysisDao: AnalysisDao,
    private val analyses: AnalysisRepository,
    private val sources: ScanSourceStore,
    private val preferences: PreferencesRepository,
    private val exporter: DataExporter,
    private val remote: FirestoreSyncDataSource,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val workManager: WorkManager,
    private val syncScheduler: SyncScheduler,
    private val clock: Clock,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AccountDataRepository {

    override suspend fun exportAll(): AppResult<File> = withContext(ioDispatcher) {
        try {
            AppResult.Success(exporter.writeZip(File(context.cacheDir, EXPORT_DIR)))
        } catch (e: IOException) {
            Log.w(TAG, "Export failed", e)
            AppResult.Failure(AppError.StorageFull)
        }
    }

    /** Deleted like a user deletion: synced results become tombstones so other devices remove them too. */
    override suspend fun deleteAnalysesBefore(cutoffMillis: Long): Int {
        val ids = analysisDao.idsCreatedBefore(cutoffMillis)
        ids.forEach { analyses.delete(it) }
        if (ids.isNotEmpty()) syncScheduler.requestSync()
        return ids.size
    }

    override suspend fun deleteAccountAndData(): AppResult<Unit> {
        val user = auth.currentUser ?: return AppResult.Failure(AppError.NotSignedIn)
        // Firebase refuses to delete an account after an old sign-in. Checking first avoids deleting the
        // cloud data and then failing on the account itself.
        val signedInAt = user.metadata?.lastSignInTimestamp ?: 0L
        if (clock.millis() - signedInAt > RECENT_LOGIN.toMillis()) {
            return AppResult.Failure(AppError.RecentLoginRequired)
        }
        // Stop sync first, so nothing is uploaded again while the cloud copy is being deleted.
        preferences.setCloudSyncEnabled(false)
        workManager.cancelAllWork()
        return try {
            remote.deleteAll(user.uid)
            deleteBackups(user.uid)
            user.delete().await()
            wipeLocal()
            auth.signOut()
            AppResult.Success(Unit)
        } catch (_: FirebaseAuthRecentLoginRequiredException) {
            AppResult.Failure(AppError.RecentLoginRequired)
        } catch (_: FirebaseNetworkException) {
            AppResult.Failure(AppError.Network)
        } catch (e: FirebaseException) {
            Log.w(TAG, "Account deletion failed", e)
            AppResult.Failure(AppError.Unexpected("account_delete_${e.javaClass.simpleName}"))
        }
    }

    private suspend fun deleteBackups(uid: String) {
        val folder = storage.reference.child("users/$uid/backups")
        folder.listAll().await().items.forEach { it.delete().await() }
    }

    /** Everything on this device except the offline models, which hold no personal data. */
    private suspend fun wipeLocal() = withContext(ioDispatcher) {
        database.clearAllTables()
        sources.deleteAll()
        File(context.cacheDir, EXPORT_DIR).deleteRecursively()
        File(context.cacheDir, PAGE_VIEW_DIR).deleteRecursively()
        preferences.clear()
    }

    private companion object {
        const val TAG = "AccountData"
        const val EXPORT_DIR = "exports"
        const val PAGE_VIEW_DIR = "page_view"

        /** Firebase's window for sensitive operations is about five minutes after sign-in. */
        val RECENT_LOGIN: Duration = Duration.ofMinutes(5)
    }
}
