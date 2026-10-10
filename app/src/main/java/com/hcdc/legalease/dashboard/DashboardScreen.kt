package com.hcdc.legalease.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcdc.legalease.R
import com.hcdc.legalease.core.designsystem.R as DesignR
import com.hcdc.legalease.core.designsystem.component.cards.DashboardCard
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer

@Composable
fun DashboardScreen(
    onLoggedOut: () -> Unit,
    onUploadPdf: () -> Unit,
    onUploadImage: () -> Unit,
    onOpenHistory: () -> Unit,
    onCreateContract: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val deviceCompromised by viewModel.deviceCompromised.collectAsStateWithLifecycle()
    val greetingName by viewModel.greetingName.collectAsStateWithLifecycle()
    val generatorEnabled by viewModel.generatorEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.signedOut.collect { onLoggedOut() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),

            horizontalArrangement = Arrangement.SpaceBetween,

        ) {
            Image(
                painter = painterResource(id = R.drawable.avatar),
                contentDescription = "Intro Screen Image",
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
            )

            Row {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Privacy and data settings",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
                IconButton(onClick = viewModel::logout) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
        VerticalSpacer(height = 15.dp)
        Text(
            text = greetingName.orEmpty(),
            style = MaterialTheme.typography.displayLarge.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        VerticalSpacer(height = 5.dp)
        Text(
            text = "Ready to explore your documents?",
            style = MaterialTheme.typography.bodyLarge.copy(
            )
        )
        if (deviceCompromised) {
            VerticalSpacer(height = 8.dp)
            Text(
                text = "This phone did not pass the Google Play integrity check. Offline analysis is turned " +
                    "off; results use basic checks only.",
                style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.error)
            )
        }
        VerticalSpacer(height = 30.dp)
        HorizontalDivider(
            color = Color.Gray.copy(alpha = .3f),
            thickness = 1.dp,
        )
        VerticalSpacer(height = 33.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            DashboardCard(
                title = "Upload PDF",
                subtitle = "Scan and analyze legal documents",
                imageRes = R.drawable.upload_pdf,
                onClick = { onUploadPdf() },
                modifier = Modifier.weight(.5f)
            )
            VerticalSpacer(height = 0.dp)
            DashboardCard(
                title = "Upload Image",
                subtitle = "Snap, upload, and understand",
                imageRes = R.drawable.upload_image,
                onClick = { onUploadImage() },
                modifier = Modifier.weight(.5f)
            )
            if (generatorEnabled) {
                VerticalSpacer(height = 0.dp)
                DashboardCard(
                    title = "Create Contract",
                    subtitle = "Lease, loan, or sale, from Philippine templates",
                    imageRes = DesignR.drawable.lawyer,
                    onClick = { onCreateContract() },
                    modifier = Modifier.weight(.5f)
                )
            }
            VerticalSpacer(height = 5.dp)
            HorizontalDivider(
                color = Color.Gray.copy(alpha = .3f),
                thickness = 1.dp,
            )
            VerticalSpacer(height = 5.dp)
            DashboardCard(
                title = "My Scans",
                subtitle = "History of your scanned files and reviews.",
                imageRes = R.drawable.history,
                onClick = { onOpenHistory() },
                modifier = Modifier.weight(.5f)
            )
        }

    }

}
