package com.hcdc.legalease.feature.auth.sheet.forgotpassword

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.designsystem.component.textfield.PrimaryTextField
import com.hcdc.legalease.core.domain.model.CredentialValidator
import com.hcdc.legalease.feature.auth.R
import com.hcdc.legalease.feature.auth.authMessageRes
import com.hcdc.legalease.feature.auth.messageRes
import com.hcdc.legalease.feature.auth.sheet.BottomSheetContent
import com.hcdc.legalease.feature.auth.sheet.BottomSheetViewModel

@Composable
fun ForgotPasswordSheetContent() {
    val bottomSheetViewModel: BottomSheetViewModel = viewModel()

    val forgotPasswordViewModel: ForgotPasswordViewModel = hiltViewModel()

    val state by forgotPasswordViewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Forgot Password",
            style = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        VerticalSpacer(height = 10.dp)
        PrimaryTextField(
            value = state.email,
            onValueChange = forgotPasswordViewModel::onEmailChanged,
            label = "Email",
            validator = { CredentialValidator.email(it) == null },
            errorMessage = stringResource(R.string.auth_error_email_invalid),
            error = state.emailProblem?.let { stringResource(it.messageRes()) },
            keyboardType = KeyboardType.Email,
        )
        val message = state.error?.let { stringResource(it.authMessageRes()) }
        val notice = state.resetRequestedFor?.let { stringResource(R.string.auth_notice_reset_sent, it) }
        if (message != null || notice != null) {
            VerticalSpacer(height = 10.dp)
            Text(
                text = message ?: notice.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (message != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        VerticalSpacer(height = 30.dp)
        PrimaryButton(
            text = "Submit",
            enabled = !state.isSubmitting,
            onClick = forgotPasswordViewModel::submit
        )
        VerticalSpacer(height = 30.dp)
        Text(
            text = "────── or ──────",
            style = MaterialTheme.typography.bodySmall
        )
        VerticalSpacer(height = 25.dp)
        Text(
            text = "Login Instead",
            modifier = Modifier.clickable {
                bottomSheetViewModel.show(BottomSheetContent.Login)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
