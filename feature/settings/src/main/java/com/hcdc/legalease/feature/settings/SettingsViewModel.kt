package com.hcdc.legalease.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.engine.IntegrityChecker
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.model.RetentionPeriod
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.usecase.DeleteAccountUseCase
import com.hcdc.legalease.core.domain.usecase.ExportMyDataUseCase
import com.hcdc.legalease.core.domain.usecase.ObservePrivacySettingsUseCase
import com.hcdc.legalease.core.domain.usecase.PrivacySettings
import com.hcdc.legalease.core.domain.usecase.SetAppLockUseCase
import com.hcdc.legalease.core.domain.usecase.SetCloudSyncUseCase
import com.hcdc.legalease.core.domain.usecase.SetRetentionUseCase
import com.hcdc.legalease.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SettingsTask { EXPORTING, DELETING }

sealed interface SettingsMessage {
    data class RetentionApplied(val deleted: Int) : SettingsMessage
    data object RecentLoginRequired : SettingsMessage
    data class Failed(val error: AppError) : SettingsMessage
}

data class SettingsUiState(
    val settings: PrivacySettings? = null,
    val integrity: DeviceIntegrity = DeviceIntegrity.UNKNOWN,
    /** False when the device has no biometrics or screen lock set up. */
    val lockAvailable: Boolean = false,
    val task: SettingsTask? = null,
    /** The cloud backup consent dialog is open. Backup is only turned on from it. */
    val confirmingBackup: Boolean = false,
    val confirmingDelete: Boolean = false,
    val message: SettingsMessage? = null,
)

sealed interface SettingsEvent {
    data class ShareExport(val file: File) : SettingsEvent
    data object AccountDeleted : SettingsEvent
    data object SignedOut : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObservePrivacySettingsUseCase,
    integrity: IntegrityChecker,
    deviceLock: DeviceLock,
    private val setCloudSync: SetCloudSyncUseCase,
    private val setRetention: SetRetentionUseCase,
    private val setAppLock: SetAppLockUseCase,
    private val exportMyData: ExportMyDataUseCase,
    private val deleteAccount: DeleteAccountUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(lockAvailable = deviceLock.isAvailable()))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _events = Channel<SettingsEvent>(Channel.BUFFERED)
    val events: Flow<SettingsEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch { observeSettings().collect { s -> _uiState.update { it.copy(settings = s) } } }
        viewModelScope.launch { integrity.verdict.collect { v -> _uiState.update { it.copy(integrity = v) } } }
    }

    /** Turning backup on asks for consent first; turning it off takes effect at once. */
    fun onBackupToggled(enabled: Boolean) {
        if (enabled) {
            _uiState.update { it.copy(confirmingBackup = true) }
        } else {
            viewModelScope.launch { setCloudSync(false) }
        }
    }

    fun onBackupConsent(agreed: Boolean) {
        _uiState.update { it.copy(confirmingBackup = false) }
        if (agreed) viewModelScope.launch { setCloudSync(true) }
    }

    fun onRetention(period: RetentionPeriod) {
        viewModelScope.launch {
            val deleted = setRetention(period)
            if (deleted > 0) _uiState.update { it.copy(message = SettingsMessage.RetentionApplied(deleted)) }
        }
    }

    /** Called after the user has authenticated once, so they cannot lock themselves out by mistake. */
    fun onAppLock(enabled: Boolean) {
        viewModelScope.launch { setAppLock(enabled) }
    }

    fun export() = runTask(SettingsTask.EXPORTING) {
        when (val result = exportMyData()) {
            is AppResult.Success -> _events.send(SettingsEvent.ShareExport(result.value))
            is AppResult.Failure -> _uiState.update { it.copy(message = SettingsMessage.Failed(result.error)) }
        }
    }

    fun requestDelete() = _uiState.update { it.copy(confirmingDelete = true) }

    fun onDeleteConfirmed(confirmed: Boolean) {
        _uiState.update { it.copy(confirmingDelete = false) }
        if (!confirmed) return
        runTask(SettingsTask.DELETING) {
            when (val result = deleteAccount()) {
                is AppResult.Success -> _events.send(SettingsEvent.AccountDeleted)
                is AppResult.Failure -> _uiState.update {
                    val message = if (result.error == AppError.RecentLoginRequired) {
                        SettingsMessage.RecentLoginRequired
                    } else {
                        SettingsMessage.Failed(result.error)
                    }
                    it.copy(message = message)
                }
            }
        }
    }

    fun signOutNow() {
        viewModelScope.launch {
            signOut()
            _events.send(SettingsEvent.SignedOut)
        }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun runTask(task: SettingsTask, block: suspend () -> Unit) {
        if (_uiState.value.task != null) return
        viewModelScope.launch {
            _uiState.update { it.copy(task = task, message = null) }
            try {
                block()
            } finally {
                _uiState.update { it.copy(task = null) }
            }
        }
    }
}
