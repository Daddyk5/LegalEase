package com.hcdc.legalease.core.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
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
    @ApplicationContext private val context: Context,
) {
    private val keyFile: File get() = File(context.noBackupFilesDir, KEY_FILE)

    /** True if a passphrase file exists, i.e. a database encrypted with it may exist. */
    val hasPassphrase: Boolean get() = keyFile.exists()

    /**
     * Returns the existing passphrase, or creates one.
     *
     * @throws GeneralSecurityException if the sealed passphrase can no longer be opened, e.g. the
     *   Keystore key was invalidated. The caller must then discard the database via [reset].
     */
    @Synchronized
    fun passphrase(): ByteArray {
        val file = keyFile
        if (file.exists()) return AesGcm.open(wrappingKey(), file.readBytes())

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val tmp = File(file.parentFile, "$KEY_FILE.tmp")
        tmp.writeBytes(AesGcm.seal(wrappingKey(), passphrase))
        check(tmp.renameTo(file)) { "Could not store database key" }
        return passphrase
    }

    /** Forgets the passphrase and Keystore key. Any database encrypted with them becomes unreadable. */
    @Synchronized
    fun reset() {
        keyFile.delete()
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
            .onFailure { Log.w(TAG, "Could not delete Keystore entry", it) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun wrappingKey(): SecretKey =
        (keyStore().getKey(KEY_ALIAS, null) as? SecretKey) ?: generateWrappingKey()

    private fun generateWrappingKey(): SecretKey = try {
        generateWrappingKey(strongBox = true)
    } catch (_: StrongBoxUnavailableException) {
        generateWrappingKey(strongBox = false)
    }

    private fun generateWrappingKey(strongBox: Boolean): SecretKey {
        val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        val spec = KeyGenParameterSpec.Builder(KEY_ALIAS, purposes)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }

    private companion object {
        const val TAG = "DatabaseKeyProvider"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "legalease_db_kek"
        const val KEY_FILE = "db.key"
        const val KEY_BITS = 256
        const val PASSPHRASE_BYTES = 32
    }
}
