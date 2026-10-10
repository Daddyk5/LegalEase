package com.hcdc.legalease.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import kotlin.random.Random

class StreamingCipherTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private fun encrypt(data: ByteArray, chunk: Int = 1024) =
        ByteArrayOutputStream()
            .also { StreamingCipher.encrypt(key, ByteArrayInputStream(data), it, chunk) }
            .toByteArray()

    private fun decrypt(sealed: ByteArray) =
        ByteArrayOutputStream().also { StreamingCipher.decrypt(key, ByteArrayInputStream(sealed), it) }.toByteArray()

    @Test
    fun `round-trips data across many chunks`() {
        val data = Random(1).nextBytes(10_000)
        assertArrayEquals(data, decrypt(encrypt(data)))
    }

    @Test
    fun `round-trips an exact multiple of the chunk size and empty input`() {
        val data = Random(2).nextBytes(4096)
        assertArrayEquals(data, decrypt(encrypt(data)))
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `detects a flipped bit`() {
        val sealed = encrypt(Random(3).nextBytes(5000))
        sealed[sealed.size / 2] = (sealed[sealed.size / 2].toInt() xor 1).toByte()
        decrypt(sealed)
    }

    @Test(expected = GeneralSecurityException::class)
    fun `detects truncation at a chunk boundary`() {
        val sealed = encrypt(Random(4).nextBytes(5000))
        // magic(4) + one full record: length(4) + iv(12) + 1024 + tag(16)
        decrypt(sealed.copyOf(4 + 4 + 12 + 1024 + 16))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `rejects the wrong key`() {
        val sealed = encrypt(Random(5).nextBytes(100))
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        StreamingCipher.decrypt(other, ByteArrayInputStream(sealed), ByteArrayOutputStream())
    }
}
