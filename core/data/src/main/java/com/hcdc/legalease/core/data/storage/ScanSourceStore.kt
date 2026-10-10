package com.hcdc.legalease.core.data.storage

import android.content.Context
import android.net.Uri
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-private copies of uploaded files, kept only until OCR has run.
 *
 * Picker URI grants can expire before background analysis starts, so files are copied into
 * `filesDir/scans/{analysisId}/` first. The directory is excluded from backups.
 */
@Singleton
class ScanSourceStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun dir(analysisId: String) = File(File(context.filesDir, ROOT), analysisId)

    /** Copies [uris] in order. On failure nothing is left behind. */
    fun copy(analysisId: String, uris: List<String>): AppResult<List<String>> {
        val dir = dir(analysisId).apply { mkdirs() }
        return try {
            AppResult.Success(
                uris.mapIndexed { index, uri ->
                    val target = File(dir, "page_%03d".format(index))
                    val input = context.contentResolver.openInputStream(Uri.parse(uri))
                        ?: throw FileNotFoundException("No content for source $index")
                    input.use { source -> target.outputStream().use { source.copyTo(it) } }
                    target.absolutePath
                }
            )
        } catch (e: IOException) {
            dir.deleteRecursively()
            AppResult.Failure(if (e.isNoSpace()) AppError.StorageFull else AppError.UnreadableFile)
        } catch (_: SecurityException) {
            dir.deleteRecursively()
            AppResult.Failure(AppError.UnreadableFile)
        }
    }

    fun paths(analysisId: String): List<String> =
        dir(analysisId).listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.map { it.absolutePath }.orEmpty()

    fun delete(analysisId: String) {
        dir(analysisId).deleteRecursively()
    }

    private fun IOException.isNoSpace(): Boolean = message?.contains("ENOSPC") == true

    private companion object {
        const val ROOT = "scans"
    }
}
