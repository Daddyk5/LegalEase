package com.hcdc.legalease.feature.auth

import androidx.annotation.StringRes
import com.hcdc.legalease.core.domain.model.CredentialProblem
import com.hcdc.legalease.core.domain.result.AppError

@StringRes
internal fun CredentialProblem.messageRes(): Int = when (this) {
    CredentialProblem.EMAIL_INVALID -> R.string.auth_error_email_invalid
    CredentialProblem.PASSWORD_TOO_SHORT -> R.string.auth_error_password_short
    CredentialProblem.PASSWORDS_DIFFER -> R.string.auth_error_passwords_differ
}

@StringRes
internal fun AppError.authMessageRes(): Int = when (this) {
    AppError.InvalidCredentials -> R.string.auth_error_invalid_credentials
    AppError.EmailNotVerified -> R.string.auth_error_email_not_verified
    AppError.EmailInUse -> R.string.auth_error_email_in_use
    AppError.WeakPassword -> R.string.auth_error_weak_password
    AppError.TooManyRequests -> R.string.auth_error_too_many_requests
    AppError.Network -> R.string.auth_error_network
    else -> R.string.auth_error_generic
}
