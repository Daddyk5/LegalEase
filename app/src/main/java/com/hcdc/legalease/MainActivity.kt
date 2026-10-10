package com.hcdc.legalease

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.hcdc.legalease.core.designsystem.theme.LegalEaseTheme
import com.hcdc.legalease.feature.settings.AppLockGate
import com.hcdc.legalease.feature.settings.PrivacyNoticeScreen
import com.hcdc.legalease.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

/** A FragmentActivity because BiometricPrompt (the optional app lock) needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LegalEaseTheme {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)

                    ) {
                        val launch by viewModel.launchState.collectAsStateWithLifecycle()
                        when (val state = launch) {
                            // Blank until settings load, so neither the app nor the notice flashes.
                            LaunchState.Loading ->
                                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                            LaunchState.NeedsPrivacyNotice -> PrivacyNoticeScreen(
                                onAccept = viewModel::acceptPrivacyNotice,
                                onExit = ::finish,
                            )
                            is LaunchState.Ready -> AppLockGate(enabled = state.appLockEnabled) {
                                val navController = rememberNavController()
                                AppNavHost(navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }
}
