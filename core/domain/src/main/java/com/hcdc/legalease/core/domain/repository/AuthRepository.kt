package com.hcdc.legalease.core.domain.repository

import com.hcdc.legalease.core.domain.model.AuthUser
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.flow.Flow

/** Firebase Authentication. Every failure is an [com.hcdc.legalease.core.domain.result.AppError]. */
interface AuthRepository {

    val currentUser: Flow<AuthUser?>

    /** Signs in, then fails with EmailNotVerified (staying signed in so a resend works) if unverified. */
    suspend fun signIn(email: String, password: String): AppResult<AuthUser>

    /** Creates the account, sends the verification email, then signs out until it is verified. */
    suspend fun register(email: String, password: String): AppResult<Unit>

    /** Succeeds whether or not an account exists for [email], so it cannot be used to probe accounts. */
    suspend fun sendPasswordReset(email: String): AppResult<Unit>

    suspend fun signInWithGoogle(idToken: String): AppResult<AuthUser>

    /** Resends the verification email to the currently signed-in, unverified user. */
    suspend fun resendVerification(): AppResult<Unit>

    suspend fun signOut()
}
