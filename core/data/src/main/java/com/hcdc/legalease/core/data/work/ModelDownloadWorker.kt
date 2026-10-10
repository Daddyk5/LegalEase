package com.hcdc.legalease.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Downloads and loads the offline classifier ahead of the first scan. Retries while offline. */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val classifier: ContractClassifier,
) : CoroutineWorker(context, params) {

    private companion object {
        const val MAX_ATTEMPTS = 5
    }

    override suspend fun doWork(): Result = when (val prepared = classifier.prepare()) {
        is AppResult.Success -> Result.success()
        // Retry while offline. Any other failure still succeeds so the chained language-model
        // download runs: the classifier is optional evidence, not a prerequisite.
        is AppResult.Failure -> if (prepared.error == AppError.ModelUnavailable && runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
