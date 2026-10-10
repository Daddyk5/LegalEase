package com.hcdc.legalease.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.io.File
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Non-exportable AES-256-GCM keys in the Android Keystore, StrongBox-backed where the device has it.
 * These keys only wrap small secrets: StrongBox is far too slow for bulk data.
 */
object KeystoreKeys {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_BITS = 256

    fun getOrCreate(alias: String): SecretKey =
        (keyStore().getKey(alias, null) as? SecretKey) ?: try {
            generate(alias, strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(alias, strongBox = false)
        }

    fun delete(alias: String) {
        keyStore().deleteEntry(alias)
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun generate(alias: String, strongBox: Boolean): SecretKey {
        val purposes = KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        val spec = KeyGenParameterSpec.Builder(alias, purposes)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_BITS)
            .setIsStrongBoxBacked(strongBox)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }
}

/**
 * A random secret stored in [file], sealed with [wrappingKey] (envelope encryption). The plaintext
 * secret never touches disk.
 */
class SealedSecret(
    private val file: File,
    private val wrappingKey: () -> SecretKey,
    private val sizeBytes: Int = DEFAULT_SIZE_BYTES,
) {
    val exists: Boolean get() = file.exists()

    /**
     * Returns the secret, creating it on first use.
     *
     * @throws GeneralSecurityException if the sealed secret can no longer be opened.
     */
    @Synchronized
    fun getOrCreate(): ByteArray {
        if (file.exists()) return AesGcm.open(wrappingKey(), file.readBytes())
        val secret = ByteArray(sizeBytes).also(SecureRandom()::nextBytes)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeBytes(AesGcm.seal(wrappingKey(), secret))
        check(tmp.renameTo(file)) { "Could not store sealed secret" }
        return secret
    }

    /** The secret as an AES key, for bulk encryption in software. */
    fun getOrCreateKey(): SecretKey = SecretKeySpec(getOrCreate(), "AES")

    @Synchronized
    fun delete() {
        file.delete()
    }

    companion object {
        const val DEFAULT_SIZE_BYTES = 32
    }
}
