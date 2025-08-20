package com.hcdc.legalease.ui.screens.result

import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.hcdc.legalease.data.ClausesModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ResultViewmodel(private val apiKey: String) : ViewModel() {

    companion object {
        private const val TAG = "ResultViewmodel"
        private const val MODEL_NAME = "gemini-2.0-flash-lite"
    }

    private val model: GenerativeModel by lazy {
        GenerativeModel(modelName = MODEL_NAME, apiKey = apiKey)
    }

    private val _clauses = mutableStateOf<ClausesModel?>(null)
    val clauses: State<ClausesModel?> = _clauses

    private val _scanCompleted = MutableStateFlow(false)
    val scanCompleted: StateFlow<Boolean> = _scanCompleted

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun analyzePrompt(rawOcrText: String) {
        viewModelScope.launch {
            _scanCompleted.value = false
            try {
                if (rawOcrText.isBlank()) {
                    Log.w(TAG, "OCR text is blank; nothing to analyze.")
                    _clauses.value = null
                    return@launch
                }

                val instruction = """
                    You are an information extraction engine.
                    From the following legal document text, extract fields for this JSON schema, and RETURN ONLY a single JSON object with no extra text, no markdown, and no code fences.

                    Schema (keys must exist; use empty strings/arrays if missing):
                    { ... schema ... }
                    
                    Document text:
                """.trimIndent()

                val prompt = buildString {
                    append(instruction)
                    append('\n')
                    append(rawOcrText.take(6000))
                }

                val response = model.generateContent(prompt)
                val rawText = response.text?.trim().orEmpty()
                Log.d(TAG, "Raw Gemini response:\n$rawText")

                val jsonText = extractJsonObject(rawText) ?: run {
                    Log.w(TAG, "No JSON object found in response.")
                    _clauses.value = null
                    _scanCompleted.value = true
                    return@launch
                }

                val parsed = try {
                    json.decodeFromString<ClausesModel>(jsonText)
                } catch (e: SerializationException) {
                    Log.e(TAG, "JSON decode error: ${e.message}\nPayload:\n$jsonText")
                    null
                }

                _clauses.value = parsed
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing Gemini output: ${e.message}", e)
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

    private fun extractJsonObject(text: String): String? {
        var cleaned = text
            .removePrefix("```json").removeSuffix("```").trim()
            .removePrefix("```").removeSuffix("```").trim()

        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        if (start == -1 || end <= start) return null

        cleaned = cleaned.substring(start, end + 1)
        cleaned = cleaned.replace(Regex(",\\s*([}\\]])"), "$1")
        return cleaned
    }
}
