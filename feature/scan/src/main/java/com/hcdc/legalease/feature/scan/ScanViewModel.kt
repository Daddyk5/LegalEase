package com.hcdc.legalease.feature.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.usecase.CancelAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import com.hcdc.legalease.core.domain.usecase.StartAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a running scan is. Null in [ScanUiState.progress] means no scan is running. */
sealed interface ScanProgress {
    data object Preparing : ScanProgress
    data class ReadingPage(val page: Int, val pageCount: Int) : ScanProgress
    data object Classifying : ScanProgress
    data class AnalyzingPage(val page: Int, val pageCount: Int) : ScanProgress
    data object Checking : ScanProgress
}

data class ScanUiState(
    val pdfUri: String? = null,
    val fileName: String? = null,
    val imageUris: List<String> = emptyList(),
    val progress: ScanProgress? = null,
)

sealed interface ScanEvent {
    data class NavigateToResult(val analysisId: String) : ScanEvent
    data class ShowError(val error: AppError) : ScanEvent
}

/**
 * Backs both upload screens. The analysis itself runs in WorkManager; this ViewModel only
 * starts it and follows its progress in the database, so leaving the app does not stop it.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val startAnalysis: StartAnalysisUseCase,
    private val observeAnalysis: ObserveAnalysisUseCase,
    private val cancelAnalysis: CancelAnalysisUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _events = Channel<ScanEvent>(Channel.BUFFERED)
    val events: Flow<ScanEvent> = _events.receiveAsFlow()

    private var observeJob: Job? = null

    init {
        // Resume following a scan started before the process was recreated.
        savedStateHandle.get<String>(KEY_ACTIVE_ANALYSIS)?.let(::follow)
    }

    fun onPdfSelected(uri: String, fileName: String?) {
        _uiState.update { it.copy(pdfUri = uri, fileName = fileName) }
    }

    fun onImagesSelected(uris: List<String>) {
        _uiState.update { it.copy(imageUris = uris) }
    }

    /** @param fallbackName used when the picker did not report a file name. */
    fun scanPdf(fallbackName: String) {
        val state = _uiState.value
        val uri = state.pdfUri ?: return
        start(state.fileName ?: fallbackName, SourceKind.PDF, listOf(uri))
    }

    fun scanImages(documentName: String) {
        val uris = _uiState.value.imageUris
        if (uris.isNotEmpty()) start(documentName, SourceKind.IMAGES, uris)
    }

    /** Stops the running analysis, if any. Starts synchronously so it completes even if the screen is leaving. */
    fun cancelScan() {
        val id = savedStateHandle.get<String>(KEY_ACTIVE_ANALYSIS) ?: return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { cancelAnalysis(id) }
    }

    private fun start(name: String, kind: SourceKind, uris: List<String>) {
        if (_uiState.value.progress != null) return
        _uiState.update { it.copy(progress = ScanProgress.Preparing) }
        viewModelScope.launch {
            when (val started = startAnalysis(name, kind, uris)) {
                is AppResult.Success -> follow(started.value)
                is AppResult.Failure -> {
                    _uiState.update { it.copy(progress = null) }
                    _events.send(ScanEvent.ShowError(started.error))
                }
            }
        }
    }

    private fun follow(analysisId: String) {
        savedStateHandle[KEY_ACTIVE_ANALYSIS] = analysisId
        _uiState.update { it.copy(progress = ScanProgress.Preparing) }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            observeAnalysis(analysisId).collect { analysis -> onUpdate(analysisId, analysis) }
        }
    }

    private suspend fun onUpdate(analysisId: String, analysis: Analysis?) {
        when (analysis?.status) {
            null -> finish(ScanEvent.ShowError(AppError.DocumentNotFound))
            AnalysisStatus.COMPLETED -> finish(ScanEvent.NavigateToResult(analysisId))
            AnalysisStatus.FAILED -> {
                val error = analysis.error ?: AppError.Unexpected("missing_error")
                // Problems with the upload are fixed here, by picking another file; anything else is
                // explained on the result screen, as before.
                val event = if (error in UPLOAD_ERRORS) {
                    ScanEvent.ShowError(error)
                } else {
                    ScanEvent.NavigateToResult(analysisId)
                }
                finish(event)
            }
            AnalysisStatus.CANCELLED -> finish(null)
            else -> setProgress(progressOf(analysis))
        }
    }

    /** Progress for a still-running analysis. */
    private fun progressOf(analysis: Analysis): ScanProgress = when (analysis.status) {
        AnalysisStatus.EXTRACTING_TEXT -> ScanProgress.ReadingPage(analysis.currentPage ?: 0, analysis.pageCount ?: 0)
        AnalysisStatus.CLASSIFYING -> ScanProgress.Classifying
        AnalysisStatus.ANALYZING_PAGES -> ScanProgress.AnalyzingPage(analysis.currentPage ?: 0, analysis.pageCount ?: 0)
        AnalysisStatus.CHECKING -> ScanProgress.Checking
        else -> ScanProgress.Preparing
    }

    private fun setProgress(progress: ScanProgress) {
        _uiState.update { it.copy(progress = progress) }
    }

    private suspend fun finish(event: ScanEvent?) {
        savedStateHandle.remove<String>(KEY_ACTIVE_ANALYSIS)
        _uiState.update { it.copy(progress = null) }
        event?.let { _events.send(it) }
        observeJob?.cancel()
    }

    private companion object {
        const val KEY_ACTIVE_ANALYSIS = "active_analysis_id"
        val UPLOAD_ERRORS = setOf(AppError.UnreadableFile, AppError.NoTextFound, AppError.StorageFull)
    }
}
