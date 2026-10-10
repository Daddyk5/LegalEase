package com.hcdc.legalease.core.ml.llm

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Decrypts the prompts sealed into [SealedPrompts] at build time. Results are cached in memory. */
internal object PromptVault {
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    val extractSystem: String by lazy { open(SealedPrompts.EXTRACT_SYSTEM) }
    val reasonSystem: String by lazy { open(SealedPrompts.REASON_SYSTEM) }
    val civilCodeReference: String by lazy { open(SealedPrompts.CIVIL_CODE_REFERENCE) }
    val clauseSuggestSystem: String by lazy { open(SealedPrompts.CLAUSE_SUGGEST_SYSTEM) }
    val clauseRewriteSystem: String by lazy { open(SealedPrompts.CLAUSE_REWRITE_SYSTEM) }

    private val key by lazy {
        ByteArray(SealedPrompts.A.size) { (SealedPrompts.A[it] xor SealedPrompts.B[it]).toByte() }
    }

    private fun open(sealed: String): String {
        val bytes = Base64.getDecoder().decode(sealed)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }
}
