package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.engine.AccountDataRepository
import com.hcdc.legalease.core.domain.engine.AuditLog
import com.hcdc.legalease.core.domain.engine.SyncScheduler
import com.hcdc.legalease.core.domain.model.AuditEntry
import com.hcdc.legalease.core.domain.model.AuditEvent
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.repository.PreferencesRepository
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

/** The privacy notice shown on first launch. Bump [CURRENT_VERSION] when its content changes materially. */
object PrivacyNotice {
    const val CURRENT_VERSION = 1
}

data class PrivacySettings(
    val cloudSyncEnabled: Boolean,
    val retention: RetentionPeriod,
    val appLockEnabled: Boolean,
)

class ObservePrivacySettingsUseCase @Inject constructor(private val preferences: PreferencesRepository) {
    operator fun invoke(): Flow<PrivacySettings> = combine(
        preferences.cloudSyncEnabled,
        preferences.retention,
        preferences.appLockEnabled,
    ) { sync, retention, lock -> PrivacySettings(sync, retention, lock) }
}

/** True once the user has accepted the current privacy notice. */
class ObservePrivacyNoticeAcceptedUseCase @Inject constructor(private val preferences: PreferencesRepository) {
    operator fun invoke(): Flow<Boolean> =
        preferences.acceptedPrivacyNoticeVersion.map { it >= PrivacyNotice.CURRENT_VERSION }
}

class AcceptPrivacyNoticeUseCase @Inject constructor(private val preferences: PreferencesRepository) {
    suspend operator fun invoke() = preferences.acceptPrivacyNotice(PrivacyNotice.CURRENT_VERSION)
}

/**
 * Turns cloud sync on or off. Callers must have shown the consent text before enabling; the change
 * is recorded in the audit log so the consent can be demonstrated.
 */
class SetCloudSyncUseCase @Inject constructor(
    private val preferences: PreferencesRepository,
    private val syncScheduler: SyncScheduler,
    private val auditLog: AuditLog,
    private val clock: Clock,
) {
    suspend operator fun invoke(enabled: Boolean) {
        preferences.setCloudSyncEnabled(enabled)
        val event = if (enabled) AuditEvent.CLOUD_SYNC_ENABLED else AuditEvent.CLOUD_SYNC_DISABLED
        auditLog.record(AuditEntry(clock.millis(), event))
        if (enabled) syncScheduler.requestSync()
    }
}

class SetAppLockUseCase @Inject constructor(private val preferences: PreferencesRepository) {
    suspend operator fun invoke(enabled: Boolean) = preferences.setAppLockEnabled(enabled)
}

/** Deletes analyses older than the chosen retention period. Returns how many were deleted. */
class ApplyRetentionUseCase @Inject constructor(
    private val preferences: PreferencesRepository,
    private val accountData: AccountDataRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(): Int {
        val days = preferences.retention.first().days ?: return 0
        return accountData.deleteAnalysesBefore(clock.millis() - Duration.ofDays(days.toLong()).toMillis())
    }
}

/** Saves the new period and applies it at once, so the user sees the effect immediately. */
class SetRetentionUseCase @Inject constructor(
    private val preferences: PreferencesRepository,
    private val applyRetention: ApplyRetentionUseCase,
) {
    suspend operator fun invoke(period: RetentionPeriod): Int {
        preferences.setRetention(period)
        return applyRetention()
    }
}

class ExportMyDataUseCase @Inject constructor(
    private val accountData: AccountDataRepository,
    private val auditLog: AuditLog,
    private val clock: Clock,
) {
    suspend operator fun invoke(): AppResult<File> {
        val result = accountData.exportAll()
        if (result is AppResult.Success) auditLog.record(AuditEntry(clock.millis(), AuditEvent.DATA_EXPORTED))
        return result
    }
}

class DeleteAccountUseCase @Inject constructor(private val accountData: AccountDataRepository) {
    suspend operator fun invoke(): AppResult<Unit> = accountData.deleteAccountAndData()
}
