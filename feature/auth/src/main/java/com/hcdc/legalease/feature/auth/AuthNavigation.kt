package com.hcdc.legalease.feature.auth

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

@Serializable data object IntroRoute

fun NavGraphBuilder.introScreen(onLoginSuccess: () -> Unit) {
    composable<IntroRoute> { IntroScreen(onLoginSuccess = onLoginSuccess) }
}
