package com.hcdc.legalease.ui.components.bottomSheet.LoginSheet

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LoginSheetViewModel : ViewModel() {

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    fun onEmailChanged(newValue: String) {
        _email.value = newValue
    }

    fun onPasswordChanged(newValue: String) {
        _password.value = newValue
    }
}
