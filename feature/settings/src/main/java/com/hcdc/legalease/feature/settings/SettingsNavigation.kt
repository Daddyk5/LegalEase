package com.hcdc.legalease.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import kotlinx.serialization.Serializable

/** Privacy & data: cloud backup consent, retention, app lock, export, and account deletion. */
@Serializable
data object SettingsRoute

/** The privacy notice, opened from Privacy & data. */
@Serializable
data object PrivacyNoticeRoute

fun NavGraphBuilder.settingsScreens(
    onOpenNotice: () -> Unit,
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
) {
    composable<SettingsRoute> { SettingsScreen(onOpenNotice = onOpenNotice, onSignedOut = onSignedOut) }
    composable<PrivacyNoticeRoute> { PrivacyNoticeScreen(onAccept = onBack) }
}
