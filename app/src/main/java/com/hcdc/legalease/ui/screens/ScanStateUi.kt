package com.hcdc.legalease.ui.screens

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.hcdc.legalease.R

@Composable
fun ScanState.Scanning.progressMessage(): String =
    if (totalPages == 0) {
        stringResource(R.string.scan_preparing)
    } else {
        stringResource(R.string.scan_progress_page, currentPage, totalPages)
    }

@StringRes
fun ScanFailure.messageRes(): Int = when (this) {
    ScanFailure.UNREADABLE_FILE -> R.string.scan_error_unreadable_file
    ScanFailure.NO_TEXT_FOUND -> R.string.scan_error_no_text
}
