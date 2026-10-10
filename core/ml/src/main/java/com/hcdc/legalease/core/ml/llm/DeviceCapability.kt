package com.hcdc.legalease.core.ml.llm

import android.app.ActivityManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Picks the model tier this device can run, silently, from its memory and free storage. */
@Singleton
class DeviceCapability @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun tier(): ModelTier? {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return tierFor(info.totalMem)
    }

    fun freeStorageBytes(): Long = context.filesDir.usableSpace

    companion object {
        private const val GIB = 1L shl 30

        /** Phones sold as "6 GB" report about 5.5 GiB because the kernel reserves memory. */
        const val PRIMARY_MIN_RAM_BYTES = 11 * GIB / 2

        const val LITE_MIN_RAM_BYTES = 5 * GIB / 2

        /**
         * Peak storage during provisioning: the download, its encrypted copy, and later the
         * temporary decrypted file at load time.
         */
        const val STORAGE_FACTOR = 2.2

        fun tierFor(totalRamBytes: Long): ModelTier? = when {
            totalRamBytes >= PRIMARY_MIN_RAM_BYTES -> ModelTier.PRIMARY
            totalRamBytes >= LITE_MIN_RAM_BYTES -> ModelTier.LITE
            else -> null
        }
    }
}
