package com.hcdc.legalease.feature.auth.sheet.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.launch

/**
 * Returns a function that runs the "Sign in with Google" flow via Credential Manager and
 * reports a Google ID token, or [onUnavailable] if the device cannot provide one. A user
 * dismissing the account picker is not an error and reports nothing.
 */
@Composable
internal fun rememberGoogleSignIn(
    webClientId: String,
    onIdToken: (String) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnIdToken by rememberUpdatedState(onIdToken)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    return remember(context, webClientId) {
        {
            scope.launch {
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                    .build()
                try {
                    val credential = CredentialManager.create(context).getCredential(context, request).credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                    ) {
                        currentOnIdToken(GoogleIdTokenCredential.createFrom(credential.data).idToken)
                    } else {
                        currentOnUnavailable()
                    }
                } catch (_: GetCredentialCancellationException) {
                    // The user closed the picker.
                } catch (_: GetCredentialException) {
                    currentOnUnavailable()
                } catch (_: GoogleIdTokenParsingException) {
                    currentOnUnavailable()
                }
            }
        }
    }
}
