package com.hcdc.legalease.core.domain.result

/**
 * Every failure the UI can show. Lower layers catch their exceptions and map them here,
 * so no raw exception reaches a screen.
 *
 * [code] is stable and persisted (Room, Firestore); never rename an existing code.
 */
sealed class AppError(val code: String) {

    /** The analysis ID is unknown, e.g. it was deleted. */
    data object DocumentNotFound : AppError("document_not_found")

    /** The uploaded file could not be opened (damaged, password-protected, or permission revoked). */
    data object UnreadableFile : AppError("unreadable_file")

    /** Every page was read but none contained recognisable text. */
    data object NoTextFound : AppError("no_text_found")

    /** The offline model has never been downloaded and the device is offline. */
    data object ModelUnavailable : AppError("model_unavailable")

    /** The bundled vocabulary or downloaded model does not match this app version. */
    data object ModelIncompatible : AppError("model_incompatible")

    /** The device has no space left for the copied document or database. */
    data object StorageFull : AppError("storage_full")

    /** The user cancelled the analysis. */
    data object Cancelled : AppError("cancelled")

    /** A network call failed; local data is unaffected. */
    data object Network : AppError("network")

    /** A cloud operation was attempted without a signed-in user. */
    data object NotSignedIn : AppError("not_signed_in")

    /** Wrong email or password. Deliberately does not say which, to avoid account enumeration. */
    data object InvalidCredentials : AppError("invalid_credentials")

    /** The account exists but its email address has not been verified yet. */
    data object EmailNotVerified : AppError("email_not_verified")

    /** Registration with an email that already has an account. */
    data object EmailInUse : AppError("email_in_use")

    /** The auth provider rejected the password as too weak. */
    data object WeakPassword : AppError("weak_password")

    /** Too many attempts; the user should wait before retrying. */
    data object TooManyRequests : AppError("too_many_requests")

    /** A failure with no specific handling. [detail] is for logs only, never document content. */
    data class Unexpected(val detail: String) : AppError(UNEXPECTED_CODE)

    companion object {
        private const val UNEXPECTED_CODE = "unexpected"

        private val known by lazy {
            listOf(
                DocumentNotFound, UnreadableFile, NoTextFound, ModelUnavailable,
                ModelIncompatible, StorageFull, Cancelled, Network, NotSignedIn,
                InvalidCredentials, EmailNotVerified, EmailInUse, WeakPassword, TooManyRequests,
            ).associateBy { it.code }
        }

        fun fromCode(code: String): AppError = known[code] ?: Unexpected(code)
    }
}
