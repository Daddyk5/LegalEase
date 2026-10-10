package com.hcdc.legalease.feature.scan

import com.hcdc.legalease.core.designsystem.component.SecureContent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.rajat.pdfviewer.PdfRendererView
import com.rajat.pdfviewer.compose.PdfRendererViewCompose
import com.rajat.pdfviewer.util.PdfSource
import kotlinx.coroutines.launch

@Composable
fun PdfScreen(
    onOpenResult: (analysisId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    // Contract content: no screenshots, recordings, or recents thumbnail.
    SecureContent()

    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pdfUri = uiState.pdfUri?.let(Uri::parse)
    val fileName = uiState.fileName
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val okLabel = stringResource(R.string.action_ok)
    val fallbackName = stringResource(R.string.scan_default_pdf_name)
    val startScan = rememberWithNotificationPermission { viewModel.scanPdf(fallbackName) }

    ScanEvents(viewModel, snackbarHostState, onOpenResult)

    val pdfPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.onPdfSelected(it.toString(), getFileNameFromUri(context, it))
        }
    }

    val progress = uiState.progress
    if (progress != null) {
        ScanningContent(progress, viewModel, onBack)
    } else {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = fileName ?: "-",
            style = MaterialTheme.typography.bodyLarge.copy(
            )
        )

        VerticalSpacer(20.dp)
        // PDF Viewer Placeholder
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {

            pdfUri?.let { uri ->
                val source = PdfSource.LocalUri(uri)
                key(uri) {
                    PdfRendererViewCompose(
                        source = source,
                        lifecycleOwner = LocalLifecycleOwner.current,
                        modifier = Modifier.fillMaxSize(),
                        zoomListener = object : PdfRendererView.ZoomListener {
                            override fun onZoomChanged(isZoomedIn: Boolean, scale: Float) = Unit
                        }
                    )
                }
            } ?: Text("No PDF selected")

        }
        VerticalSpacer(20.dp)
        SecondaryButton(
            text = "Choose PDF",
            onClick = {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
            }
        )
        VerticalSpacer(10.dp)
        PrimaryButton(
            text = "Proceed to Scan",
            onClick = {
                if (fileName?.endsWith(".pdf", ignoreCase = true) == true && pdfUri != null) {
                    startScan()
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Please select a valid PDF file",
                            actionLabel = okLabel
                        )
                    }
                }
            }
        )
    }}
}}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex != -1 && cursor.moveToFirst()) {
            return cursor.getString(nameIndex)
        }
    }
    return null
}
