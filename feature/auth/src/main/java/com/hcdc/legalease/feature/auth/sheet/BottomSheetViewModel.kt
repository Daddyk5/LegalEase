package com.hcdc.legalease.feature.auth.sheet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BottomSheetViewModel : ViewModel() {
    private val _isSheetVisible = MutableStateFlow(false)
    val isSheetVisible: StateFlow<Boolean> = _isSheetVisible.asStateFlow()

    private val _sheetContent = MutableStateFlow<BottomSheetContent?>(null)
    val sheetContent: StateFlow<BottomSheetContent?> = _sheetContent.asStateFlow()

    fun show(content: BottomSheetContent) {
        _sheetContent.value = content
        _isSheetVisible.value = true
    }

    fun hide() {
        _isSheetVisible.value = false
        _sheetContent.value = null
    }
}
