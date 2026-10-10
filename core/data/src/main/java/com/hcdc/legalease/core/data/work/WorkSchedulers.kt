package com.hcdc.legalease.core.data.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hcdc.legalease.core.data.sync.SyncWorker
import com.hcdc.legalease.core.domain.engine.AnalysisScheduler
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal class WorkManagerAnalysisScheduler @Inject constructor(
    private val workManager: WorkManager,
) : AnalysisScheduler {

    override fun enqueue(analysisId: String) {
        val request = OneTimeWorkRequestBuilder<AnalysisWorker>()
            .setInputData(AnalysisWorker.inputFor(analysisId))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG_ANALYSIS)
            .build()
        workManager.enqueueUniqueWork(uniqueName(analysisId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancel(analysisId: String) {
        workManager.cancelUniqueWork(uniqueName(analysisId))
    }

    private fun uniqueName(analysisId: String) = "analysis-$analysisId"

    private companion object {
        const val TAG_ANALYSIS = "analysis"
    }
}

internal class WorkManagerSyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {

    /** Replaces any running sync: sync is idempotent, and a restart picks up the newest changes. */
    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraint)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(SYNC_NOW, ExistingWorkPolicy.REPLACE, request)
    }
}

/** Background work started once per app launch. */
@Singleton
class BackgroundWork @Inject constructor(
    private val workManager: WorkManager,
) {
    fun schedule() {
        workManager.enqueueUniqueWork(
            MODEL_DOWNLOAD,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            SYNC_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(SYNC_INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .build(),
        )
    }
}

private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

private const val MODEL_DOWNLOAD = "model-download"
private const val SYNC_NOW = "sync-now"
private const val SYNC_PERIODIC = "sync-periodic"
private const val SYNC_INTERVAL_HOURS = 12L
private const val BACKOFF_SECONDS = 30L
