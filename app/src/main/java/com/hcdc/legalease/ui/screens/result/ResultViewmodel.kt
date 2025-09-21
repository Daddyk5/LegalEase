package com.hcdc.legalease.ui.screens.result

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.hcdc.legalease.data.ClausesModel
import com.hcdc.legalease.ml.TFLiteClassifier
import com.hcdc.legalease.ml.preprocessTextToIds
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class ResultViewmodel(
    application: Application,
    private val apiKey: String? = null
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "ResultViewmodel"
        private const val GEMINI_MODEL = "gemini-2.0-flash-lite"
    }

    // Offline classifier
    private val classifier = TFLiteClassifier(application.applicationContext)

    // Online model (only if apiKey is provided)
    private val geminiModel: GenerativeModel? by lazy {
        apiKey?.let { GenerativeModel(modelName = GEMINI_MODEL, apiKey = it) }
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

    /**
     * Main entry point: decides offline (TFLite) or online (Gemini)
     */
    fun analyze(ocrText: String) {
        if (hasInternet(getApplication()) && geminiModel != null) {
            analyzeOnline(ocrText) // Gemini
        } else {
            analyzeOffline(ocrText) // TFLite
        }
    }

    /**
     * Offline analysis using TFLite
     */
    private fun analyzeOffline(rawText: String) {
        viewModelScope.launch {
            _scanCompleted.value = false
            try {
                if (rawText.isBlank()) {
                    _clauses.value = null
                    return@launch
                }

                // 🔹 vocab + preprocessing + classification
                val vocab = loadVocab(getApplication())
                val inputIds = preprocessTextToIds(rawText, vocab)
                val buffer = classifier.convertToByteBuffer(inputIds)
                val result = classifier.classify(buffer)

                if (result != null) {
                    val (label, confidence) = result
                    _clauses.value = ClausesModel(
                        contractName = "Uploaded Contract",
                        summary = rawText.take(300),
                        classification = label,
                        confidence = confidence
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Offline analysis failed: ${e.message}", e)
                _clauses.value = null
            } finally {
                _scanCompleted.value = true
            }
        }
    }

    /**
     * Online analysis using Gemini
     */
    private fun analyzeOnline(rawText: String) {
        viewModelScope.launch {
            _scanCompleted.value = false
            try {
                val prompt = """
                    You are a contract classification engine.
                    Return JSON with this schema:
                    {
                      "contractName": "string",
                      "summary": "string",
                      "classification": "Void | Voidable | Unenforceable | Rescissible | Enforceable",
                      "confidence": 0.0
                    }
                    Document:
                    $rawText
                """.trimIndent()

                val response = geminiModel?.generateContent(prompt)
                val raw = response?.text?.trim().orEmpty()

                val jsonText = extractJsonObject(raw)
                val parsed = try {
                    json.decodeFromString<ClausesModel>(jsonText!!)
                } catch (e: SerializationException) {
                    Log.e(TAG, "JSON decode error: ${e.message}\nPayload:\n$jsonText")
                    null
                }
                _clauses.value = parsed
            } catch (e: Exception) {
                Log.e(TAG, "Online analysis failed: ${e.message}", e)
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

    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start == -1 || end <= start) return null
        return text.substring(start, end + 1)
            .replace(Regex(",\\s*([}\\]])"), "$1")
            .trim()
    }

    private fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Load vocab.json from assets
     */
    private fun loadVocab(context: Context): Map<String, Int> {
        return try {
            val jsonStr = context.assets.open("vocab.json").bufferedReader().use { it.readText() }
            Json.decodeFromString<Map<String, Int>>(jsonStr)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
