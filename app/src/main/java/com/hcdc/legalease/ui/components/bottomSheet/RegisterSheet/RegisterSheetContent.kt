package com.hcdc.legalease.ui.components.bottomSheet.RegisterSheet

import com.hcdc.legalease.ui.components.textfield.PrimaryTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hcdc.legalease.ui.components.bottomSheet.MainBottomSheets.BottomSheetContent
import com.hcdc.legalease.ui.components.buttons.PrimaryButton
import com.hcdc.legalease.ui.components.spacers.VerticalSpacer
import com.hcdc.legalease.ui.components.bottomSheet.MainBottomSheets.BottomSheetViewModel

@Composable
fun RegisterSheetContent(onSubmit: () -> Unit) {

    val bottomSheetViewModel: BottomSheetViewModel = viewModel()

    val registerSheetViewModel: RegisterSheetViewModel = viewModel()

    val email by registerSheetViewModel.email.collectAsState()
    val password by registerSheetViewModel.password.collectAsState()
    val repeatPassword by registerSheetViewModel.repeatPassword.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Register",
            style = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        VerticalSpacer(height = 10.dp)
        PrimaryTextField(
            value = email,
            onValueChange = {registerSheetViewModel.onEmailChanged(it)},
            label = "Email",
            validator = { android.util.Patterns.EMAIL_ADDRESS.matcher(it).matches() },
            errorMessage = "Invalid email format"
        )
        VerticalSpacer(height = 5.dp)
        PrimaryTextField(
            value = password,
            onValueChange = {registerSheetViewModel.onPasswordChanged(it)},
            label = "Password",
            isPassword = true,
            validator = { it.length >= 8 },
            errorMessage = "Password must be at least 8 characters"
        )
        VerticalSpacer(height = 5.dp)
        PrimaryTextField(
            value = repeatPassword,
            onValueChange = {registerSheetViewModel.onRepeatPasswordChanged(it)},
            label = "Repeat Password",
            isPassword = true,
            validator = { it.length >= 8 },
            errorMessage = "Password must be at least 8 characters"
        )
        VerticalSpacer(height = 30.dp)
        PrimaryButton(
            text = "Register",
            onClick = onSubmit
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

