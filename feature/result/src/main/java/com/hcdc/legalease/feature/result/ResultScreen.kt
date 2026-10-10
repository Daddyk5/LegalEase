package com.hcdc.legalease.feature.result

import com.hcdc.legalease.core.designsystem.component.SecureContent
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
import com.hcdc.legalease.core.designsystem.component.cards.RiskCard
import com.hcdc.legalease.core.designsystem.theme.RiskColorHigh
import com.hcdc.legalease.core.designsystem.theme.RiskColorLow
import com.hcdc.legalease.core.designsystem.theme.RiskColorMedium
import com.hcdc.legalease.core.domain.model.AnalysisMethod
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.ConfidenceBand
import com.hcdc.legalease.core.domain.model.RiskFlag
import com.hcdc.legalease.core.domain.model.RiskLevel
import com.hcdc.legalease.core.domain.model.UnclearReason
import androidx.compose.foundation.lazy.LazyListScope
import com.hcdc.legalease.core.designsystem.component.CustomLoading
import com.hcdc.legalease.core.designsystem.component.cards.SummaryCard
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer

@Composable
fun ResultScreen(
    onOpenPage: (analysisId: String, page: Int) -> Unit,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    // Contract content: no screenshots, recordings, or recents thumbnail.
    SecureContent()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is ResultUiState.Success -> ResultContent(state.result, onOpenPage)
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
private fun ResultContent(result: ResultModel, onOpenPage: (String, Int) -> Unit) {
    val report = result.report
    val shown = report.shownClassification
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
            text = result.title,
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
                if (shown != null) {
                    ClassificationCard(shown, report.confidence.band, result.thresholdText)
                    SummaryCard(modifier = Modifier, text = report.summary)
                } else {
                    SummaryCard(
                        modifier = Modifier,
                        title = stringResource(R.string.result_inconclusive_title),
                        text = inconclusiveText(result),
                    )
                }
            }
            reasonsCard(result, onOpenPage)
            if (report.generalCitations.isNotEmpty()) {
                item {
                    RiskCard(
                        riskLevel = stringResource(R.string.result_legal_references),
                        riskColor = MaterialTheme.colorScheme.primary,
                        points = report.generalCitations.map { "${it.article}: ${it.description}" },
                    )
                }
            }
            riskFlagCards(report.riskFlags)
            if (report.missingElements.isNotEmpty()) {
                item {
                    RiskCard(
                        riskLevel = stringResource(R.string.result_missing_elements),
                        riskColor = RiskColorMedium,
                        points = report.missingElements,
                    )
                }
            }
            if (report.recommendations.isNotEmpty()) {
                item {
                    RiskCard(
                        riskLevel = stringResource(R.string.result_recommendations),
                        riskColor = MaterialTheme.colorScheme.primary,
                        points = report.recommendations,
                    )
                }
            }
            item {
                Text(
                    text = stringResource(R.string.result_disclaimer),
                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}

/** "Why this classification": each reason with its verified quote, page, and attached articles. */
private fun LazyListScope.reasonsCard(result: ResultModel, onOpenPage: (String, Int) -> Unit) {
    val reasons = result.report.reasons
    if (reasons.isEmpty()) return
    item {
        val pageLabel = stringResource(R.string.result_page)
        val openHint = stringResource(R.string.result_open_page_hint)
        RiskCard(
            riskLevel = stringResource(
                if (result.report.shownClassification != null) R.string.result_why else R.string.result_what_we_found
            ),
            riskColor = MaterialTheme.colorScheme.primary,
            points = reasons.map { reason ->
                buildString {
                    append(reason.finding)
                    reason.clauseQuote?.let { append("\n“").append(it).append("”") }
                    reason.page?.let { append("\n").append(pageLabel.format(it)).append(" · ").append(openHint) }
                    if (reason.citations.isNotEmpty()) {
                        append("\n").append(reason.citations.joinToString(" · ") { it.article })
                    }
                }
            },
            onPointClick = { index -> reasons[index].page?.let { onOpenPage(result.analysisId, it) } },
            isPointClickable = { index -> result.pagesViewable && reasons[index].page != null },
        )
    }
}

private fun LazyListScope.riskFlagCards(flags: List<RiskFlag>) {
    listOf(RiskLevel.HIGH, RiskLevel.MEDIUM, RiskLevel.LOW).forEach { level ->
        val points = flags.filter { it.level == level }.map { it.description }
        if (points.isEmpty()) return@forEach
        item {
            RiskCard(
                riskLevel = stringResource(
                    when (level) {
                        RiskLevel.HIGH -> R.string.result_risk_high
                        RiskLevel.MEDIUM -> R.string.result_risk_medium
                        RiskLevel.LOW -> R.string.result_risk_low
                    }
                ),
                riskColor = when (level) {
                    RiskLevel.HIGH -> RiskColorHigh
                    RiskLevel.MEDIUM -> RiskColorMedium
                    RiskLevel.LOW -> RiskColorLow
                },
                points = points,
            )
        }
    }
}

private val RESCAN_REASONS =
    setOf(UnclearReason.UNREADABLE_PAGES, UnclearReason.POOR_SCAN_QUALITY, UnclearReason.SKIPPED_PAGES)

@Composable
private fun unclearText(reason: UnclearReason, result: ResultModel): String {
    val report = result.report
    val pages = { list: List<Int> -> list.joinToString(", ") }
    return when (reason) {
        UnclearReason.UNREADABLE_PAGES ->
            stringResource(R.string.result_unclear_unreadable, pages(result.unreadablePages))
        UnclearReason.POOR_SCAN_QUALITY ->
            stringResource(R.string.result_unclear_poor_quality, pages(report.poorQualityPages))
        UnclearReason.SKIPPED_PAGES -> stringResource(R.string.result_unclear_skipped, pages(report.skippedPages))
        UnclearReason.MISSING_ELEMENTS -> stringResource(R.string.result_unclear_missing)
        UnclearReason.CONFLICTING_RESULTS -> stringResource(R.string.result_unclear_conflicting)
        UnclearReason.UNVERIFIED_QUOTES -> stringResource(R.string.result_unclear_unverified)
        UnclearReason.MODEL_DISAGREEMENT -> stringResource(R.string.result_unclear_disagreement)
        UnclearReason.LIMITED_ANALYSIS -> stringResource(
            if (report.method == AnalysisMethod.LEGACY) {
                R.string.result_unclear_legacy
            } else {
                R.string.result_unclear_limited
            }
        )
    }
}

/** Lists exactly what was unclear, then the next steps. */
@Composable
private fun inconclusiveText(result: ResultModel): String {
    val report = result.report
    val unclear = report.unclear.map { unclearText(it, result) }
        .ifEmpty { listOf(stringResource(R.string.result_unclear_low_confidence)) }
    val rescan = report.unclear.any { it in RESCAN_REASONS }
    val steps = buildList {
        if (rescan) add(stringResource(R.string.result_step_rescan))
        add(stringResource(R.string.result_step_clearer))
        add(stringResource(R.string.result_step_lawyer))
    }
    return buildString {
        if (report.summary.isNotBlank()) append(report.summary).append("\n\n")
        append(stringResource(R.string.result_unclear_heading)).append('\n')
        unclear.forEach { append("• ").append(it).append('\n') }
        append('\n').append(stringResource(R.string.result_next_steps_heading)).append('\n')
        append(steps.joinToString("\n") { "• $it" })
    }
}

@Composable
private fun ClassificationCard(classification: Classification, band: ConfidenceBand, thresholdText: String) {
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
                text = "${stringResource(R.string.label_confidence)} ${stringResource(
                    if (band == ConfidenceBand.HIGH) R.string.result_band_high else R.string.result_band_confident
                )}",
                style = MaterialTheme.typography.bodyMedium
            )
            if (thresholdText.isNotBlank()) {
                Text(text = thresholdText, style = MaterialTheme.typography.bodySmall)
            }
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
