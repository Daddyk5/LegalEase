package com.hcdc.legalease.core.data.local

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator

class AesGcmTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `seal then open returns the plaintext`() {
        val secret = ByteArray(32) { it.toByte() }
        assertArrayEquals(secret, AesGcm.open(key, AesGcm.seal(key, secret)))
    }

    @Test
    fun `each seal uses a fresh IV`() {
        val secret = ByteArray(32)
        assertFalse(AesGcm.seal(key, secret).contentEquals(AesGcm.seal(key, secret)))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `tampered ciphertext is rejected`() {
        val sealed = AesGcm.seal(key, ByteArray(32))
        sealed[sealed.size - 1] = (sealed.last() + 1).toByte()
        AesGcm.open(key, sealed)
    }

    @Test(expected = GeneralSecurityException::class)
    fun `wrong key is rejected`() {
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        AesGcm.open(other, AesGcm.seal(key, ByteArray(32)))
    }
}
