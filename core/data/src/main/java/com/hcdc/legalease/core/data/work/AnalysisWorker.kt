package com.hcdc.legalease.core.data.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.hcdc.legalease.core.domain.repository.AnalysisRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.usecase.AnalyzeContractUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Runs [AnalyzeContractUseCase] as foreground work, so a long multi-page analysis keeps going
 * when the app is backgrounded. Results and failures are written to Room, which the UI observes.
 */
@HiltWorker
class AnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val analyze: AnalyzeContractUseCase,
    private val repository: AnalysisRepository,
    private val notifications: AnalysisNotifications,
) : CoroutineWorker(context, params) {

    private val analysisId: String get() = checkNotNull(inputData.getString(KEY_ANALYSIS_ID))

    override suspend fun getForegroundInfo(): ForegroundInfo = notifications.foregroundInfo(analysisId, id, null)

    override suspend fun doWork(): Result {
        val analysisId = inputData.getString(KEY_ANALYSIS_ID) ?: return Result.failure()
        promoteToForeground(analysisId)
        return try {
            analyze(analysisId) { progress -> notifications.update(analysisId, id, progress) }
            Result.success()
        } catch (e: CancellationException) {
            if (stopReason == WorkInfo.STOP_REASON_CANCELLED_BY_APP) {
                // Cancelled from the notification: record it so the UI stops waiting.
                withContext(NonCancellable) { repository.markCancelled(analysisId) }
            }
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // Last-resort boundary: record the failure so the UI never waits on a dead analysis.
            Log.e(TAG, "Analysis failed unexpectedly", e)
            repository.fail(analysisId, AppError.Unexpected(e.javaClass.simpleName))
            Result.failure()
        }
    }

    private suspend fun promoteToForeground(analysisId: String) {
        try {
            setForeground(notifications.foregroundInfo(analysisId, id, null))
        } catch (e: IllegalStateException) {
            // Android may refuse a foreground start (e.g. app restrictions); the work still runs.
            Log.w(TAG, "Could not start foreground service", e)
        }
    }

    companion object {
        private const val TAG = "AnalysisWorker"
        const val KEY_ANALYSIS_ID = "analysis_id"

        fun inputFor(analysisId: String) = workDataOf(KEY_ANALYSIS_ID to analysisId)
    }
}
