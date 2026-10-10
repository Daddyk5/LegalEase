package com.hcdc.legalease.feature.auth

import app.cash.turbine.test
import com.hcdc.legalease.core.domain.model.AuthUser
import com.hcdc.legalease.core.domain.model.CredentialProblem
import com.hcdc.legalease.core.domain.model.GoogleSignInConfig
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeAuthRepository
import com.hcdc.legalease.core.domain.testing.MainDispatcherRule
import com.hcdc.legalease.core.domain.usecase.RegisterUseCase
import com.hcdc.legalease.core.domain.usecase.ResendVerificationUseCase
import com.hcdc.legalease.core.domain.usecase.SendPasswordResetUseCase
import com.hcdc.legalease.core.domain.usecase.SignInUseCase
import com.hcdc.legalease.core.domain.usecase.SignInWithGoogleUseCase
import com.hcdc.legalease.feature.auth.sheet.forgotpassword.ForgotPasswordViewModel
import com.hcdc.legalease.feature.auth.sheet.login.LoginSheetViewModel
import com.hcdc.legalease.feature.auth.sheet.register.RegisterSheetViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthViewModelsTest {

    @get:Rule val mainDispatcher = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val analytics = FakeAnalytics()

    private fun login() = LoginSheetViewModel(
        SignInUseCase(auth, analytics),
        SignInWithGoogleUseCase(auth, analytics),
        ResendVerificationUseCase(auth),
        GoogleSignInConfig("web-client-id"),
    )

    @Test
    fun `login validates before calling Firebase`() {
        val vm = login()
        vm.onEmailChanged("not-an-email")
        vm.submit()

        assertEquals(CredentialProblem.EMAIL_INVALID, vm.uiState.value.emailProblem)
        assertEquals(CredentialProblem.PASSWORD_TOO_SHORT, vm.uiState.value.passwordProblem)
        assertTrue(auth.calls.isEmpty())
    }

    @Test
    fun `successful login emits once and clears the form`() = runTest {
        auth.signInResult = AppResult.Success(AuthUser("u", "a@b.ph", null, isEmailVerified = true))
        val vm = login()
        vm.onEmailChanged("a@b.ph")
        vm.onPasswordChanged("password1")

        vm.loggedIn.test {
            vm.submit()
            awaitItem()
        }
        assertEquals("", vm.uiState.value.password)
    }

    @Test
    fun `unverified email offers to resend the link`() {
        auth.signInResult = AppResult.Failure(AppError.EmailNotVerified)
        val vm = login()
        vm.onEmailChanged("a@b.ph")
        vm.onPasswordChanged("password1")
        vm.submit()
        assertTrue(vm.uiState.value.canResendVerification)

        vm.resendVerificationEmail()

        assertTrue(vm.uiState.value.verificationResent)
        assertNull(vm.uiState.value.error)
        assertEquals("resend", auth.calls.last())
    }

    @Test
    fun `wrong password shows a generic credentials error`() {
        val vm = login()
        vm.onEmailChanged("a@b.ph")
        vm.onPasswordChanged("password1")
        vm.submit()

        assertEquals(AppError.InvalidCredentials, vm.uiState.value.error)
        assertFalse(vm.uiState.value.canResendVerification)
        assertFalse(vm.uiState.value.isSubmitting)
    }

    @Test
    fun `register requires matching passwords and reports where the link was sent`() {
        val vm = RegisterSheetViewModel(RegisterUseCase(auth, analytics))
        vm.onEmailChanged(" a@b.ph ")
        vm.onPasswordChanged("password1")
        vm.onRepeatPasswordChanged("password2")
        vm.submit()
        assertEquals(CredentialProblem.PASSWORDS_DIFFER, vm.uiState.value.repeatProblem)
        assertTrue(auth.calls.isEmpty())

        vm.onRepeatPasswordChanged("password1")
        vm.submit()

        assertEquals("a@b.ph", vm.uiState.value.verificationSentTo)
        assertEquals("", vm.uiState.value.password)
    }

    @Test
    fun `register surfaces an existing account`() {
        auth.registerResult = AppResult.Failure(AppError.EmailInUse)
        val vm = RegisterSheetViewModel(RegisterUseCase(auth, analytics))
        vm.onEmailChanged("a@b.ph")
        vm.onPasswordChanged("password1")
        vm.onRepeatPasswordChanged("password1")
        vm.submit()

        assertEquals(AppError.EmailInUse, vm.uiState.value.error)
        assertNull(vm.uiState.value.verificationSentTo)
    }

    @Test
    fun `password reset confirms without revealing whether the account exists`() {
        val vm = ForgotPasswordViewModel(SendPasswordResetUseCase(auth))
        vm.onEmailChanged("unknown@b.ph")
        vm.submit()

        assertEquals("unknown@b.ph", vm.uiState.value.resetRequestedFor)
        assertEquals(listOf("reset:unknown@b.ph"), auth.calls)
    }
}
