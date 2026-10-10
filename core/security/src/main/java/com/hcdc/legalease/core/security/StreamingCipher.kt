package com.hcdc.legalease.core.security

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Chunked AES-256-GCM for files too large to hold in memory (on-device model weights).
 *
 * Format: magic `LEC1`, then records of `[chunk length: int][12-byte IV][ciphertext + tag]`.
 * Each chunk's associated data binds its index and whether it is the last chunk, so chunks
 * cannot be reordered, dropped, or the file truncated without detection.
 */
object StreamingCipher {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val MAGIC = byteArrayOf('L'.code.toByte(), 'E'.code.toByte(), 'C'.code.toByte(), '1'.code.toByte())
    const val DEFAULT_CHUNK_BYTES = 1 shl 20

    fun encrypt(key: SecretKey, input: InputStream, output: OutputStream, chunkBytes: Int = DEFAULT_CHUNK_BYTES) {
        val out = DataOutputStream(output)
        out.write(MAGIC)
        val random = SecureRandom()
        var current = readChunk(input, chunkBytes)
        var index = 0L
        while (true) {
            val next = if (current.size == chunkBytes) readChunk(input, chunkBytes) else ByteArray(0)
            val isLast = next.isEmpty()
            val iv = ByteArray(IV_BYTES).also(random::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(aad(index, isLast))
            }
            val sealed = cipher.doFinal(current)
            out.writeInt(sealed.size)
            out.write(iv)
            out.write(sealed)
            if (isLast) break
            current = next
            index++
        }
        out.flush()
    }

    /** @throws GeneralSecurityException if the data is corrupt, truncated, reordered, or the key is wrong. */
    fun decrypt(key: SecretKey, input: InputStream, output: OutputStream) {
        val inp = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        inp.readFully(magic)
        if (!magic.contentEquals(MAGIC)) throw GeneralSecurityException("Not an encrypted model file")
        var index = 0L
        var finished = false
        while (!finished) {
            val (iv, sealed) = readSealedChunk(inp)
            // Try "not last" first; the final chunk is the only one that authenticates as last.
            val plain = openChunk(key, iv, sealed, index, isLast = false)
            if (plain != null) {
                output.write(plain)
            } else {
                output.write(openFinalChunk(key, iv, sealed, index, inp))
                finished = true
            }
            index++
        }
        output.flush()
    }

    private fun readSealedChunk(inp: DataInputStream): Pair<ByteArray, ByteArray> {
        val length = try {
            inp.readInt()
        } catch (_: EOFException) {
            throw GeneralSecurityException("Truncated: no final chunk")
        }
        if (length < TAG_BITS / Byte.SIZE_BITS) throw GeneralSecurityException("Corrupt chunk length")
        val iv = ByteArray(IV_BYTES).also(inp::readFully)
        return iv to ByteArray(length).also(inp::readFully)
    }

    private fun openFinalChunk(
        key: SecretKey,
        iv: ByteArray,
        sealed: ByteArray,
        index: Long,
        rest: InputStream,
    ): ByteArray {
        val last = openChunk(key, iv, sealed, index, isLast = true)
            ?: throw GeneralSecurityException("Chunk $index failed authentication")
        if (rest.read() != -1) throw GeneralSecurityException("Data after final chunk")
        return last
    }

    private fun openChunk(key: SecretKey, iv: ByteArray, sealed: ByteArray, index: Long, isLast: Boolean): ByteArray? =
        try {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
                updateAAD(aad(index, isLast))
            }.doFinal(sealed)
        } catch (_: javax.crypto.AEADBadTagException) {
            null
        }

    private fun aad(index: Long, isLast: Boolean): ByteArray =
        ByteBuffer.allocate(Long.SIZE_BYTES + 1).putLong(index).put(if (isLast) 1 else 0).array()

    private fun readChunk(input: InputStream, size: Int): ByteArray {
        val buffer = ByteArray(size)
        var read = 0
        while (read < size) {
            val n = input.read(buffer, read, size - read)
            if (n < 0) break
            read += n
        }
        return if (read == size) buffer else buffer.copyOf(read)
    }
}
