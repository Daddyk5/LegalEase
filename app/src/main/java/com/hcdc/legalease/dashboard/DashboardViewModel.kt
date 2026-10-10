package com.hcdc.legalease.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.engine.IntegrityChecker
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import com.hcdc.legalease.core.domain.usecase.ObserveUserUseCase
import com.hcdc.legalease.core.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeUser: ObserveUserUseCase,
    private val signOut: SignOutUseCase,
    featureFlags: FeatureFlagsRepository,
    integrity: IntegrityChecker,
) : ViewModel() {

    /** True when Play Integrity found the device rooted or the app modified. */
    val deviceCompromised: StateFlow<Boolean> = integrity.verdict
        .map { it == DeviceIntegrity.COMPROMISED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), false)

    /** Remote kill switch for the contract generator. */
    val generatorEnabled: StateFlow<Boolean> = featureFlags.flags
        .map { it.generatorEnabled }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            featureFlags.flags.value.generatorEnabled,
        )

    /** The signed-in user's name, or null while unknown. */
    val greetingName: StateFlow<String?> = observeUser()
        .map { it?.greetingName }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private val _signedOut = Channel<Unit>(Channel.BUFFERED)
    val signedOut: Flow<Unit> = _signedOut.receiveAsFlow()

    fun logout() {
        viewModelScope.launch {
            signOut()
            _signedOut.send(Unit)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
