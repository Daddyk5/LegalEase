package com.hcdc.legalease.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialValidatorTest {

    @Test
    fun `accepts ordinary addresses, trimming spaces`() {
        assertNull(CredentialValidator.email("juan.dela.cruz@gmail.com"))
        assertNull(CredentialValidator.email("  maria@hcdc.edu.ph "))
    }

    @Test
    fun `rejects malformed addresses`() {
        listOf("", "juan", "juan@", "@gmail.com", "juan@gmail", "juan @gmail.com", "a@b@c.com").forEach {
            assertEquals(it, CredentialProblem.EMAIL_INVALID, CredentialValidator.email(it))
        }
    }

    @Test
    fun `password needs eight characters`() {
        assertEquals(CredentialProblem.PASSWORD_TOO_SHORT, CredentialValidator.password("1234567"))
        assertNull(CredentialValidator.password("12345678"))
    }

    @Test
    fun `repeat password must match exactly`() {
        assertEquals(CredentialProblem.PASSWORDS_DIFFER, CredentialValidator.repeatPassword("secret12", "Secret12"))
        assertNull(CredentialValidator.repeatPassword("secret12", "secret12"))
    }

    @Test
    fun `greeting prefers display name, then email local part`() {
        assertEquals("Maria", AuthUser("u", "maria@x.ph", "Maria", true).greetingName)
        assertEquals("juan", AuthUser("u", "juan@x.ph", " ", true).greetingName)
        assertNull(AuthUser("u", null, null, true).greetingName)
    }
}
