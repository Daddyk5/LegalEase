package com.hcdc.legalease.core.data.telemetry

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.hcdc.legalease.core.domain.telemetry.AnalyticsEvent
import com.hcdc.legalease.core.domain.telemetry.AnalyticsTracker
import com.hcdc.legalease.core.domain.telemetry.CrashReporter
import com.hcdc.legalease.core.domain.telemetry.PerformanceTrace
import com.hcdc.legalease.core.domain.telemetry.PerformanceTracer
import javax.inject.Inject

internal class FirebaseAnalyticsTracker @Inject constructor(
    private val analytics: FirebaseAnalytics,
) : AnalyticsTracker {

    override fun log(event: AnalyticsEvent) {
        val bundle = Bundle()
        event.params.forEach { (key, value) ->
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                is Double -> bundle.putDouble(key, value)
                else -> bundle.putString(key, value.toString())
            }
        }
        analytics.logEvent(event.name, bundle)
    }
}

internal class FirebasePerformanceTracer @Inject constructor(
    private val performance: FirebasePerformance,
) : PerformanceTracer {

    override fun start(name: String): PerformanceTrace {
        val trace = performance.newTrace(name).apply { start() }
        return object : PerformanceTrace {
            override fun putAttribute(name: String, value: String) = trace.putAttribute(name, value)
            override fun putMetric(name: String, value: Long) = trace.putMetric(name, value)
            override fun stop() = trace.stop()
        }
    }
}

internal class FirebaseCrashReporter @Inject constructor(
    private val crashlytics: FirebaseCrashlytics,
) : CrashReporter {
    override fun recordNonFatal(throwable: Throwable) = crashlytics.recordException(throwable)
}
