package com.hcdc.legalease

import android.os.StrictMode

/**
 * Debug builds flag disk and network access on the main thread, and leaked closeables, in Logcat.
 * LeakCanary (debug only) watches for leaked activities, fragments, and ViewModels on its own.
 */
internal fun installStrictMode() {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
            .penaltyLog()
            .build()
    )
    StrictMode.setVmPolicy(
        StrictMode.VmPolicy.Builder()
            .detectLeakedClosableObjects()
            .detectLeakedSqlLiteObjects()
            .detectActivityLeaks()
            .penaltyLog()
            .build()
    )
}
