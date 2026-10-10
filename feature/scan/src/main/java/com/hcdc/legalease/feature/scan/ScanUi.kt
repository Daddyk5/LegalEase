package com.hcdc.legalease.feature.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import com.hcdc.legalease.core.designsystem.component.CustomLoading
import com.hcdc.legalease.core.domain.result.AppError

@Composable
internal fun ScanProgress.message(): String = when (this) {
    ScanProgress.Preparing -> stringResource(R.string.scan_preparing)
    is ScanProgress.ReadingPage -> if (pageCount == 0) {
        stringResource(R.string.scan_preparing)
    } else {
        stringResource(R.string.scan_progress_page, page, pageCount)
    }
    ScanProgress.Classifying -> stringResource(R.string.scan_classifying)
    is ScanProgress.AnalyzingPage -> stringResource(R.string.scan_analyzing_page, page, pageCount)
    ScanProgress.Checking -> stringResource(R.string.scan_checking)
}

@StringRes
internal fun AppError.scanMessageRes(): Int = when (this) {
    AppError.UnreadableFile -> R.string.scan_error_unreadable_file
    AppError.NoTextFound -> R.string.scan_error_no_text
    AppError.StorageFull -> R.string.scan_error_storage_full
    else -> R.string.scan_error_generic
}

/** Full-screen progress for a running scan. Back cancels the scan and leaves the screen, as before. */
@Composable
internal fun ScanningContent(progress: ScanProgress, viewModel: ScanViewModel, onBack: () -> Unit) {
    BackHandler {
        viewModel.cancelScan()
        onBack()
    }
    CustomLoading(message = progress.message())
}

/** Collects one-off events: navigation on success, a snackbar for upload problems. */
@Composable
internal fun ScanEvents(
    viewModel: ScanViewModel,
    snackbarHostState: SnackbarHostState,
    onOpenResult: (String) -> Unit,
) {
    val resources = LocalResources.current
    val okLabel = stringResource(R.string.action_ok)
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ScanEvent.NavigateToResult -> onOpenResult(event.analysisId)
                is ScanEvent.ShowError -> snackbarHostState.showSnackbar(
                    message = resources.getString(event.error.scanMessageRes()),
                    actionLabel = okLabel,
                )
            }
        }
    }
}

/**
 * Returns a function that runs [action] after asking for notification permission (Android 13+),
 * so the background analysis can show its progress notification. The scan runs either way.
 */
@Composable
internal fun rememberWithNotificationPermission(action: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { action() }
    return remember(context, launcher, action) {
        {
            val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            if (needsPermission) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) else action()
        }
    }
}
