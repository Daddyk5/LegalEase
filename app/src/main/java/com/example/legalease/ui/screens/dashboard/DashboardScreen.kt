package com.example.legalease.ui.screens.dashboard

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
import androidx.navigation.NavController
import com.example.legalease.R
import com.example.legalease.ui.components.cards.DashboardCard
import com.example.legalease.ui.components.spacers.VerticalSpacer

@Composable
fun DashboardScreen(navController: NavController){

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

            IconButton(onClick = { navController.navigate("home")}) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Logout,
                    contentDescription = "Logout",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
        VerticalSpacer(height = 15.dp)
        Text(
            text = "Ana Beech",
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
                onClick = { navController.navigate("pdf") },
                modifier = Modifier.weight(.5f)
            )
            VerticalSpacer(height = 0.dp)
            DashboardCard(
                title = "Upload Image",
                subtitle = "Snap, upload, and understand",
                imageRes = R.drawable.upload_image,
                onClick = { navController.navigate("image") },
                modifier = Modifier.weight(.5f)
            )
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
                onClick = { navController.navigate("history") },
                modifier = Modifier.weight(.5f)
            )
        }

    }

}