package com.hcdc.legalease.feature.generator

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hcdc.legalease.core.designsystem.component.CustomLoading
import com.hcdc.legalease.core.designsystem.component.SecureContent
import com.hcdc.legalease.core.designsystem.component.buttons.PrimaryButton
import com.hcdc.legalease.core.designsystem.component.buttons.SecondaryButton
import com.hcdc.legalease.core.designsystem.component.cards.RiskCard
import com.hcdc.legalease.core.designsystem.component.cards.SummaryCard
import com.hcdc.legalease.core.designsystem.component.spacers.VerticalSpacer
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.model.OptionalClause
import java.io.File

@Composable
fun ContractEditorScreen(
    onOpenAnalysis: (analysisId: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ContractEditorViewModel = hiltViewModel(),
) {
    // Contract content: no screenshots, recordings, or recents thumbnail.
    SecureContent()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val shareTitle = stringResource(R.string.generator_share)
    val goBack = { if (!viewModel.back()) onBack() }

    BackHandler(onBack = goBack)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is EditorEvent.Share -> share(context, event.file, shareTitle)
            }
        }
    }

    val message = when (val m = state.message) {
        null -> null
        is EditorMessage.SuggestionsWithheld -> pluralStringResource(m.textRes, m.count, m.count)
        else -> stringResource(m.textRes)
    }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            when {
                state.loading -> CustomLoading()
                state.stage == EditorStage.FORM -> ContractForm(
                    state = state,
                    onUpdate = viewModel::update,
                    onNext = viewModel::next,
                    onBack = goBack,
                )
                else -> ContractPreview(state, viewModel, onOpenAnalysis)
            }
        }
        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.proposal?.let {
        ProposalDialog(it, onAccept = viewModel::acceptProposal, onDiscard = viewModel::nextProposal)
    }
}

/** Shares an exported PDF through the app's FileProvider; the receiving app gets read access only. */
private fun share(context: Context, file: File, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(send, title))
    } catch (_: ActivityNotFoundException) {
        // No app can receive a PDF; nothing else to do.
    }
}

@Composable
private fun ContractPreview(
    state: EditorUiState,
    viewModel: ContractEditorViewModel,
    onOpenAnalysis: (String) -> Unit,
) {
    val rendered = state.rendered
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Text(
                text = stringResource(state.draft.type.templateName),
                style = MaterialTheme.typography.displayLarge.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.semantics { heading() },
            )
            VerticalSpacer(5.dp)
            Text(
                text = state.saved?.let { stringResource(R.string.generator_version, it.version) }
                    ?: stringResource(R.string.generator_unsaved),
                style = MaterialTheme.typography.bodyMedium,
            )
            VerticalSpacer(10.dp)
            HorizontalDivider(color = Color.Gray.copy(alpha = .3f), thickness = 1.dp)
        }
        if (state.readOnlyBody != null) {
            item { SummaryCard(title = state.saved?.title.orEmpty(), text = state.readOnlyBody) }
            item { Text(stringResource(R.string.generator_readonly), style = MaterialTheme.typography.bodyMedium) }
            return@LazyColumn
        }
        item {
            val notices = state.notices.map { stringResource(it.textRes) } +
                stringResource(R.string.generator_notice_acknowledgment)
            RiskCard(
                riskLevel = stringResource(R.string.generator_notices_title),
                riskColor = MaterialTheme.colorScheme.primary,
                points = notices,
            )
        }
        rendered?.let { item { SummaryCard(title = it.title, text = it.plainText.removePrefix(it.title).trim()) } }
        optionalClauses(state, viewModel)
        item { VerticalSpacer(10.dp) }
        if (state.hasUnsavedChanges) {
            item {
                PrimaryButton(
                    text = stringResource(R.string.generator_save_analyze),
                    onClick = viewModel::saveAndAnalyze,
                    enabled = !state.busy && rendered != null,
                )
                VerticalSpacer(8.dp)
                SecondaryButton(text = stringResource(R.string.generator_edit_form), onClick = { viewModel.back() })
            }
        } else {
            item { AnalysisSection(state, onOpenAnalysis, viewModel::analyzeAgain) }
            item { ExportSection(state, viewModel::setPaper, viewModel::export) }
            item {
                VerticalSpacer(8.dp)
                SecondaryButton(
                    text = stringResource(R.string.generator_edit_new_version),
                    onClick = viewModel::edit,
                    enabled = !state.busy,
                )
            }
        }
        item {
            VerticalSpacer(15.dp)
            Text(
                text = stringResource(R.string.generator_disclaimer),
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground),
            )
        }
    }
}

/** Included clauses first (removable, rewordable), then library clauses the user can add. */
private fun LazyListScope.optionalClauses(state: EditorUiState, viewModel: ContractEditorViewModel) {
    val included = state.draft.optionalClauses
    val library = state.library.filter { lib -> included.none { it.id == lib.id } }
    val canAssist = state.assistant == LlmAvailability.READY && !state.assistantBusy && !state.busy
    item {
        VerticalSpacer(10.dp)
        Text(
            text = stringResource(R.string.generator_optional_title),
            style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.primary),
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.generator_optional_hint), style = MaterialTheme.typography.bodySmall)
    }
    items(included, key = { it.id }) { clause ->
        ClauseItem(clause, checked = true, onToggle = { viewModel.toggleClause(clause) }) {
            if (state.assistant == LlmAvailability.READY) {
                Row(modifier = Modifier.padding(start = 28.dp)) {
                    TextButton(onClick = { viewModel.rewrite(clause) }, enabled = canAssist) {
                        Text(stringResource(R.string.generator_reword))
                    }
                    TextButton(onClick = { viewModel.toggleClause(clause) }, enabled = !state.busy) {
                        Text(stringResource(R.string.generator_remove))
                    }
                }
            }
        }
    }
    items(library, key = { it.id }) { clause ->
        ClauseItem(clause, checked = false, onToggle = { viewModel.toggleClause(clause) })
    }
    item { AssistantRow(state, onSuggest = viewModel::suggest) }
}

@Composable
private fun ClauseItem(
    clause: OptionalClause,
    checked: Boolean,
    onToggle: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        CheckRow(checked = checked, label = clause.title, error = null, onToggle = { onToggle() })
        Text(clause.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 40.dp))
        if (clause.source == OptionalClause.Source.ASSISTANT) {
            Text(
                stringResource(R.string.generator_optional_from_assistant),
                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary),
                modifier = Modifier.padding(start = 40.dp),
            )
        }
        actions()
    }
}

@Composable
private fun AssistantRow(state: EditorUiState, onSuggest: () -> Unit) {
    when (state.assistant) {
        LlmAvailability.READY -> {
            VerticalSpacer(6.dp)
            if (state.assistantBusy) {
                Text(stringResource(R.string.generator_assistant_working), style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            } else {
                SecondaryButton(
                    text = stringResource(R.string.generator_suggest),
                    onClick = onSuggest,
                    enabled = !state.busy,
                )
            }
        }
        LlmAvailability.PREPARING ->
            Text(stringResource(R.string.generator_assistant_preparing), style = MaterialTheme.typography.bodySmall)
        LlmAvailability.UNAVAILABLE -> Unit
    }
}
