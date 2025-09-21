package com.hcdc.legalease.ui.screens.result

import android.app.Application
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.data.ClausesModel
import com.hcdc.legalease.ml.TFLiteClassifier
import com.hcdc.legalease.ml.preprocessTextToIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ResultViewmodel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ResultViewmodel"
    }

    private val classifier = TFLiteClassifier(application.applicationContext)

    private val _clauses = mutableStateOf<ClausesModel?>(null)
    val clauses: State<ClausesModel?> = _clauses

    private val _scanCompleted = MutableStateFlow(false)
    val scanCompleted: StateFlow<Boolean> = _scanCompleted

    /**
     * Analyze OCR text using on-device TFLite model
     */
    fun analyzeText(rawOcrText: String) {
        viewModelScope.launch {
            _scanCompleted.value = false

            try {
                if (rawOcrText.isBlank()) {
                    Log.w(TAG, "OCR text is blank; nothing to analyze.")
                    _clauses.value = null
                    _scanCompleted.value = true
                    return@launch
                }

                // 🔹 Preprocess: convert text → token IDs
                // TODO: load your vocab map from assets/resources
                val vocab = emptyMap<String, Int>() // replace with real vocab
                val inputIds = preprocessTextToIds(rawOcrText, vocab)
                val inputBuffer = classifier.convertToByteBuffer(inputIds)

                // 🔹 Run classification
                val result = classifier.classify(inputBuffer)
                if (result != null) {
                    val (label, confidence) = result
                    _clauses.value = ClausesModel(
                        contractName = "Uploaded Contract",
                        summary = rawOcrText.take(300),
                        classification = label,
                        confidence = confidence
                    )
                } else {
                    Log.e(TAG, "Model returned null result.")
                    _clauses.value = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error running TFLite model: ${e.message}", e)
                _clauses.value = null
            } finally {
                _scanCompleted.value = true
            }
        }
    }

    fun resetScan() {
        _clauses.value = null
        _scanCompleted.value = false
    }

    override fun onCleared() {
        super.onCleared()
        classifier.close()
    }
}
