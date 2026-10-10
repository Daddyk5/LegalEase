package com.hcdc.legalease.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.usecase.ObserveUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class IntroViewModel @Inject constructor(
    observeUser: ObserveUserUseCase,
) : ViewModel() {

    /** True only for a signed-in user with a verified email; unverified accounts must log in again. */
    val isSignedIn: StateFlow<Boolean> = observeUser()
        .map { it?.isEmailVerified == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
}
