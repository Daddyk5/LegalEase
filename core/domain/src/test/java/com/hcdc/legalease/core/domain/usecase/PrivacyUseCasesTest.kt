package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.model.AuditEvent
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAccountData
import com.hcdc.legalease.core.domain.testing.FakeAuditLog
import com.hcdc.legalease.core.domain.testing.FakePreferences
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class PrivacyUseCasesTest {

    private val now = Instant.parse("2026-10-15T00:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val preferences = FakePreferences()
    private val accountData = FakeAccountData()
    private val auditLog = FakeAuditLog()

    @Test
    fun `cloud sync is off until the user opts in, and each choice is audited`() = runTest {
        val sync = FakeSyncScheduler()
        val setSync = SetCloudSyncUseCase(preferences, sync, auditLog, clock)
        assertFalse(ObservePrivacySettingsUseCase(preferences)().first().cloudSyncEnabled)

        setSync(true)
        assertTrue(preferences.sync.value)
        assertEquals(1, sync.requests)
        setSync(false)
        assertEquals(
            listOf(AuditEvent.CLOUD_SYNC_ENABLED, AuditEvent.CLOUD_SYNC_DISABLED),
            auditLog.entries.value.map { it.event },
        )
    }

    @Test
    fun `retention deletes analyses older than the chosen period, and never means never`() = runTest {
        val apply = ApplyRetentionUseCase(preferences, accountData, clock)
        assertEquals(0, apply())
        assertEquals(emptyList<Long>(), accountData.cutoffs)

        SetRetentionUseCase(preferences, apply)(RetentionPeriod.DAYS_30)
        assertEquals(listOf(now.minus(Duration.ofDays(30)).toEpochMilli()), accountData.cutoffs)
    }

    @Test
    fun `the privacy notice must be accepted once per version`() = runTest {
        val accepted = ObservePrivacyNoticeAcceptedUseCase(preferences)
        assertFalse(accepted().first())
        AcceptPrivacyNoticeUseCase(preferences)()
        assertTrue(accepted().first())
        assertEquals(PrivacyNotice.CURRENT_VERSION, preferences.notice.value)
    }

    @Test
    fun `a data export is audited only when it succeeds`() = runTest {
        val export = ExportMyDataUseCase(accountData, auditLog, clock)
        accountData.exportResult = AppResult.Failure(AppError.StorageFull)
        export()
        assertTrue(auditLog.entries.value.isEmpty())
        accountData.exportResult = AppResult.Success(File("x.zip"))
        export()
        assertEquals(AuditEvent.DATA_EXPORTED, auditLog.entries.value.single().event)
    }

    @Test
    fun `account deletion reports when a fresh sign-in is required`() = runTest {
        accountData.deleteResult = AppResult.Failure(AppError.RecentLoginRequired)
        assertEquals(AppResult.Failure(AppError.RecentLoginRequired), DeleteAccountUseCase(accountData)())
        assertEquals(1, accountData.deleteCalls)
    }
}
