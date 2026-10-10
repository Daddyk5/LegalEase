package com.hcdc.legalease.core.data.config

import android.util.Log
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.hcdc.legalease.core.data.R
import com.hcdc.legalease.core.domain.repository.FeatureFlags
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Feature flags and kill switches from Remote Config, with bundled defaults for first launch and offline use. */
@Singleton
internal class RemoteConfigFeatureFlags @Inject constructor(
    private val remoteConfig: FirebaseRemoteConfig,
) : FeatureFlagsRepository {

    private val _flags = MutableStateFlow(FeatureFlags.DEFAULT)
    override val flags: StateFlow<FeatureFlags> = _flags.asStateFlow()

    private var listening = false

    override suspend fun refresh() {
        try {
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults).await()
            _flags.value = read()
            remoteConfig.fetchAndActivate().await()
        } catch (e: FirebaseRemoteConfigException) {
            Log.w(TAG, "Remote Config fetch failed; keeping current flags", e)
        }
        _flags.value = read()
        listenForUpdates()
    }

    /** Kill switches take effect without waiting for the next launch. */
    private fun listenForUpdates() {
        if (listening) return
        listening = true
        remoteConfig.addOnConfigUpdateListener(object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                remoteConfig.activate().addOnSuccessListener { _flags.value = read() }
            }

            override fun onError(error: FirebaseRemoteConfigException) {
                Log.w(TAG, "Real-time config update failed: ${error.code}")
            }
        })
    }

    private fun read() = FeatureFlags(
        generatorEnabled = remoteConfig.getBoolean(KEY_ENABLE_GENERATOR),
        llmEnabled = remoteConfig.getBoolean(KEY_ENABLE_LLM),
        confidenceThresholdText = remoteConfig.getString(KEY_CONFIDENCE_THRESHOLD_TEXT),
    )

    companion object {
        private const val TAG = "RemoteConfigFlags"
        const val KEY_ENABLE_GENERATOR = "enable_generator"
        const val KEY_ENABLE_LLM = "enable_llm"
        const val KEY_CONFIDENCE_THRESHOLD_TEXT = "confidence_threshold_text"
    }
}
