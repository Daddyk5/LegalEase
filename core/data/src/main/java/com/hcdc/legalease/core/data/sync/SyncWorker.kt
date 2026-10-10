package com.hcdc.legalease.core.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestoreException
import com.hcdc.legalease.core.data.local.AnalysisDao
import com.hcdc.legalease.core.data.local.GeneratedContractDao
import com.hcdc.legalease.core.data.remote.FirestoreSyncDataSource
import com.hcdc.legalease.core.data.repository.DataStorePreferencesRepository
import com.hcdc.legalease.core.domain.repository.UserSession
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Two-way sync between Room and `users/{uid}/…` in Firestore.
 *
 * Runs only when a user is signed in and has opted in to cloud sync. Push first (local edits win
 * over older remote copies), then pull anything newer than the last pull. Conflicts resolve by
 * `updatedAt`: the newer version wins.
 */
@HiltWorker
internal class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val session: UserSession,
    private val preferences: DataStorePreferencesRepository,
    private val remote: FirestoreSyncDataSource,
    private val analysisDao: AnalysisDao,
    private val contractDao: GeneratedContractDao,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!preferences.cloudSyncEnabled.first()) return Result.success()
        val uid = session.currentUserId.first() ?: return Result.success()
        return try {
            push(uid)
            pull(uid)
            Result.success()
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Sync failed: ${e.code}")
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) Result.failure() else Result.retry()
        }
    }

    private suspend fun push(uid: String) {
        for (entity in analysisDao.pendingSync()) {
            remote.putAnalysis(uid, entity)
            if (entity.deleted) {
                analysisDao.hardDelete(entity.id)
            } else {
                analysisDao.markSynced(entity.id, entity.updatedAt)
            }
        }
        for (entity in contractDao.pendingSync()) {
            remote.putContract(uid, entity)
            contractDao.markSynced(entity.id)
        }
    }

    private suspend fun pull(uid: String) {
        val since = preferences.lastPulledAt(uid)
        var newest = since

        for (incoming in remote.analysesUpdatedSince(uid, since)) {
            newest = maxOf(newest, incoming.updatedAt)
            val local = analysisDao.get(incoming.id)
            when {
                incoming.deleted -> if (local != null && !local.needsSync) analysisDao.hardDelete(incoming.id)
                local == null || incoming.updatedAt > local.updatedAt -> analysisDao.upsert(incoming)
            }
        }
        for (incoming in remote.contractsUpdatedSince(uid, since)) {
            newest = maxOf(newest, incoming.createdAt)
            if (contractDao.get(incoming.id) == null) contractDao.upsert(incoming)
        }
        if (newest > since) preferences.setLastPulledAt(uid, newest)
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
