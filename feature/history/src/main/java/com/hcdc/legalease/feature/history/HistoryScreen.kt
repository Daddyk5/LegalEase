package com.hcdc.legalease.feature.history

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcdc.legalease.core.designsystem.component.cards.HistoryCard
import com.hcdc.legalease.core.designsystem.component.searchbar.ScanSearchBar
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer

@Composable
fun HistoryScreen(
    onOpenAnalysis: (analysisId: String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Row(
        modifier = Modifier.fillMaxWidth().padding(0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
            Column() {
                Text(
                    text = "My Scans",
                    style = MaterialTheme.typography.displayLarge.copy(
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                VerticalSpacer(5.dp)
                Text(
                    text = "Your contract history in one place.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            Image(
                painter = painterResource(R.drawable.together),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(100.dp)
            )
        }

        HorizontalDivider(
            color = Color.Gray.copy(alpha = .3f),
            thickness = 1.dp,
        )
        VerticalSpacer(height = 15.dp)
        ScanSearchBar(
            query = uiState.query,
            onQueryChange = viewModel::onQueryChange
        )
        VerticalSpacer(height = 15.dp)

        if (!uiState.isLoading && uiState.items.isEmpty()) {
            Text(
                text = if (uiState.query.isBlank()) {
                    stringResource(R.string.history_empty)
                } else {
                    stringResource(R.string.history_no_matches, uiState.query)
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                textAlign = TextAlign.Center
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            items(uiState.items, key = { it.analysisId }) { item ->
                HistoryCard(
                    title = item.title,
                    onClick = { onOpenAnalysis(item.analysisId) }
                )
            }
        }
    }
}
