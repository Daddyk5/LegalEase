package com.hcdc.legalease.core.ml

import org.junit.Assert.assertEquals
import org.junit.Test

class VocabTest {

    @Test
    fun `parses a token map`() {
        assertEquals(mapOf("lease" to 2, "loan" to 10), parseVocab("""{"lease": 2, "loan": 10}"""))
    }

    @Test(expected = ClassifierException.VocabUnavailable::class)
    fun `empty vocab is an error, not a silent empty map`() {
        parseVocab("{}")
    }

    @Test(expected = ClassifierException.VocabUnavailable::class)
    fun `malformed json is an error`() {
        parseVocab("""{"lease": """)
    }

    @Test(expected = ClassifierException.VocabUnavailable::class)
    fun `non-integer ids are an error`() {
        parseVocab("""{"lease": "two"}""")
    }
}
