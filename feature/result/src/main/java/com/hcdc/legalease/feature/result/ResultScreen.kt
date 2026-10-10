package com.hcdc.legalease.feature.result

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.designsystem.component.CustomLoading
import com.hcdc.legalease.core.designsystem.component.cards.SummaryCard
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import kotlin.math.roundToInt

@Composable
fun ResultScreen(viewModel: ResultViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is ResultUiState.Success -> ResultContent(state.result)
            is ResultUiState.Error -> Text(
                text = stringResource(state.error.messageRes()),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            ResultUiState.Loading -> CustomLoading(message = stringResource(R.string.result_analyzing))
        }
    }
}

private fun AppError.messageRes(): Int = when (this) {
    AppError.DocumentNotFound -> R.string.result_error_not_found
    AppError.ModelUnavailable -> R.string.result_error_model_unavailable
    AppError.ModelIncompatible -> R.string.result_error_model_incompatible
    AppError.UnreadableFile -> R.string.result_error_unreadable_file
    AppError.NoTextFound -> R.string.result_error_no_text
    AppError.Cancelled -> R.string.result_error_cancelled
    else -> R.string.result_error_generic
}

@Composable
private fun ResultContent(clauseData: ResultModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Header()

        VerticalSpacer(10.dp)
        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)
        VerticalSpacer(15.dp)

        Text(
            text = clauseData.contractName,
            style = MaterialTheme.typography.titleMedium.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )

        VerticalSpacer(15.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            item {
                SummaryCard(
                    modifier = Modifier,
                    text = clauseData.summary
                )

                ClassificationCard(
                    classification = clauseData.classification,
                    confidence = clauseData.confidence
                )
            }
        }
    }
}

@Composable
private fun ClassificationCard(classification: Classification, confidence: Float) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = classificationColor(classification))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "${stringResource(R.string.label_classification)} ${classification.label}",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${stringResource(R.string.label_confidence)} ${(confidence * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/** Must be @Composable so we can read MaterialTheme.colorScheme safely. */
@Composable
private fun classificationColor(c: Classification): Color {
    val colors = MaterialTheme.colorScheme
    return when (c) {
        Classification.VOID -> colors.errorContainer
        Classification.VOIDABLE -> Color(0xFFFFE08A)
        Classification.UNENFORCEABLE -> colors.surfaceVariant
        Classification.RESCISSIBLE -> Color(0xFFE0BBFF)
        Classification.ENFORCEABLE -> colors.tertiaryContainer
    }
}

@Composable
private fun Header() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(0.9f)) {
            Text(
                text = "Scan Complete",
                style = MaterialTheme.typography.displayLarge.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            )
            VerticalSpacer(5.dp)
            Text(
                text = "Insights from your past scan",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Image(
            painter = painterResource(R.drawable.complete),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(100.dp)
                .weight(0.3f)
        )
    }
}
