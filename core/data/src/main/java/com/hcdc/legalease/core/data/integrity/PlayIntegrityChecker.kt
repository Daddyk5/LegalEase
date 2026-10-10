package com.hcdc.legalease.core.data.integrity

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import com.google.firebase.FirebaseApp
import com.google.firebase.functions.FirebaseFunctions
import com.hcdc.legalease.core.domain.engine.IntegrityChecker
import com.hcdc.legalease.core.domain.model.DeviceIntegrity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Play Integrity (standard request). The token is decoded by the `integrityVerdict` Cloud Function,
 * because a verdict can only be read server-side; the app receives TRUSTED, COMPROMISED, or nothing.
 *
 * The last verdict is kept for a day, so a compromised device stays flagged while offline. When the
 * check cannot complete (no Play services, offline, function not deployed) the verdict stays as it
 * was, and a device is never marked compromised without a verdict saying so.
 */
@Singleton
internal class PlayIntegrityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
    private val functions: FirebaseFunctions,
    private val clock: Clock,
) : IntegrityChecker {

    private val state = MutableStateFlow(DeviceIntegrity.UNKNOWN)
    override val verdict: StateFlow<DeviceIntegrity> = state.asStateFlow()
    private val mutex = Mutex()

    override suspend fun refresh() = mutex.withLock {
        val prefs = dataStore.data.first()
        val cached = DeviceIntegrity.entries.firstOrNull { it.name == prefs[VERDICT] }
        if (cached != null) state.value = cached
        val fresh = cached != null && clock.millis() - (prefs[CHECKED_AT] ?: 0L) < MAX_AGE.toMillis()
        if (fresh) return@withLock
        val result = check() ?: return@withLock
        state.value = result
        dataStore.edit {
            it[VERDICT] = result.name
            it[CHECKED_AT] = clock.millis()
        }
    }

    @Suppress("TooGenericExceptionCaught") // Play services and Functions fail in many ways; all mean "no verdict".
    private suspend fun check(): DeviceIntegrity? = try {
        val projectNumber = FirebaseApp.getInstance().options.gcmSenderId?.toLongOrNull()
            ?: error("No Cloud project number")
        val provider = IntegrityManagerFactory.createStandard(context)
            .prepareIntegrityToken(PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(projectNumber).build())
            .await()
        // A fresh hash per request; the server checks the token was issued for exactly this request.
        val requestHash = sha256Hex(UUID.randomUUID().toString())
        val token = provider.request(StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build())
            .await()
            .token()
        val response = functions.getHttpsCallable(FUNCTION)
            .call(mapOf("token" to token, "requestHash" to requestHash))
            .await()
        when ((response.data as? Map<*, *>)?.get("verdict")) {
            "TRUSTED" -> DeviceIntegrity.TRUSTED
            "COMPROMISED" -> DeviceIntegrity.COMPROMISED
            else -> null
        }
    } catch (e: Exception) {
        Log.i(TAG, "Integrity check unavailable (${e.javaClass.simpleName})")
        null
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val TAG = "Integrity"
        const val FUNCTION = "integrityVerdict"
        val MAX_AGE: Duration = Duration.ofDays(1)
        val VERDICT = stringPreferencesKey("integrity_verdict")
        val CHECKED_AT = longPreferencesKey("integrity_checked_at")
    }
}

/** For debug builds, which Play never recognises: the check is skipped and nothing is disabled. */
internal class DisabledIntegrityChecker @Inject constructor() : IntegrityChecker {
    override val verdict: StateFlow<DeviceIntegrity> = MutableStateFlow(DeviceIntegrity.UNKNOWN)

    override suspend fun refresh() = Unit
}
