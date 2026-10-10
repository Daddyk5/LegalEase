package com.hcdc.legalease.feature.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the result screen shows for a completed analysis. */
data class ResultModel(
    val contractName: String,
    val summary: String,
    val classification: Classification,
    val confidence: Float,
)

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data class Success(val result: ResultModel) : ResultUiState
    data class Error(val error: AppError) : ResultUiState
}

@HiltViewModel
class ResultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeAnalysis: ObserveAnalysisUseCase,
) : ViewModel() {

    private val analysisId: String = checkNotNull(savedStateHandle[ResultRoute.ARG_ANALYSIS_ID])

    val uiState: StateFlow<ResultUiState> = observeAnalysis(analysisId)
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ResultUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun toUiState(analysis: Analysis?): ResultUiState {
    if (analysis == null) return ResultUiState.Error(AppError.DocumentNotFound)
    val verdict = analysis.verdict
    return when (analysis.status) {
        AnalysisStatus.COMPLETED if verdict != null -> ResultUiState.Success(
            ResultModel(analysis.documentName, verdict.summary, verdict.classification, verdict.confidence)
        )
        AnalysisStatus.COMPLETED, AnalysisStatus.FAILED ->
            ResultUiState.Error(analysis.error ?: AppError.Unexpected("missing_error"))
        AnalysisStatus.CANCELLED -> ResultUiState.Error(AppError.Cancelled)
        AnalysisStatus.QUEUED, AnalysisStatus.EXTRACTING_TEXT, AnalysisStatus.CLASSIFYING -> ResultUiState.Loading
    }
}
