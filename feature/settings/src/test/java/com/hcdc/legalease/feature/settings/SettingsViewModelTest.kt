package com.hcdc.legalease.feature.settings

import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.AuditEvent
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAccountData
import com.hcdc.legalease.core.domain.testing.FakeAuditLog
import com.hcdc.legalease.core.domain.testing.FakeAuthRepository
import com.hcdc.legalease.core.domain.testing.FakeIntegrityChecker
import com.hcdc.legalease.core.domain.testing.FakePreferences
import com.hcdc.legalease.core.domain.testing.FakeSyncScheduler
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.usecase.ApplyRetentionUseCase
import com.hcdc.legalease.core.domain.usecase.DeleteAccountUseCase
import com.hcdc.legalease.core.domain.usecase.ExportMyDataUseCase
import com.hcdc.legalease.core.domain.usecase.ObservePrivacySettingsUseCase
import com.hcdc.legalease.core.domain.usecase.SetAppLockUseCase
import com.hcdc.legalease.core.domain.usecase.SetCloudSyncUseCase
import com.hcdc.legalease.core.domain.usecase.SetRetentionUseCase
import com.hcdc.legalease.core.domain.usecase.SignOutUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val preferences = FakePreferences()
    private val accountData = FakeAccountData()
    private val auditLog = FakeAuditLog()
    private val integrity = FakeIntegrityChecker()
    private val auth = FakeAuthRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(lockAvailable: Boolean = true) = SettingsViewModel(
        observeSettings = ObservePrivacySettingsUseCase(preferences),
        integrity = integrity,
        deviceLock = object : DeviceLock {
            override fun isAvailable() = lockAvailable
        },
        setCloudSync = SetCloudSyncUseCase(preferences, FakeSyncScheduler(), auditLog, clock),
        setRetention = SetRetentionUseCase(preferences, ApplyRetentionUseCase(preferences, accountData, clock)),
        setAppLock = SetAppLockUseCase(preferences),
        exportMyData = ExportMyDataUseCase(accountData, auditLog, clock),
        deleteAccount = DeleteAccountUseCase(accountData),
        signOut = SignOutUseCase(auth),
    )

    @Test
    fun `cloud backup turns on only after consent`() = runTest {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBackupToggled(true)
        assertTrue(vm.uiState.value.confirmingBackup)
        assertFalse("not before consent", preferences.sync.value)

        vm.onBackupConsent(agreed = false)
        advanceUntilIdle()
        assertFalse(preferences.sync.value)

        vm.onBackupToggled(true)
        vm.onBackupConsent(agreed = true)
        advanceUntilIdle()
        assertTrue(preferences.sync.value)
        assertEquals(AuditEvent.CLOUD_SYNC_ENABLED, auditLog.entries.value.single().event)
        assertTrue(vm.uiState.value.settings!!.cloudSyncEnabled)
    }

    @Test
    fun `turning backup off needs no confirmation`() = runTest {
        preferences.sync.value = true
        val vm = viewModel()
        vm.onBackupToggled(false)
        advanceUntilIdle()
        assertFalse(preferences.sync.value)
        assertFalse(vm.uiState.value.confirmingBackup)
    }

    @Test
    fun `choosing a retention period saves it and applies it`() = runTest {
        val vm = viewModel()
        vm.onRetention(RetentionPeriod.DAYS_90)
        advanceUntilIdle()
        assertEquals(RetentionPeriod.DAYS_90, preferences.retentionState.value)
        assertEquals(1, accountData.cutoffs.size)
    }

    @Test
    fun `export shares the archive`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.export()
            assertEquals(SettingsEvent.ShareExport(File("export.zip")), awaitItem())
        }
        assertNull(vm.uiState.value.task)
    }

    @Test
    fun `account deletion needs confirmation and reports a required sign-in`() = runTest {
        accountData.deleteResult = AppResult.Failure(AppError.RecentLoginRequired)
        val vm = viewModel()
        vm.requestDelete()
        assertTrue(vm.uiState.value.confirmingDelete)
        vm.onDeleteConfirmed(false)
        advanceUntilIdle()
        assertEquals(0, accountData.deleteCalls)

        vm.requestDelete()
        vm.onDeleteConfirmed(true)
        advanceUntilIdle()
        assertEquals(1, accountData.deleteCalls)
        assertEquals(SettingsMessage.RecentLoginRequired, vm.uiState.value.message)
    }

    @Test
    fun `a successful deletion leaves the app`() = runTest {
        val vm = viewModel()
        vm.events.test {
            vm.requestDelete()
            vm.onDeleteConfirmed(true)
            assertEquals(SettingsEvent.AccountDeleted, awaitItem())
        }
    }

    @Test
    fun `shows the integrity warning state and lock availability`() = runTest {
        integrity.state.value = DeviceIntegrity.COMPROMISED
        val vm = viewModel(lockAvailable = false)
        advanceUntilIdle()
        assertEquals(DeviceIntegrity.COMPROMISED, vm.uiState.value.integrity)
        assertFalse(vm.uiState.value.lockAvailable)
    }
}
