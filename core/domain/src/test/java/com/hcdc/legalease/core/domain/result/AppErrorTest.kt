package com.hcdc.legalease.core.domain.result

import org.junit.Assert.assertEquals
import org.junit.Test

class AppErrorTest {

    @Test
    fun `known codes round-trip`() {
        listOf(
            AppError.DocumentNotFound, AppError.UnreadableFile, AppError.NoTextFound, AppError.ModelUnavailable,
            AppError.ModelIncompatible, AppError.StorageFull, AppError.Cancelled, AppError.Network,
            AppError.NotSignedIn, AppError.InvalidCredentials, AppError.EmailNotVerified, AppError.EmailInUse,
            AppError.WeakPassword, AppError.TooManyRequests,
        ).forEach { assertEquals(it, AppError.fromCode(it.code)) }
    }

    @Test
    fun `unknown codes become Unexpected instead of throwing`() {
        assertEquals(AppError.Unexpected("from_a_newer_app_version"), AppError.fromCode("from_a_newer_app_version"))
    }

    @Test
    fun `map and flatMap pass failures through`() {
        val failure: AppResult<Int> = AppResult.Failure(AppError.Network)
        assertEquals(failure, failure.map { it + 1 })
        assertEquals(AppResult.Success(2), AppResult.Success(1).flatMap { AppResult.Success(it + 1) })
    }
}
