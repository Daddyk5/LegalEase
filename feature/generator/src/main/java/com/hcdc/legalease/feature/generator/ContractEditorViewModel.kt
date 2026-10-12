package com.hcdc.legalease.feature.generator

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.di.ApplicationScope
import com.hcdc.legalease.core.domain.engine.ContractAssistant
import com.hcdc.legalease.core.domain.engine.ContractDocuments
import com.hcdc.legalease.core.domain.engine.LlmAvailability
import com.hcdc.legalease.core.domain.engine.PaperSize
import com.hcdc.legalease.core.domain.generator.ClauseRejection
import com.hcdc.legalease.core.domain.generator.ContractTemplates
import com.hcdc.legalease.core.domain.generator.ContractValidator
import com.hcdc.legalease.core.domain.generator.FieldError
import com.hcdc.legalease.core.domain.generator.FormField
import com.hcdc.legalease.core.domain.generator.FormStep
import com.hcdc.legalease.core.domain.generator.LegalNotice
import com.hcdc.legalease.core.domain.generator.LegalNotices
import com.hcdc.legalease.core.domain.generator.OptionalClauseLibrary
import com.hcdc.legalease.core.domain.generator.RenderedContract
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.ContractDraft
import com.hcdc.legalease.core.domain.model.ContractType
import com.hcdc.legalease.core.domain.model.GeneratedContract
import com.hcdc.legalease.core.domain.model.OptionalClause
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.telemetry.AnalyticsEvent
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import com.hcdc.legalease.core.domain.usecase.AnalyzeGeneratedContractUseCase
import com.hcdc.legalease.core.domain.usecase.AssistantAvailabilityUseCase
import com.hcdc.legalease.core.domain.usecase.ExportContractUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveContractVersionUseCase
import com.hcdc.legalease.core.domain.usecase.ProposedEdit
import com.hcdc.legalease.core.domain.usecase.RewriteClauseUseCase
import com.hcdc.legalease.core.domain.usecase.RewriteOutcome
import com.hcdc.legalease.core.domain.usecase.SaveContractVersionUseCase
import com.hcdc.legalease.core.domain.usecase.SuggestClausesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

enum class EditorStage { FORM, PREVIEW }

/** A one-off message for the user. */
sealed interface EditorMessage {
    data class SuggestionsWithheld(val count: Int) : EditorMessage
    data object NoSuggestions : EditorMessage
    data class RewriteWithheld(val reason: ClauseRejection) : EditorMessage
    data object RewriteUnchanged : EditorMessage
    data class Failed(val error: AppError) : EditorMessage
}

data class EditorUiState(
    val loading: Boolean = true,
    val stage: EditorStage = EditorStage.FORM,
    val draft: ContractDraft = ContractDraft(ContractType.LEASE),
    val stepIndex: Int = 0,
    val errors: Map<FormField, FieldError> = emptyMap(),
    val contractId: String? = null,
    /** The saved version on screen. Null while there are unsaved changes. */
    val saved: GeneratedContract? = null,
    /** Text of a version whose form could not be read: shown, but it cannot be edited. */
    val readOnlyBody: String? = null,
    val analysis: Analysis? = null,
    val assistant: LlmAvailability = LlmAvailability.UNAVAILABLE,
    val assistantBusy: Boolean = false,
    val proposal: ProposedEdit? = null,
    val message: EditorMessage? = null,
    val paper: PaperSize = PaperSize.A4,
    val busy: Boolean = false,
) {
    val steps: List<FormStep> get() = ContractValidator.steps(draft.type)
    val step: FormStep get() = steps[stepIndex.coerceIn(steps.indices)]
    val rendered: RenderedContract? get() = ContractTemplates.render(draft)
    val notices: List<LegalNotice> get() = LegalNotices.forDraft(draft)
    val library: List<OptionalClause> get() = OptionalClauseLibrary.forDraft(draft)
    val hasUnsavedChanges: Boolean get() = saved == null

    /** Export needs a saved version whose analysis has finished. */
    val canExport: Boolean get() = saved != null && analysis?.status == AnalysisStatus.COMPLETED
}

sealed interface EditorEvent {
    data class Share(val file: File) : EditorEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ContractEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveVersion: SaveContractVersionUseCase,
    private val observeVersion: ObserveContractVersionUseCase,
    private val observeAnalysis: ObserveAnalysisUseCase,
    private val analyze: AnalyzeGeneratedContractUseCase,
    private val assistantAvailability: AssistantAvailabilityUseCase,
    private val suggestClauses: SuggestClausesUseCase,
    private val rewriteClause: RewriteClauseUseCase,
    private val assistant: ContractAssistant,
    private val documents: ContractDocuments,
    private val exportContract: ExportContractUseCase,
    private val analytics: AnalyticsTracker,
    private val clock: Clock,
    @ApplicationScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events: Flow<EditorEvent> = _events.receiveAsFlow()

