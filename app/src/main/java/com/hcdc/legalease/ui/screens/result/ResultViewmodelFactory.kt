package com.hcdc.legalease.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hcdc.legalease.AppContainer

class ResultViewmodelFactory(
    private val container: AppContainer,
    private val analysisId: String,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ResultViewmodel::class.java)) { "Unknown ViewModel class $modelClass" }
        @Suppress("UNCHECKED_CAST")
        return ResultViewmodel(analysisId, container.documentRepository, container.classifier) as T
    }
}
