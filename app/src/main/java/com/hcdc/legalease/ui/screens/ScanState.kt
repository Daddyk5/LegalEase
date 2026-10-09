package com.hcdc.legalease.ui.screens

/** Progress of turning an uploaded PDF or set of images into text. */
sealed interface ScanState {
    data object Idle : ScanState

    /** [currentPage] is 1-based; [totalPages] is 0 until the page count is known. */
    data class Scanning(val currentPage: Int, val totalPages: Int) : ScanState

    data class Failed(val reason: ScanFailure) : ScanState
}

enum class ScanFailure {
    /** The file could not be opened (damaged, password-protected, or permission revoked). */
    UNREADABLE_FILE,

    /** Every page was read but none contained recognisable text. */
    NO_TEXT_FOUND,
}