    private val savedVersionId = MutableStateFlow<String?>(null)
    private var assistantJob: Job? = null

    /** Further suggestions, shown one at a time after the current proposal is accepted or discarded. */
    private var pendingProposals: List<ProposedEdit> = emptyList()

    init {
        val versionId: String? = savedStateHandle[ContractEditorRoute.ARG_VERSION_ID]
        val type: String? = savedStateHandle[ContractEditorRoute.ARG_TYPE]
        viewModelScope.launch { load(versionId, type) }
        viewModelScope.launch {
            savedVersionId
                .flatMapLatest { id -> if (id == null) flowOf(null) else observeVersion(id) }
                .flatMapLatest { version ->
                    val analysisId = version?.analysisId
                    if (analysisId == null) {
                        flowOf(version to null)
                    } else {
                        observeAnalysis(analysisId).map { version to it }
                    }
                }
                .collect { (version, analysis) ->
                    _uiState.update { state ->
                        // Ignore a version that no longer matches what is on screen.
                        val current = version != null && state.saved?.id == version.id
                        if (current) state.copy(saved = version, analysis = analysis) else state
                    }
                }
        }
        viewModelScope.launch {
            val availability = assistantAvailability()
            _uiState.update { it.copy(assistant = availability) }
        }
    }

    private suspend fun load(versionId: String?, type: String?) {
        if (versionId != null) {
            val version = observeVersion(versionId).first()
            if (version == null) {
                _uiState.update { it.copy(loading = false, message = EditorMessage.Failed(AppError.DocumentNotFound)) }
                return
            }
            _uiState.update {
                it.copy(
                    loading = false,
                    stage = EditorStage.PREVIEW,
                    draft = version.draft ?: ContractDraft(version.type),
                    contractId = version.contractId,
                    saved = version,
                    readOnlyBody = version.body.takeIf { version.draft == null },
                )
            }
            savedVersionId.value = version.id
        } else {
            val contractType = type?.let { runCatching { ContractType.valueOf(it) }.getOrNull() } ?: ContractType.LEASE
            val today = LocalDate.now(clock).toString()
            _uiState.update { it.copy(loading = false, draft = ContractDraft(contractType, executionDate = today)) }
        }
    }

    /** Applies a form edit. After a failed "Next", errors on this step update as the user types. */
    fun update(change: (ContractDraft) -> ContractDraft) = _uiState.update { state ->
        val draft = change(state.draft)
        val errors = if (state.errors.isEmpty()) state.errors else ContractValidator.validate(draft, state.step)
        val saved = state.saved?.takeIf { it.draft == draft }
        // Any change means a new, unanalysed version: the old analysis no longer describes this text.
        state.copy(draft = draft, errors = errors, saved = saved, analysis = state.analysis.takeIf { saved != null })
    }

    fun next() = _uiState.update { state ->
        val errors = ContractValidator.validate(state.draft, state.step)
        when {
            errors.isNotEmpty() -> state.copy(errors = errors)
            state.stepIndex < state.steps.lastIndex -> state.copy(stepIndex = state.stepIndex + 1, errors = emptyMap())
            else -> state.copy(stage = EditorStage.PREVIEW, errors = emptyMap())
        }
    }

    /** Returns false when there is no earlier step, so the screen can leave. */
    fun back(): Boolean {
        val state = _uiState.value
        return when {
            state.stage == EditorStage.PREVIEW && state.saved == null -> {
                _uiState.update { it.copy(stage = EditorStage.FORM, stepIndex = it.steps.lastIndex) }
                true
            }
            state.stage == EditorStage.FORM && state.stepIndex > 0 -> {
                _uiState.update { it.copy(stepIndex = it.stepIndex - 1, errors = emptyMap()) }
                true
            }
            else -> false
        }
    }

    /** Edit a saved version: saving the edit creates the next version. */
    fun edit() = _uiState.update { it.copy(stage = EditorStage.FORM, stepIndex = 0, errors = emptyMap()) }

    /** Adds an optional clause, or removes it if it is already in the contract. */
    fun toggleClause(clause: OptionalClause) = update { draft ->
        val present = draft.optionalClauses.any { it.id == clause.id }
        draft.copy(
            optionalClauses = if (present) {
                draft.optionalClauses.filterNot { it.id == clause.id }
            } else {
                draft.optionalClauses + clause
            }
        )
    }

