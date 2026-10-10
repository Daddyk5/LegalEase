package com.hcdc.legalease.feature.scan

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import kotlinx.coroutines.launch


@Composable
fun ImageScreen(
    onOpenResult: (analysisId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val images = uiState.imageUris.map(Uri::parse)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val okLabel = stringResource(R.string.action_ok)
    val documentName = stringResource(R.string.scan_default_images_name)
    val startScan = rememberWithNotificationPermission { viewModel.scanImages(documentName) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        viewModel.onImagesSelected(uris.map(Uri::toString))
    }

    ScanEvents(viewModel, snackbarHostState, onOpenResult)

    val progress = uiState.progress
    if (progress != null) {
        ScanningContent(progress, viewModel, onBack)
    } else{
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
                    text = "(${images.size}) Images selected"
                )

                VerticalSpacer(20.dp)
                // Image Viewer Placeholder
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
                    if (images.isNotEmpty()) {
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(images) { uri ->
                                AsyncImage(
                                    model = uri,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    } else {
                        Text("No images selected.")
                    }
                }
                VerticalSpacer(20.dp)
                SecondaryButton(
                    text = "Choose Image/s",
                    onClick = {
                        imagePickerLauncher.launch("image/*")
                    }
                )
                VerticalSpacer(10.dp)
                PrimaryButton(
                    text = "Proceed to Scan",
                    onClick = {
                        if (images.isNotEmpty()){
                            startScan()
                        }else{
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Please select images",
                                    actionLabel = okLabel
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}
