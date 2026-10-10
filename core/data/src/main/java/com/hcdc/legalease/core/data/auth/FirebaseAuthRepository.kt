package com.hcdc.legalease.core.data.auth

import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.hcdc.legalease.core.data.remote.FirestoreSchema
import com.hcdc.legalease.core.domain.model.AuthUser
import com.hcdc.legalease.core.domain.repository.AuthRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import java.time.Clock
import javax.inject.Inject

internal class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val clock: Clock,
) : AuthRepository {

    override val currentUser: Flow<AuthUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toDomain()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

    override suspend fun signIn(email: String, password: String): AppResult<AuthUser> = guard {
        val user = checkNotNull(auth.signInWithEmailAndPassword(email, password).await().user)
        if (!user.isEmailVerified) return AppResult.Failure(AppError.EmailNotVerified)
        recordSignIn(user)
        user.toDomain()
    }

    override suspend fun register(email: String, password: String): AppResult<Unit> = guard {
        val user = checkNotNull(auth.createUserWithEmailAndPassword(email, password).await().user)
        try {
            user.sendEmailVerification().await()
        } finally {
            // Unverified accounts may not use the app; they sign in again after verifying.
            auth.signOut()
        }
    }

    override suspend fun sendPasswordReset(email: String): AppResult<Unit> =
        when (val result = guard { auth.sendPasswordResetEmail(email).await().let { } }) {
            // "No such user" must look like success, or the form could be used to probe for accounts.
            AppResult.Failure(AppError.InvalidCredentials) -> AppResult.Success(Unit)
            else -> result
        }

    override suspend fun signInWithGoogle(idToken: String): AppResult<AuthUser> = guard {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val user = checkNotNull(auth.signInWithCredential(credential).await().user)
        recordSignIn(user)
        user.toDomain()
    }

    override suspend fun resendVerification(): AppResult<Unit> {
        val user = auth.currentUser ?: return AppResult.Failure(AppError.NotSignedIn)
        return guard { user.sendEmailVerification().await().let { } }
    }

    override suspend fun signOut() {
        auth.signOut()
    }

    /**
     * Keeps `users/{uid}` present for the signed-in user. Not awaited: Firestore queues the write
     * while offline, and sign-in must not depend on it.
     */
    private fun recordSignIn(user: FirebaseUser) {
        firestore.collection(FirestoreSchema.USERS).document(user.uid)
            .set(
                mapOf(
                    FirestoreSchema.F_SCHEMA to FirestoreSchema.SCHEMA_VERSION,
                    "lastSignInAt" to clock.millis(),
                ),
                SetOptions.merge(),
            )
            .addOnFailureListener { Log.w(TAG, "Profile write failed", it) }
    }

    /** Maps Firebase failures to [AppError]. Order matters: weak password is a subtype of invalid credentials. */
    private inline fun <T> guard(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (_: FirebaseAuthWeakPasswordException) {
        AppResult.Failure(AppError.WeakPassword)
    } catch (_: FirebaseAuthInvalidCredentialsException) {
        AppResult.Failure(AppError.InvalidCredentials)
    } catch (_: FirebaseAuthInvalidUserException) {
        AppResult.Failure(AppError.InvalidCredentials)
    } catch (_: FirebaseAuthUserCollisionException) {
        AppResult.Failure(AppError.EmailInUse)
    } catch (_: FirebaseTooManyRequestsException) {
        AppResult.Failure(AppError.TooManyRequests)
    } catch (_: FirebaseNetworkException) {
        AppResult.Failure(AppError.Network)
    } catch (e: FirebaseAuthException) {
        AppResult.Failure(AppError.Unexpected(e.errorCode))
    } catch (e: FirebaseException) {
        AppResult.Failure(AppError.Unexpected(e.javaClass.simpleName))
    }

    private fun FirebaseUser.toDomain() = AuthUser(uid, email, displayName, isEmailVerified)

    private companion object {
        const val TAG = "FirebaseAuthRepository"
    }
}
