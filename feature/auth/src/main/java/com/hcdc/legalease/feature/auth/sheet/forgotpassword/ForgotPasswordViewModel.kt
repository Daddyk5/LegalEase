package com.hcdc.legalease.feature.auth.sheet.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.model.CredentialProblem
import com.hcdc.legalease.core.domain.model.CredentialValidator
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.usecase.SendPasswordResetUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ForgotPasswordUiState(
    val email: String = "",
    val emailProblem: CredentialProblem? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** Set after submit: the address a reset link was requested for (sent only if the account exists). */
    val resetRequestedFor: String? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val sendPasswordReset: SendPasswordResetUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChanged(newValue: String) =
        _uiState.update { it.copy(email = newValue, emailProblem = null, error = null, resetRequestedFor = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        CredentialValidator.email(state.email)?.let { problem ->
            _uiState.update { it.copy(emailProblem = problem) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null) }
            val result = sendPasswordReset(state.email)
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false, resetRequestedFor = state.email.trim())
                    is AppResult.Failure -> it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }
}
