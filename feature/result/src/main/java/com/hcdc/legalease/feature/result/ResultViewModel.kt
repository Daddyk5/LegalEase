package com.hcdc.legalease.feature.result

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.core.domain.model.Analysis
import com.hcdc.legalease.core.domain.model.AnalysisReport
import com.hcdc.legalease.core.domain.model.AnalysisStatus
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.usecase.ObserveAnalysisUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** What the result screen shows for a completed analysis. */
data class ResultModel(
    val analysisId: String,
    val title: String,
    val report: AnalysisReport,
    /** 1-based pages OCR could not read at all. */
    val unreadablePages: List<Int>,
    /** Remote Config text explaining the confidence policy. */
    val thresholdText: String,
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
    featureFlags: FeatureFlagsRepository,
) : ViewModel() {

    val analysisId: String = checkNotNull(savedStateHandle[ResultRoute.ARG_ANALYSIS_ID])

    val uiState: StateFlow<ResultUiState> =
        combine(observeAnalysis(analysisId), featureFlags.flags) { analysis, flags ->
            toUiState(analysis, flags.confidenceThresholdText)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ResultUiState.Loading)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}

internal fun toUiState(analysis: Analysis?, thresholdText: String = ""): ResultUiState {
    if (analysis == null) return ResultUiState.Error(AppError.DocumentNotFound)
    val report = analysis.report
    return when (analysis.status) {
        AnalysisStatus.COMPLETED if report != null -> ResultUiState.Success(
            ResultModel(
                analysisId = analysis.id,
                title = report.contractTitle ?: analysis.documentName,
                report = report,
                unreadablePages = analysis.unreadablePages.map { it + 1 },
                thresholdText = thresholdText,
            )
        )
        AnalysisStatus.COMPLETED, AnalysisStatus.FAILED ->
            ResultUiState.Error(analysis.error ?: AppError.Unexpected("missing_error"))
        AnalysisStatus.CANCELLED -> ResultUiState.Error(AppError.Cancelled)
        AnalysisStatus.QUEUED, AnalysisStatus.EXTRACTING_TEXT, AnalysisStatus.CLASSIFYING,
        AnalysisStatus.ANALYZING_PAGES, AnalysisStatus.CHECKING -> ResultUiState.Loading
    }
}
