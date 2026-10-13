package com.hcdc.legalease.feature.auth.sheet.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
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
fun LoginSheetContent(
    onNavigateToDashboard: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val loginSheetViewModel: LoginSheetViewModel = hiltViewModel()
    val bottomSheetViewModel: BottomSheetViewModel = viewModel()

    val state by loginSheetViewModel.uiState.collectAsStateWithLifecycle()
    val startGoogleSignIn = rememberGoogleSignIn(
        webClientId = loginSheetViewModel.googleWebClientId,
        onIdToken = loginSheetViewModel::onGoogleIdToken,
        onUnavailable = loginSheetViewModel::onGoogleUnavailable,
    )

    LaunchedEffect(loginSheetViewModel) {
        loginSheetViewModel.loggedIn.collect {
            onLoginSuccess()
            onNavigateToDashboard()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.auth_login),
            style = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        VerticalSpacer(height = 10.dp)
        PrimaryTextField(
            value = state.email,
            onValueChange = loginSheetViewModel::onEmailChanged,
            label = stringResource(R.string.auth_email),
            validator = { CredentialValidator.email(it) == null },
            errorMessage = stringResource(R.string.auth_error_email_invalid),
            error = state.emailProblem?.let { stringResource(it.messageRes()) },
            keyboardType = KeyboardType.Email,
        )
        VerticalSpacer(height = 5.dp)
        PrimaryTextField(
            value = state.password,
            onValueChange = loginSheetViewModel::onPasswordChanged,
            label = stringResource(R.string.auth_password),
            isPassword = true,
            validator = { CredentialValidator.password(it) == null },
            errorMessage = stringResource(R.string.auth_error_password_short),
            error = state.passwordProblem?.let { stringResource(it.messageRes()) },
        )
        VerticalSpacer(height = 10.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = stringResource(R.string.auth_forgot_link),
                modifier = Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) {
                    bottomSheetViewModel.show(BottomSheetContent.ForgotPassword)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        LoginMessages(state, onResend = loginSheetViewModel::resendVerificationEmail)
        VerticalSpacer(height = 30.dp)
        PrimaryButton(
            text = stringResource(R.string.auth_login),
            enabled = !state.isSubmitting,
            onClick = loginSheetViewModel::submit
        )
        VerticalSpacer(height = 30.dp)
        Text(
            text = stringResource(R.string.auth_or),
            modifier = Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.bodySmall
        )
        VerticalSpacer(height = 30.dp)
        // 48dp touch target around the existing 25dp Google mark.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    enabled = !state.isSubmitting,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.auth_google_sign_in),
                    onClick = startGoogleSignIn,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.google),
                contentDescription = stringResource(R.string.auth_google_sign_in),
                modifier = Modifier.size(25.dp)
            )
        }
        VerticalSpacer(height = 18.dp)
        Text(
            text = stringResource(R.string.auth_create_account),
            modifier = Modifier.minimumInteractiveComponentSize().clickable(role = Role.Button) {
                bottomSheetViewModel.show(BottomSheetContent.Register)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/** Inline feedback under the form: the error, a resend link when unverified, or a confirmation. */
@Composable
private fun LoginMessages(state: LoginUiState, onResend: () -> Unit) {
    val message = when {
        state.error != null -> stringResource(state.error.authMessageRes())
        state.googleUnavailable -> stringResource(R.string.auth_error_google_unavailable)
        else -> null
    }
    if (message != null) {
        VerticalSpacer(height = 10.dp)
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
    if (state.canResendVerification) {
        VerticalSpacer(height = 6.dp)
        Text(
            text = stringResource(R.string.auth_action_resend_verification),
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(enabled = !state.isSubmitting, role = Role.Button, onClick = onResend),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
    if (state.verificationResent) {
        VerticalSpacer(height = 10.dp)
        Text(
            text = stringResource(R.string.auth_notice_verification_sent),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
