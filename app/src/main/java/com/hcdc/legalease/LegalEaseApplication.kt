package com.hcdc.legalease

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hcdc.legalease.core.data.work.BackgroundWork
import com.hcdc.legalease.core.domain.di.ApplicationScope
import com.hcdc.legalease.core.domain.repository.FeatureFlagsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class LegalEaseApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var backgroundWork: BackgroundWork

    @Inject lateinit var featureFlags: FeatureFlagsRepository

    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Before any Firebase call that App Check protects.
        installAppCheck()
        backgroundWork.schedule()
        applicationScope.launch { featureFlags.refresh() }
    }
}
