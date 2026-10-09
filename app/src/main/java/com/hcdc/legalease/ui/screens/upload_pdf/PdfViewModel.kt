package com.hcdc.legalease.ui.screens.upload_pdf

import android.app.Application
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.R
import com.hcdc.legalease.appContainer
import com.hcdc.legalease.ml.OcrEngine
import com.hcdc.legalease.ml.OcrPages
import com.hcdc.legalease.ml.ocrPages
import com.hcdc.legalease.ml.renderPage
import com.hcdc.legalease.ui.screens.ScanFailure
import com.hcdc.legalease.ui.screens.ScanState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

class PdfViewModel(application: Application) : AndroidViewModel(application) {

    private val documentRepository = application.appContainer.documentRepository

    private val _pdfUri = MutableStateFlow<Uri?>(null)
    val pdfUri: StateFlow<Uri?> = _pdfUri.asStateFlow()

    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _navigateToResult = Channel<String>(Channel.BUFFERED)

    /** Emits the analysis ID once a scan finishes. */
    val navigateToResult: Flow<String> = _navigateToResult.receiveAsFlow()

    private var scanJob: Job? = null

    fun setPdfUri(uri: Uri?) { _pdfUri.value = uri }
    fun setPdfName(name: String?) { _fileName.value = name }

    fun dismissError() {
        if (_scanState.value is ScanState.Failed) _scanState.value = ScanState.Idle
    }

    fun scan() {
        val uri = _pdfUri.value ?: return
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _scanState.value = ScanState.Scanning(currentPage = 0, totalPages = 0)
            val result = try {
                withContext(Dispatchers.IO) { ocrPdf(uri) }
            } catch (_: IOException) {
                null
            } catch (_: SecurityException) {
                null
            }
            when {
                result == null -> _scanState.value = ScanState.Failed(ScanFailure.UNREADABLE_FILE)
                !result.hasText -> _scanState.value = ScanState.Failed(ScanFailure.NO_TEXT_FOUND)
                else -> {
                    val name = _fileName.value
                        ?: getApplication<Application>().getString(R.string.scan_default_pdf_name)
                    val id = documentRepository.save(name, result.pages, result.unreadablePages)
                    _navigateToResult.send(id)
                    _scanState.value = ScanState.Idle
                }
            }
        }
    }

    /** Renders and reads one page at a time, recycling each bitmap before the next is created. */
    private suspend fun ocrPdf(uri: Uri): OcrPages? {
        val descriptor = getApplication<Application>().contentResolver.openFileDescriptor(uri, "r") ?: return null
        return descriptor.use {
            PdfRenderer(descriptor).use { renderer ->
                OcrEngine().use { ocr ->
                    ocrPages(
                        pageCount = renderer.pageCount,
                        onPage = { _scanState.value = ScanState.Scanning(it, renderer.pageCount) },
                    ) { index ->
                        val bitmap = renderer.renderPage(index)
                        try {
                            ocr.recognize(bitmap)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }
}
