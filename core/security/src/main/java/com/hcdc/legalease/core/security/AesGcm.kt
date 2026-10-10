package com.hcdc.legalease.core.security

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM sealing for small secrets. The format is
 * `[version: 1 byte][iv length: 1 byte][iv][ciphertext + 16-byte tag]`.
 */
object AesGcm {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val FORMAT_VERSION: Byte = 1
    private const val TAG_BITS = 128

    /** Encrypts [plaintext]; the cipher picks a fresh random IV (required by Android Keystore). */
    fun seal(key: SecretKey, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key) }
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(2 + iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(iv.size.toByte())
            .put(iv)
            .put(ciphertext)
            .array()
    }

    /** @throws java.security.GeneralSecurityException if the data was tampered with or the key is wrong. */
    fun open(key: SecretKey, sealed: ByteArray): ByteArray {
        val buffer = ByteBuffer.wrap(sealed)
        val version = buffer.get()
        require(version == FORMAT_VERSION) { "Unsupported sealed format $version" }
        val iv = ByteArray(buffer.get().toInt()).also(buffer::get)
        val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        return cipher.doFinal(ciphertext)
    }
}
