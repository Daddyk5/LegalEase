package com.hcdc.legalease.feature.auth

import com.hcdc.legalease.feature.auth.sheet.BottomSheet
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hcdc.legalease.core.designsystem.R as DesignR
import com.hcdc.legalease.feature.auth.sheet.BottomSheetContent
import com.hcdc.legalease.feature.auth.sheet.forgotpassword.ForgotPasswordSheetContent
import com.hcdc.legalease.feature.auth.sheet.login.LoginSheetContent
import com.hcdc.legalease.feature.auth.sheet.register.RegisterSheetContent
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.feature.auth.sheet.BottomSheetViewModel

@Composable
fun IntroScreen(
    onLoginSuccess: () -> Unit
) {
    val sheetViewModel: BottomSheetViewModel = viewModel()

    BottomSheet(
        viewModel = sheetViewModel,
        loginContent = {
            LoginSheetContent(
                onNavigateToDashboard = onLoginSuccess,
                onLoginSuccess = { sheetViewModel.hide() }
            )
        },
        registerContent = { RegisterSheetContent { sheetViewModel.hide() } },
        forgotPasswordContent = { ForgotPasswordSheetContent { sheetViewModel.hide() } }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Image(
            painter = painterResource(id = DesignR.drawable.lawyer),
            contentDescription = "Intro Screen Image",
            modifier = Modifier.size(350.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = "LegalEase",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 50.sp
                )
            )
            VerticalSpacer(height = 8.dp)
            Text(
                text = "Understand your contract before you sign.",
                style = MaterialTheme.typography.titleSmall.copy(
                    color = MaterialTheme.colorScheme.onBackground

                )
            )
            VerticalSpacer(height = 35.dp)
            PrimaryButton(
                text = "Begin",
                onClick = {sheetViewModel.show(BottomSheetContent.Login)}
                )
        }
    }
}

