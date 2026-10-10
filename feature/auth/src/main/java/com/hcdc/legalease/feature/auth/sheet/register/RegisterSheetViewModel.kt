package com.hcdc.legalease.feature.auth.sheet.register

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.model.CredentialProblem
import com.hcdc.legalease.core.domain.model.CredentialValidator
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.usecase.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val email: String = "",
    val password: String = "",
    val repeatPassword: String = "",
    val emailProblem: CredentialProblem? = null,
    val passwordProblem: CredentialProblem? = null,
    val repeatProblem: CredentialProblem? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    /** Set after success: the address the verification link was sent to. */
    val verificationSentTo: String? = null,
)

@HiltViewModel
class RegisterSheetViewModel @Inject constructor(
    private val register: RegisterUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, emailProblem = null, error = null) }

    fun onPasswordChanged(value: String) =
        _uiState.update { it.copy(password = value, passwordProblem = null, repeatProblem = null, error = null) }

    fun onRepeatPasswordChanged(value: String) =
        _uiState.update { it.copy(repeatPassword = value, repeatProblem = null, error = null) }

    fun submit() {
        val state = _uiState.value
        if (state.isSubmitting) return
        val problems = Triple(
            CredentialValidator.email(state.email),
            CredentialValidator.password(state.password),
            CredentialValidator.repeatPassword(state.password, state.repeatPassword),
        )
        if (problems.toList().any { it != null }) {
            _uiState.update {
                it.copy(
                    emailProblem = problems.first,
                    passwordProblem = problems.second,
                    repeatProblem = problems.third,
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, error = null, verificationSentTo = null) }
            when (val result = register(state.email, state.password)) {
                is AppResult.Success -> _uiState.value = RegisterUiState(verificationSentTo = state.email.trim())
                is AppResult.Failure -> _uiState.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }
}
