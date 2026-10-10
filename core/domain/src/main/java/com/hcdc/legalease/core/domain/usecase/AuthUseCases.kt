package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.model.AuthUser
import com.hcdc.legalease.core.domain.repository.AuthRepository
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.telemetry.AnalyticsEvent
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUserUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(): Flow<AuthUser?> = auth.currentUser
}

class SignInUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<AuthUser> =
        auth.signIn(email.trim(), password).also {
            if (it is AppResult.Success) analytics.log(AnalyticsEvent.Login(AnalyticsEvent.METHOD_EMAIL))
        }
}

class SignInWithGoogleUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(idToken: String): AppResult<AuthUser> =
        auth.signInWithGoogle(idToken).also {
            if (it is AppResult.Success) analytics.log(AnalyticsEvent.Login(AnalyticsEvent.METHOD_GOOGLE))
        }
}

class RegisterUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val analytics: AnalyticsTracker,
) {
    suspend operator fun invoke(email: String, password: String): AppResult<Unit> =
        auth.register(email.trim(), password).also {
            if (it is AppResult.Success) analytics.log(AnalyticsEvent.SignUp(AnalyticsEvent.METHOD_EMAIL))
        }
}

class SendPasswordResetUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): AppResult<Unit> = auth.sendPasswordReset(email.trim())
}

class ResendVerificationUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(): AppResult<Unit> = auth.resendVerification()
}

class SignOutUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke() = auth.signOut()
}