    fun suggest() = runAssistant {
        when (val result = suggestClauses(_uiState.value.draft)) {
            is AppResult.Failure -> _uiState.update { it.copy(message = EditorMessage.Failed(result.error)) }
            is AppResult.Success -> {
                val (proposals, withheld) = result.value
                pendingProposals = proposals.drop(1)
                _uiState.update {
                    it.copy(
                        proposal = proposals.firstOrNull(),
                        message = when {
                            withheld > 0 -> EditorMessage.SuggestionsWithheld(withheld)
                            proposals.isEmpty() -> EditorMessage.NoSuggestions
                            else -> null
                        },
                    )
                }
            }
        }
    }

    fun rewrite(clause: OptionalClause) = runAssistant {
        when (val result = rewriteClause(_uiState.value.draft, clause)) {
            is AppResult.Failure -> _uiState.update { it.copy(message = EditorMessage.Failed(result.error)) }
            is AppResult.Success -> _uiState.update {
                when (val outcome = result.value) {
                    is RewriteOutcome.Proposed -> it.copy(proposal = outcome.edit)
                    is RewriteOutcome.Withheld -> it.copy(message = EditorMessage.RewriteWithheld(outcome.reason))
                    RewriteOutcome.Unchanged -> it.copy(message = EditorMessage.RewriteUnchanged)
                }
            }
        }
    }

    /** Accepting replaces the clause with the same id, or adds a new one. Nothing changes without this. */
    fun acceptProposal() {
        val proposal = _uiState.value.proposal ?: return
        update { draft ->
            val replaced = draft.optionalClauses.any { it.id == proposal.clause.id }
            draft.copy(
                optionalClauses = if (replaced) {
                    draft.optionalClauses.map { if (it.id == proposal.clause.id) proposal.clause else it }
                } else {
                    draft.optionalClauses + proposal.clause
                }
            )
        }
        analytics.log(AnalyticsEvent.ClauseAssist(if (proposal.before == null) "suggest" else "rewrite", "accepted", 1))
        nextProposal()
    }

    /** Discards the current proposal and shows the next pending one, if any. */
    fun nextProposal() {
        val next = pendingProposals.firstOrNull()
        pendingProposals = pendingProposals.drop(1)
        _uiState.update { it.copy(proposal = next) }
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    private fun runAssistant(block: suspend () -> Unit) {
        if (assistantJob?.isActive == true) return
        assistantJob = viewModelScope.launch {
            _uiState.update { it.copy(assistantBusy = true, message = null) }
            try {
                block()
            } finally {
                _uiState.update { it.copy(assistantBusy = false) }
            }
        }
    }

    /** Saves a new version and runs it through the same analysis as an uploaded contract. */
    fun saveAndAnalyze() {
        val state = _uiState.value
        if (state.busy || state.saved != null) return
        val rendered = state.rendered ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            // The analysis needs the model's memory; the assistant must let go of it first.
            assistantJob?.cancel()
            assistant.release()
            when (val saved = saveVersion(state.contractId, state.draft)) {
                is AppResult.Failure ->
                    _uiState.update { it.copy(busy = false, message = EditorMessage.Failed(saved.error)) }
                is AppResult.Success -> {
                    val version = saved.value
                    _uiState.update { it.copy(saved = version, contractId = version.contractId, analysis = null) }
                    savedVersionId.value = version.id
                    startAnalysis(version, rendered)
                }
            }
        }
    }

    /** For a saved version whose analysis failed or was cancelled. */
    fun analyzeAgain() {
        val state = _uiState.value
        val version = state.saved ?: return
        val rendered = state.rendered ?: return
        if (state.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, message = null) }
            startAnalysis(version, rendered)
        }
    }

    private suspend fun startAnalysis(version: GeneratedContract, rendered: RenderedContract) {
        val pages = documents.pageTexts(rendered, PaperSize.A4)
        val result = analyze(version, pages)
        _uiState.update {
            it.copy(busy = false, message = (result as? AppResult.Failure)?.let { f -> EditorMessage.Failed(f.error) })
        }
    }

    fun setPaper(paper: PaperSize) = _uiState.update { it.copy(paper = paper) }

    fun export() {
        val state = _uiState.value
        val version = state.saved ?: return
        if (!state.canExport || state.busy) return
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            when (val result = exportContract(version, state.paper)) {
                is AppResult.Success -> _events.send(EditorEvent.Share(result.value))
                is AppResult.Failure -> _uiState.update { it.copy(message = EditorMessage.Failed(result.error)) }
            }
            _uiState.update { it.copy(busy = false) }
        }
    }

    /** Frees the model when the editor closes; it is loaded again on demand. */
    override fun onCleared() {
        appScope.launch { assistant.release() }
    }
}
