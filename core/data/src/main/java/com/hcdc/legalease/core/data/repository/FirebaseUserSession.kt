package com.hcdc.legalease.core.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.hcdc.legalease.core.domain.repository.UserSession
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

internal class FirebaseUserSession @Inject constructor(
    private val auth: FirebaseAuth,
) : UserSession {

    override val currentUserId: Flow<String?> = callbackFlow {
        // Unverified users cannot pass the Firestore rules, so they are not a sync target.
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser?.takeIf { it.isEmailVerified }?.uid)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()
}
