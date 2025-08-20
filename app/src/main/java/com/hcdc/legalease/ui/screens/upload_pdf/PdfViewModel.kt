package com.hcdc.legalease.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.tasks.await
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PdfViewModel : ViewModel() {

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _pdfBitmaps = MutableStateFlow<List<Bitmap>>(emptyList())
    val pdfBitmaps: StateFlow<List<Bitmap>> = _pdfBitmaps.asStateFlow()

    private val _ocrResults = MutableStateFlow<List<String>>(emptyList())
    val ocrResults: StateFlow<List<String>> = _ocrResults.asStateFlow()

    private val _scanCompleted = MutableStateFlow(false)
    val scanCompleted: StateFlow<Boolean> = _scanCompleted

    private var lastProcessedUri: Uri? = null

    fun setPdfUri(uri: Uri?) { _pdfUri.value = uri }
    fun setPdfName(name: String?) { _fileName.value = name }
    fun clearOcrResults() { _ocrResults.value = emptyList() }

    fun processPdfAndRunOcr(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _scanCompleted.value = false
            val uri = _pdfUri.value ?: return@launch

            if (uri == lastProcessedUri && _ocrResults.value.isNotEmpty()) {
                Log.d("PdfViewModel", "Same URI and OCR already present; skipping reprocess.")
                _scanCompleted.value = true
                return@launch
            }
            lastProcessedUri = uri

            _pdfBitmaps.value = emptyList()
            _ocrResults.value = emptyList()

            val bitmaps = mutableListOf<Bitmap>()

            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@launch
                PdfRenderer(pfd).use { pdfRenderer ->
                    for (i in 0 until pdfRenderer.pageCount) {
                        pdfRenderer.openPage(i).use { page ->
                            // Increase scale for better OCR (5–8x depending on device)
                            val scale = 8
                            val width = page.width * scale
                            val height = page.height * scale

                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(android.graphics.Color.WHITE)

                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmaps.add(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("PdfViewModel", "PDF render error: ${e.localizedMessage}")
            }

            _pdfBitmaps.value = bitmaps

            if (bitmaps.isEmpty()) {
                Log.w("PdfViewModel", "No pages rendered; OCR skipped.")
                _ocrResults.value = listOf("")
                _scanCompleted.value = true
                return@launch
            }

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val sb = StringBuilder()

            try {
                for ((idx, bitmap) in bitmaps.withIndex()) {
                    try {
                        val image = InputImage.fromBitmap(bitmap, 0)
                        val visionText = recognizer.process(image).await()
                        val text = visionText.text.trim()
                        Log.d("PdfViewModel", "OCR page $idx chars=${text.length}")
                        if (text.isNotBlank()) {
                            sb.appendLine(text)
                            sb.appendLine() // page break spacing
                        }
                    } catch (e: Exception) {
                        Log.e("PdfViewModel", "OCR error on page $idx: ${e.localizedMessage}")
                    }
                }
            } finally {
                recognizer.close()
            }

            val fullText = sb.toString().trim()
            _ocrResults.value = listOf(fullText)
            _scanCompleted.value = true
        }
    }
}
