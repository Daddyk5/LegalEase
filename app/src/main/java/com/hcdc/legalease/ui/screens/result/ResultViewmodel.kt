package com.hcdc.legalease.ui.screens.result

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.hcdc.legalease.data.ClausesModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class ResultViewmodel : ViewModel() {

    private val model = Firebase
        .ai(backend = GenerativeBackend.googleAI())
        .generativeModel("gemini-2.0-flash-lite")

    private val _clauses = mutableStateOf<ClausesModel?>(null)
    val clauses: State<ClausesModel?> = _clauses

    private val _scanCompleted = MutableStateFlow(false)
    val scanCompleted: StateFlow<Boolean> = _scanCompleted

    fun analyzePrompt(prompt: String) {
        viewModelScope.launch {
            _scanCompleted.value = false
            try {
                val response = model.generateContent(prompt)
                val rawText = response.text ?: "No output returned."
                Log.d("ResultViewmodel", "Raw Gemini response:\n$rawText")

                val jsonStart = rawText.indexOf("{")
                val jsonEnd = rawText.lastIndexOf("}") + 1

                if (jsonStart == -1 || jsonEnd <= jsonStart) {
                    Log.w("ResultViewmodel", "No valid JSON object found in response.")
                    _clauses.value = null
                    return@launch
                }

                val jsonText = rawText.substring(jsonStart, jsonEnd)
                val parsed = Json.decodeFromString<ClausesModel>(jsonText)
                _clauses.value = parsed

            } catch (e: Exception) {
                Log.e("ResultViewmodel", "Error parsing Gemini output: ${e.localizedMessage}")
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
}
