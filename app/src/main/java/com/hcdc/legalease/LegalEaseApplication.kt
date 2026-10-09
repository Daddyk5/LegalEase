package com.hcdc.legalease

import android.app.Application
import android.content.Context

class LegalEaseApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.classifier.warmUp()
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as LegalEaseApplication).container
