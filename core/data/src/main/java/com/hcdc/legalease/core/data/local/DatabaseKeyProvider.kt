package com.hcdc.legalease.core.data.local

import android.content.Context
import android.util.Log
import com.hcdc.legalease.core.security.KeystoreKeys
import com.hcdc.legalease.core.security.SealedSecret
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplies the SQLCipher passphrase.
 *
 * A random 256-bit passphrase is generated once and stored in `noBackupFilesDir`, sealed with a
 * non-exportable AES-256-GCM key in the Android Keystore (StrongBox when the device has it). The
 * passphrase never exists in plain form on disk, and the database cannot be opened off-device.
 */
@Singleton
class DatabaseKeyProvider @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val secret = SealedSecret(
        file = File(context.noBackupFilesDir, KEY_FILE),
        wrappingKey = { KeystoreKeys.getOrCreate(KEY_ALIAS) },
    )

    /**
     * Returns the existing passphrase, or creates one.
     *
     * @throws GeneralSecurityException if the sealed passphrase can no longer be opened, e.g. the
     *   Keystore key was invalidated. The caller must then discard the database via [reset].
     */
    fun passphrase(): ByteArray = secret.getOrCreate()

    /** Forgets the passphrase and Keystore key. Any database encrypted with them becomes unreadable. */
    fun reset() {
        secret.delete()
        runCatching { KeystoreKeys.delete(KEY_ALIAS) }
            .onFailure { Log.w(TAG, "Could not delete Keystore entry", it) }
    }

    private companion object {
        const val TAG = "DatabaseKeyProvider"
        const val KEY_ALIAS = "legalease_db_kek"
        const val KEY_FILE = "db.key"
    }
}
