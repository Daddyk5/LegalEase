package com.hcdc.legalease.feature.generator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.generator.DiffSegment
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.ConfidenceBand
import com.hcdc.legalease.core.domain.usecase.ProposedEdit

@Composable
internal fun AnalysisSection(state: EditorUiState, onOpenAnalysis: (String) -> Unit, onAnalyzeAgain: () -> Unit) {
    val analysis = state.analysis
    Section(R.string.generator_analysis_title) {
        when (analysis?.status) {
            null, AnalysisStatus.QUEUED, AnalysisStatus.EXTRACTING_TEXT ->
                Progress(stringResource(R.string.generator_analysis_waiting))
            AnalysisStatus.CLASSIFYING, AnalysisStatus.CHECKING ->
                Progress(stringResource(R.string.generator_analysis_checking))
            AnalysisStatus.ANALYZING_PAGES -> Progress(
                stringResource(R.string.generator_analysis_page, analysis.currentPage ?: 0, analysis.pageCount ?: 0)
            )
            AnalysisStatus.COMPLETED -> {
                Text(verdict(analysis), style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(
                    text = stringResource(R.string.generator_view_analysis),
                    onClick = { onOpenAnalysis(analysis.id) },
                )
            }
            AnalysisStatus.FAILED, AnalysisStatus.CANCELLED -> {
                Text(stringResource(R.string.generator_analysis_failed), style = MaterialTheme.typography.bodyLarge)
                SecondaryButton(
                    text = stringResource(R.string.generator_analyze_again),
                    onClick = onAnalyzeAgain,
                    enabled = !state.busy,
                )
            }
        }
    }
}

/** The verdict appears only when the analysis is confident enough; otherwise it says so plainly. */
@Composable
private fun verdict(analysis: Analysis): String {
    val report = analysis.report
    val shown = report?.shownClassification ?: return stringResource(R.string.generator_analysis_inconclusive)
    val band = if (report.confidence.band == ConfidenceBand.HIGH) {
        R.string.generator_analysis_verdict_high
    } else {
        R.string.generator_analysis_verdict_confident
    }
    return stringResource(band, shown.label)
}

@Composable
internal fun ExportSection(state: EditorUiState, onPaper: (PaperSize) -> Unit, onExport: () -> Unit) {
    Section(R.string.generator_export_title) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaperSize.entries.forEach { size ->
                FilterChip(
                    selected = state.paper == size,
                    onClick = { onPaper(size) },
                    label = { Text(stringResource(size.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
        }
        PrimaryButton(
            text = stringResource(R.string.generator_export),
            onClick = onExport,
            enabled = state.canExport && !state.busy,
        )
        if (!state.canExport) {
            Text(stringResource(R.string.generator_export_after_analysis), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Section(title: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

@Composable
private fun Progress(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
}

/** An assistant edit shown as a word diff. Nothing changes unless the user accepts. */
@Composable
internal fun ProposalDialog(proposal: ProposedEdit, onAccept: () -> Unit, onDiscard: () -> Unit) {
    // Underline and strike-through carry the meaning; color only reinforces it.
    val added = SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    )
    val removed = SpanStyle(color = MaterialTheme.colorScheme.error, textDecoration = TextDecoration.LineThrough)
    val title = if (proposal.before == null) R.string.generator_diff_title_new else R.string.generator_diff_title_reword
    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text(stringResource(title, proposal.clause.title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(diffText(proposal.diff, added, removed), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.generator_diff_legend), style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.generator_accept)) } },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.generator_discard)) } },
    )
}

private fun diffText(diff: List<DiffSegment>, added: SpanStyle, removed: SpanStyle): AnnotatedString =
    buildAnnotatedString {
        diff.forEachIndexed { index, segment ->
            if (index > 0) append(' ')
            when (segment.type) {
                DiffSegment.Type.SAME -> append(segment.text)
                DiffSegment.Type.ADDED -> withStyle(added) { append(segment.text) }
                DiffSegment.Type.REMOVED -> withStyle(removed) { append(segment.text) }
            }
        }
    }
