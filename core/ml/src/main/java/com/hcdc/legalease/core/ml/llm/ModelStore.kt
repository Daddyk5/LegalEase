package com.hcdc.legalease.core.ml.llm

import android.content.Context
import com.hcdc.legalease.core.security.KeystoreKeys
import com.hcdc.legalease.core.security.SealedSecret
import com.hcdc.legalease.core.security.StreamingCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.InputStream
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Model weights at rest: AES-256-GCM in streamed chunks, under `filesDir/.cache_core/` (excluded
 * from backups). The data key is random and sealed by a non-exportable Keystore key (StrongBox
 * when available); bulk encryption runs in software because StrongBox is far too slow for it.
 *
 * At load time the model is decrypted to a temporary file in `noBackupFilesDir`, which the engine
 * deletes as soon as it has loaded. This is deterrence against casual extraction, not DRM.
 */
@Singleton
class ModelStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val dir = File(context.filesDir, ".cache_core")
    private val runtimeDir = File(context.noBackupFilesDir, ".rt")
    private val dataKey = SealedSecret(
        file = File(context.noBackupFilesDir, ".cck"),
        wrappingKey = { KeystoreKeys.getOrCreate(KEY_ALIAS) },
    )

    sealed interface ImportResult {
        data object Imported : ImportResult
        data class HashMismatch(val actual: String) : ImportResult
    }

    private fun encrypted(spec: ModelSpec) = File(dir, spec.tier.fileName)
    private fun marker(spec: ModelSpec) = File(dir, spec.tier.fileName + ".ok")

    /** True if this exact pinned model has been verified and stored. */
    fun isReady(spec: ModelSpec): Boolean =
        spec.isPinned && encrypted(spec).exists() && marker(spec).takeIf { it.exists() }?.readText() == spec.sha256

    /**
     * Hashes [plain] while encrypting it, and keeps the result only if the hash matches the pinned one.
     * [plain] is read exactly once.
     */
    fun import(spec: ModelSpec, plain: InputStream): ImportResult {
        dir.mkdirs()
        val tmp = File(dir, spec.tier.fileName + ".part")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            DigestInputStream(plain, digest).use { input ->
                tmp.outputStream().buffered().use { output ->
                    StreamingCipher.encrypt(dataKey.getOrCreateKey(), input, output)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (actual != spec.sha256) return ImportResult.HashMismatch(actual)
            marker(spec).delete()
            check(tmp.renameTo(encrypted(spec))) { "Could not store model" }
            marker(spec).writeText(spec.sha256)
            return ImportResult.Imported
        } finally {
            tmp.delete()
        }
    }

    /** Decrypts to a fresh temporary file. The caller must delete it as soon as the engine has loaded. */
    fun decryptToTemp(spec: ModelSpec): File {
        runtimeDir.mkdirs()
        val tmp = File.createTempFile("m", null, runtimeDir)
        try {
            encrypted(spec).inputStream().buffered().use { input ->
                tmp.outputStream().buffered().use { output ->
                    StreamingCipher.decrypt(dataKey.getOrCreateKey(), input, output)
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            tmp.delete()
            throw e
        }
        return tmp
    }

    /** Scratch space for the runtime's own caches; cleared whenever the engine is released. */
    fun runtimeCacheDir(): File = File(runtimeDir, "cache").apply { mkdirs() }

    fun clearRuntimeFiles() {
        runtimeDir.deleteRecursively()
    }

    fun delete(spec: ModelSpec) {
        encrypted(spec).delete()
        marker(spec).delete()
    }

    private companion object {
        const val KEY_ALIAS = "legalease_core_kek"
    }
}
