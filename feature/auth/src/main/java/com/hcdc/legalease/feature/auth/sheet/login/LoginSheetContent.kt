package com.hcdc.legalease.feature.auth.sheet.login

import com.hcdc.legalease.core.designsystem.component.textfield.PrimaryTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hcdc.legalease.feature.auth.R
import com.hcdc.legalease.feature.auth.sheet.BottomSheetContent
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.feature.auth.sheet.BottomSheetViewModel

@Composable
fun LoginSheetContent(
    onNavigateToDashboard: () -> Unit,
    onLoginSuccess: () -> Unit
) {

    val loginSheetViewModel: LoginSheetViewModel = viewModel()
    val bottomSheetViewModel: BottomSheetViewModel = viewModel()

    val email by loginSheetViewModel.email.collectAsState()
    val password by loginSheetViewModel.password.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Login",
            style = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        VerticalSpacer(height = 10.dp)
        PrimaryTextField(
            value = email,
            onValueChange = {loginSheetViewModel.onEmailChanged(it)},
            label = "Email",
            validator = { android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() },
            errorMessage = "Invalid email format"
        )
        VerticalSpacer(height = 5.dp)
        PrimaryTextField(
            value = password,
            onValueChange = {loginSheetViewModel.onPasswordChanged(it)},
            label = "Password",
            isPassword = true,
            validator = { it.length >= 8 },
            errorMessage = "Password must be at least 8 characters"
        )
        VerticalSpacer(height = 10.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "Forgot password?",
                modifier = Modifier.clickable {
                    bottomSheetViewModel.show(BottomSheetContent.ForgotPassword)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )

        }
        VerticalSpacer(height = 30.dp)
        PrimaryButton(
            text = "Login",
            onClick = {
                onLoginSuccess()
                onNavigateToDashboard()
            }
        )
        VerticalSpacer(height = 30.dp)
        Text(
            text = "────── or ──────",
            style = MaterialTheme.typography.bodySmall
        )
        VerticalSpacer(height = 30.dp)
        Image(
            painter = painterResource(id = R.drawable.google),
            contentDescription = "Google Icon",
            modifier = Modifier.size(25.dp)
        )
        VerticalSpacer(height = 30.dp)
        Text(
            text = "Create an Account",
            modifier = Modifier.clickable {
                bottomSheetViewModel.show(BottomSheetContent.Register)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary
        )

    }
}

