package com.hcdc.legalease.ui.screens.upload_image

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hcdc.legalease.R
import com.hcdc.legalease.appContainer
import com.hcdc.legalease.ml.OcrEngine
import com.hcdc.legalease.ml.ocrPages
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

class ImageViewModel(application: Application) : AndroidViewModel(application) {

    private val documentRepository = application.appContainer.documentRepository

    private val _selectedImages = MutableStateFlow<List<Uri>>(emptyList())
    val selectedImages: StateFlow<List<Uri>> = _selectedImages.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _navigateToResult = Channel<String>(Channel.BUFFERED)

    /** Emits the analysis ID once a scan finishes. */
    val navigateToResult: Flow<String> = _navigateToResult.receiveAsFlow()

    private var scanJob: Job? = null

    fun setSelectedImages(uris: List<Uri>) {
        _selectedImages.value = uris
    }

    fun dismissError() {
        if (_scanState.value is ScanState.Failed) _scanState.value = ScanState.Idle
    }

    /** Reads every selected image in selection order; each image is one page. */
    fun scan() {
        val uris = _selectedImages.value
        if (uris.isEmpty() || scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            _scanState.value = ScanState.Scanning(currentPage = 0, totalPages = uris.size)
            val app = getApplication<Application>()
            val result = withContext(Dispatchers.IO) {
                OcrEngine().use { ocr ->
                    ocrPages(
                        pageCount = uris.size,
                        onPage = { _scanState.value = ScanState.Scanning(it, uris.size) },
                    ) { index ->
                        try {
                            ocr.recognize(app, uris[index])
                        } catch (_: IOException) {
                            ""
                        }
                    }
                }
            }
            if (!result.hasText) {
                _scanState.value = ScanState.Failed(ScanFailure.NO_TEXT_FOUND)
            } else {
                val name = app.getString(R.string.scan_default_images_name)
                val id = documentRepository.save(name, result.pages, result.unreadablePages)
                _navigateToResult.send(id)
                _scanState.value = ScanState.Idle
            }
        }
    }
}
