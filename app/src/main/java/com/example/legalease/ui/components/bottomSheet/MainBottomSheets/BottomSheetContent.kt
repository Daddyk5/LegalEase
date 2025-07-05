package com.example.legalease.ui.components.bottomSheet.MainBottomSheets

sealed class BottomSheetContent {
    object Login : BottomSheetContent()
    object Register : BottomSheetContent()
    object ForgotPassword : BottomSheetContent()
}