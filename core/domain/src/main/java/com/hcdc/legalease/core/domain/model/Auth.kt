package com.hcdc.legalease.core.domain.model

/** The signed-in Firebase user. */
data class AuthUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val isEmailVerified: Boolean,
) {
    /** Name for greetings: the profile name, else the part of the email before "@". */
    val greetingName: String?
        get() = displayName?.takeIf { it.isNotBlank() } ?: email?.substringBefore('@')?.takeIf { it.isNotBlank() }
}

/** OAuth client used for Google sign-in, supplied by the app from google-services.json. */
data class GoogleSignInConfig(val webClientId: String)

/** Field-level problems found before any network call. */
enum class CredentialProblem { EMAIL_INVALID, PASSWORD_TOO_SHORT, PASSWORDS_DIFFER }

/** Client-side checks for the auth forms. The server still enforces its own rules. */
object CredentialValidator {
    const val MIN_PASSWORD_LENGTH = 8

    // Deliberately permissive: one "@", a dot in the domain, no spaces. Firebase does the real check.
    private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun email(email: String): CredentialProblem? =
        if (EMAIL.matches(email.trim())) null else CredentialProblem.EMAIL_INVALID

    fun password(password: String): CredentialProblem? =
        if (password.length >= MIN_PASSWORD_LENGTH) null else CredentialProblem.PASSWORD_TOO_SHORT

    fun repeatPassword(password: String, repeat: String): CredentialProblem? =
        if (password == repeat) null else CredentialProblem.PASSWORDS_DIFFER
}
