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

    override suspend fun doWork(): Result = when (val prepared = classifier.prepare()) {
        is AppResult.Success -> Result.success()
        is AppResult.Failure -> if (prepared.error == AppError.ModelUnavailable) Result.retry() else Result.failure()
    }
}
