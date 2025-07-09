package com.hcdc.legalease.ui.components.bottomSheet.MainBottomSheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hcdc.legalease.ui.components.spacers.VerticalSpacer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheet(
    viewModel: BottomSheetViewModel,
    loginContent: @Composable () -> Unit,
    registerContent: @Composable () -> Unit,
    forgotPasswordContent: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isVisible by viewModel.isSheetVisible.collectAsState()
    val content by viewModel.sheetContent.collectAsState()
    val scope = rememberCoroutineScope()

    if (isVisible && content != null) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hide() },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (content) {
                    is BottomSheetContent.Login -> loginContent()
                    is BottomSheetContent.Register -> registerContent()
                    is BottomSheetContent.ForgotPassword -> forgotPasswordContent()
                    else -> {}
                }
                VerticalSpacer(50.dp)
            }

        }
    }
}
