package com.hcdc.legalease.core.domain.usecase

import com.hcdc.legalease.core.domain.model.AuthUser
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.testing.FakeAnalytics
import com.hcdc.legalease.core.domain.testing.FakeAuthRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthUseCasesTest {

    private val auth = FakeAuthRepository()
    private val analytics = FakeAnalytics()

    @Test
    fun `sign-in trims the email and logs the method, never the address`() = runTest {
        auth.signInResult = AppResult.Success(AuthUser("u1", "a@b.ph", null, true))

        SignInUseCase(auth, analytics)("  a@b.ph ", "password1")

        assertEquals(listOf("signIn:a@b.ph"), auth.calls)
        assertEquals(mapOf("method" to "email"), analytics.events.single().params)
    }

    @Test
    fun `failed sign-in logs nothing`() = runTest {
        auth.signInResult = AppResult.Failure(AppError.EmailNotVerified)

        val result = SignInUseCase(auth, analytics)("a@b.ph", "password1")

        assertEquals(AppResult.Failure(AppError.EmailNotVerified), result)
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun `registration logs sign_up`() = runTest {
        RegisterUseCase(auth, analytics)("a@b.ph", "password1")
        assertEquals("sign_up", analytics.events.single().name)
    }
}
