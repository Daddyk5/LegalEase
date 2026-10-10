package com.hcdc.legalease.feature.auth.sheet.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.model.CredentialProblem
import com.hcdc.legalease.core.domain.model.CredentialValidator
import com.hcdc.legalease.core.domain.model.GoogleSignInConfig
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.usecase.ResendVerificationUseCase
import com.hcdc.legalease.core.domain.usecase.SignInUseCase
import com.hcdc.legalease.core.domain.usecase.SignInWithGoogleUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailProblem: CredentialProblem? = null,
    val passwordProblem: CredentialProblem? = null,
    val isSubmitting: Boolean = false,
    val error: AppError? = null,
    val verificationResent: Boolean = false,
    val googleUnavailable: Boolean = false,
) {
    /** Offer to resend the link only when that is the problem. */
    val canResendVerification: Boolean get() = error == AppError.EmailNotVerified
}

@HiltViewModel
class LoginSheetViewModel @Inject constructor(
    private val signIn: SignInUseCase,
    private val signInWithGoogle: SignInWithGoogleUseCase,
    private val resendVerification: ResendVerificationUseCase,
    googleConfig: GoogleSignInConfig,
) : ViewModel() {

    val googleWebClientId: String = googleConfig.webClientId

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _loggedIn = Channel<Unit>(Channel.BUFFERED)
    val loggedIn: Flow<Unit> = _loggedIn.receiveAsFlow()

    fun onEmailChanged(value: String) = _uiState.update { it.copy(email = value, emailProblem = null, error = null) }

    fun onPasswordChanged(value: String) =
        _uiState.update { it.copy(password = value, passwordProblem = null, error = null) }

    fun submit() {
        val state = _uiState.value
        val emailProblem = CredentialValidator.email(state.email)
        // Only presence is checked here; length rules belong to registration, not to existing passwords.
        val passwordProblem = if (state.password.isEmpty()) CredentialProblem.PASSWORD_TOO_SHORT else null
        if (emailProblem != null || passwordProblem != null) {
            _uiState.update { it.copy(emailProblem = emailProblem, passwordProblem = passwordProblem) }
            return
        }
        authenticate { signIn(state.email, state.password) }
    }

    fun onGoogleIdToken(idToken: String) = authenticate { signInWithGoogle(idToken) }

    fun onGoogleUnavailable() = _uiState.update { it.copy(error = null, googleUnavailable = true) }

    fun resendVerificationEmail() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true) }
            val result = resendVerification()
            _uiState.update {
                when (result) {
                    is AppResult.Success -> it.copy(isSubmitting = false, error = null, verificationResent = true)
                    is AppResult.Failure -> it.copy(isSubmitting = false, error = result.error)
                }
            }
        }
    }

    private fun authenticate(call: suspend () -> AppResult<*>) {
        if (_uiState.value.isSubmitting) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(isSubmitting = true, error = null, verificationResent = false, googleUnavailable = false)
            }
            when (val result = call()) {
                is AppResult.Success -> {
                    _uiState.update { LoginUiState() }
                    _loggedIn.send(Unit)
                }
                is AppResult.Failure -> _uiState.update { it.copy(isSubmitting = false, error = result.error) }
            }
        }
    }
}
