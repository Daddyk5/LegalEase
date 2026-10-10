package com.hcdc.legalease.core.data.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.hcdc.legalease.core.data.R
import com.hcdc.legalease.core.domain.usecase.AnalysisProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

/**
 * The ongoing notification shown while an analysis runs in the background. It never shows the
 * document name or content, since it can appear on the lock screen.
 */
class AnalysisNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun foregroundInfo(analysisId: String, workId: UUID, progress: AnalysisProgress?): ForegroundInfo =
        ForegroundInfo(
            notificationId(analysisId),
            build(workId, progress),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

    /** Updates the existing notification; does nothing if notifications are not permitted. */
    fun update(analysisId: String, workId: UUID, progress: AnalysisProgress) {
        if (manager.areNotificationsEnabled()) manager.notify(notificationId(analysisId), build(workId, progress))
    }

    private fun build(workId: UUID, progress: AnalysisProgress?): Notification {
        ensureChannel()
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_analysis)
            .setContentTitle(context.getString(R.string.analysis_notification_title))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(openAppIntent())
            .addAction(
                0,
                context.getString(R.string.analysis_notification_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(workId),
            )
        when (progress) {
            is AnalysisProgress.ReadingPage -> builder
                .setContentText(
                    context.getString(R.string.analysis_notification_reading, progress.page, progress.pageCount)
                )
                .setProgress(progress.pageCount, progress.page, false)
            AnalysisProgress.Classifying -> builder
                .setContentText(context.getString(R.string.analysis_notification_classifying))
                .setProgress(0, 0, true)
            is AnalysisProgress.AnalyzingPage -> builder
                .setContentText(
                    context.getString(R.string.analysis_notification_analyzing, progress.page, progress.pageCount)
                )
                .setProgress(progress.pageCount, progress.page, false)
            is AnalysisProgress.Reasoning -> builder
                .setContentText(context.getString(R.string.analysis_notification_reasoning))
                .setProgress(progress.passes, progress.pass, false)
            null -> builder
                .setContentText(context.getString(R.string.analysis_notification_preparing))
                .setProgress(0, 0, true)
        }
        return builder.build()
    }

    private fun openAppIntent(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.analysis_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun notificationId(analysisId: String) = NOTIFICATION_ID_BASE + (analysisId.hashCode() and ID_MASK)

    private companion object {
        const val CHANNEL_ID = "analysis_progress"
        const val NOTIFICATION_ID_BASE = 1_000
        const val ID_MASK = 0xFFFF
    }
}
