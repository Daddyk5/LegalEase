package com.hcdc.legalease.ui.screens.result

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ResultViewmodelFactory(
    private val application: Application,
    private val apiKey: String? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ResultViewmodel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ResultViewmodel(application, apiKey) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
