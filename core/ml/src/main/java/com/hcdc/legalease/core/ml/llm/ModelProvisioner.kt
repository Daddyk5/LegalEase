package com.hcdc.legalease.core.ml.llm

import android.content.Context
import android.util.Log
import com.google.android.play.core.assetpacks.AssetPackManager
import com.google.android.play.core.assetpacks.AssetPackManagerFactory
import com.google.android.play.core.assetpacks.model.AssetPackStatus
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.engine.OfflineModelProvisioner
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Which model this device would use, and whether it is ready. */
interface ModelAvailability {
    fun specForDevice(): ModelSpec?
    suspend fun isProvisioned(): Boolean
}

/**
 * Gets the model for this device's tier onto the device, verified and encrypted:
 * 1. Play Store installs: the `llm_pack` fast-follow asset pack. Its plaintext copy is removed
 *    after import.
 * 2. Debug and sideloaded builds: a one-time download from Cloud Storage `models/` (the worker
 *    runs on unmetered networks only).
 *
 * Every byte is checked against the SHA-256 pinned in [ModelCatalog]; a mismatch discards the file.
 */
@Singleton
class ModelProvisioner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capability: DeviceCapability,
    private val store: ModelStore,
    private val storage: FirebaseStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : OfflineModelProvisioner, ModelAvailability {

    /** The spec for this device, or null if it cannot run any tier. */
    override fun specForDevice(): ModelSpec? = capability.tier()?.let(ModelCatalog::spec)

    override suspend fun isProvisioned(): Boolean = specForDevice()?.let(store::isReady) ?: false

    override suspend fun provision(onProgress: (Float) -> Unit): AppResult<Unit> = withContext(ioDispatcher) {
        val spec = specForDevice() ?: return@withContext AppResult.Failure(AppError.Unexpected(DEVICE_UNSUPPORTED))
        if (!spec.isPinned) return@withContext AppResult.Failure(AppError.Unexpected(MODEL_NOT_PINNED))
        if (store.isReady(spec)) return@withContext AppResult.Success(Unit)
        if (capability.freeStorageBytes() < spec.sizeBytes * DeviceCapability.STORAGE_FACTOR) {
            return@withContext AppResult.Failure(AppError.StorageFull)
        }
        when (val fromPack = fromAssetPack(spec)) {
            PackOutcome.NotAvailable -> fromCloudStorage(spec, onProgress)
            PackOutcome.StillDownloading -> AppResult.Failure(AppError.Network) // retried by the worker
            is PackOutcome.Done -> fromPack.result
        }
    }

    private sealed interface PackOutcome {
        data object NotAvailable : PackOutcome
        data object StillDownloading : PackOutcome
        data class Done(val result: AppResult<Unit>) : PackOutcome
    }

    private suspend fun fromAssetPack(spec: ModelSpec): PackOutcome {
        val manager = AssetPackManagerFactory.getInstance(context)
        val assetsPath = manager.getPackLocation(ModelCatalog.ASSET_PACK)?.assetsPath()
            // Not installed from Play, or the fast-follow download has not finished yet.
            ?: return if (packStatus(manager) in DOWNLOADING) {
                PackOutcome.StillDownloading
            } else {
                PackOutcome.NotAvailable
            }
        val file = File(assetsPath, "${ModelCatalog.REMOTE_FOLDER}/${spec.tier.fileName}")
        if (!file.exists()) return PackOutcome.NotAvailable
        val result = importFile(spec, file)
        if (result is AppResult.Success) {
            // The pack's plaintext copy is no longer needed once the encrypted copy exists.
            runCatching { manager.removePack(ModelCatalog.ASSET_PACK) }
        }
        return PackOutcome.Done(result)
    }

    private suspend fun packStatus(manager: AssetPackManager): Int? = try {
        manager.getPackStates(listOf(ModelCatalog.ASSET_PACK)).await().packStates()[ModelCatalog.ASSET_PACK]?.status()
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Log.i(TAG, "Asset pack unavailable (${e.javaClass.simpleName})")
        null
    }

    private suspend fun fromCloudStorage(spec: ModelSpec, onProgress: (Float) -> Unit): AppResult<Unit> {
        val partial = File(File(context.noBackupFilesDir, ".dl").apply { mkdirs() }, spec.tier.fileName)
        return try {
            storage.reference.child("${ModelCatalog.REMOTE_FOLDER}/${spec.tier.fileName}")
                .getFile(partial)
                .addOnProgressListener { snapshot ->
                    val total = snapshot.totalByteCount
                    if (total > 0) onProgress(snapshot.bytesTransferred.toFloat() / total)
                }
                .await()
            importFile(spec, partial)
        } catch (e: StorageException) {
            Log.w(TAG, "Model download failed: ${e.errorCode}")
            AppResult.Failure(AppError.Network)
        } finally {
            partial.delete()
        }
    }

    private fun importFile(spec: ModelSpec, file: File): AppResult<Unit> = try {
        when (val result = file.inputStream().buffered().use { store.import(spec, it) }) {
            ModelStore.ImportResult.Imported -> AppResult.Success(Unit)
            is ModelStore.ImportResult.HashMismatch -> {
                Log.w(TAG, "Model hash mismatch; discarded")
                AppResult.Failure(AppError.Unexpected(HASH_MISMATCH))
            }
        }
    } catch (e: IOException) {
        Log.w(TAG, "Model import failed", e)
        AppResult.Failure(AppError.StorageFull.takeIf { e.message?.contains("ENOSPC") == true } ?: AppError.Network)
    }

    companion object {
        private const val TAG = "ModelProvisioner"
        const val DEVICE_UNSUPPORTED = "device_unsupported"
        const val MODEL_NOT_PINNED = "model_not_pinned"
        const val HASH_MISMATCH = "hash_mismatch"
        private val DOWNLOADING = setOf(
            AssetPackStatus.PENDING,
            AssetPackStatus.DOWNLOADING,
            AssetPackStatus.TRANSFERRING,
            AssetPackStatus.WAITING_FOR_WIFI,
        )
    }
}
