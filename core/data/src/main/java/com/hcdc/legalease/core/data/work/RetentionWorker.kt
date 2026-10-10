package com.hcdc.legalease.core.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hcdc.legalease.core.domain.usecase.ApplyRetentionUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Deletes history older than the user's retention setting. Runs daily; a no-op when set to "never". */
@HiltWorker
class RetentionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val applyRetention: ApplyRetentionUseCase,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        applyRetention()
        return Result.success()
    }
}
