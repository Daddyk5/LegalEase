package com.hcdc.legalease.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.data.ClausesModel
import com.hcdc.legalease.data.DocumentRepository
import com.hcdc.legalease.ml.ClassifierException
import com.hcdc.legalease.ml.TFLiteClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ResultUiState {
    data object Loading : ResultUiState
    data class Success(val clauses: ClausesModel) : ResultUiState
    data class Error(val error: ResultError) : ResultUiState
}

enum class ResultError {
    /** The analysis ID is unknown, e.g. after process death. */
    DOCUMENT_NOT_FOUND,

    /** The model has never been downloaded and the device is offline. */
    MODEL_UNAVAILABLE,

    /** The bundled vocabulary or downloaded model does not match this app version. */
    MODEL_INCOMPATIBLE,
}

class ResultViewmodel(
    private val analysisId: String,
    private val documentRepository: DocumentRepository,
    private val classifier: TFLiteClassifier,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultUiState>(ResultUiState.Loading)
    val uiState: StateFlow<ResultUiState> = _uiState.asStateFlow()

    init {
        analyze()
    }

    fun analyze() {
        viewModelScope.launch {
            _uiState.value = ResultUiState.Loading
            val document = documentRepository.get(analysisId)
            if (document == null) {
                _uiState.value = ResultUiState.Error(ResultError.DOCUMENT_NOT_FOUND)
                return@launch
            }
            val text = document.fullText
            _uiState.value = try {
                val prediction = classifier.classify(text)
                ResultUiState.Success(
                    ClausesModel(
                        contractName = document.name,
                        summary = text.take(SUMMARY_PREVIEW_CHARS),
                        classification = prediction.classification,
                        confidence = prediction.probability,
                    )
                )
            } catch (_: ClassifierException.ModelUnavailable) {
                ResultUiState.Error(ResultError.MODEL_UNAVAILABLE)
            } catch (_: ClassifierException) {
                ResultUiState.Error(ResultError.MODEL_INCOMPATIBLE)
            }
        }
    }

    private companion object {
        const val SUMMARY_PREVIEW_CHARS = 300
    }
}
