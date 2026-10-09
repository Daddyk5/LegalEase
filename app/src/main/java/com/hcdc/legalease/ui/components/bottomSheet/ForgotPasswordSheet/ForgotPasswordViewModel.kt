package com.hcdc.legalease.ui.components.bottomSheet.ForgotPasswordSheet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ForgotPasswordViewModel : ViewModel(){

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    fun onEmailChanged(newValue: String) {
        _email.value = newValue
    }

}
