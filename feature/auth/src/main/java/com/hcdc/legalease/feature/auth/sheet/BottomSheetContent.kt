package com.hcdc.legalease.feature.auth.sheet

sealed class BottomSheetContent {
    object Login : BottomSheetContent()
    object Register : BottomSheetContent()
    object ForgotPassword : BottomSheetContent()
}
