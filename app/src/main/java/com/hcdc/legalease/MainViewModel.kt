package com.hcdc.legalease

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.usecase.AcceptPrivacyNoticeUseCase
import com.hcdc.legalease.core.domain.usecase.ObservePrivacyNoticeAcceptedUseCase
import com.hcdc.legalease.core.domain.usecase.ObservePrivacySettingsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the activity shows before any app content: nothing while loading, the notice, or the app. */
sealed interface LaunchState {
    data object Loading : LaunchState
    data object NeedsPrivacyNotice : LaunchState
    data class Ready(val appLockEnabled: Boolean) : LaunchState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    noticeAccepted: ObservePrivacyNoticeAcceptedUseCase,
    settings: ObservePrivacySettingsUseCase,
    private val acceptNotice: AcceptPrivacyNoticeUseCase,
) : ViewModel() {

    val launchState: StateFlow<LaunchState> = combine(noticeAccepted(), settings()) { accepted, s ->
        if (accepted) LaunchState.Ready(s.appLockEnabled) else LaunchState.NeedsPrivacyNotice
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LaunchState.Loading)

    fun acceptPrivacyNotice() {
        viewModelScope.launch { acceptNotice() }
    }
}
