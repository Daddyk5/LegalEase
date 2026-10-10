package com.hcdc.legalease.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hcdc.legalease.core.domain.engine.LlmAnalyzer
import com.hcdc.legalease.core.domain.engine.OfflineModelProvisioner
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Gets the on-device language model in place (asset pack or one-time Wi-Fi download), verified
 * against its pinned hash. Silent: users only ever see "Preparing offline analysis…" in results.
 */
@HiltWorker
class OfflineModelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val provisioner: OfflineModelProvisioner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (provisioner.isProvisioned()) return Result.success()
        return when (val result = provisioner.provision { setProgressAsync(workDataOf(KEY_PROGRESS to it)) }) {
            is AppResult.Success -> Result.success()
            is AppResult.Failure -> when (result.error) {
                // Transient: offline, pack still downloading, or the download failed its hash check.
                AppError.Network, AppError.StorageFull -> Result.retry()
                is AppError.Unexpected -> if ((result.error as AppError.Unexpected).detail == HASH_MISMATCH) {
                    Result.retry()
                } else {
                    // Unsupported device or no pinned model: nothing to retry; the classifier fallback is used.
                    Result.success()
                }
                else -> Result.retry()
            }
        }
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        private const val HASH_MISMATCH = "hash_mismatch"
    }
}

/** Loads the model once while the device is charging and idle, as a self-test and warm-up. */
@HiltWorker
class LlmWarmUpWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val llm: LlmAnalyzer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (llm.warmUp()) {
        is AppResult.Success -> Result.success()
        // A model that cannot run here is not retried; analyses fall back to the classifier.
        is AppResult.Failure -> Result.failure()
    }
}
