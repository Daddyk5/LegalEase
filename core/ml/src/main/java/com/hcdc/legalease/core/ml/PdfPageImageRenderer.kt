package com.hcdc.legalease.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.engine.PageImageRenderer
import com.hcdc.legalease.core.domain.engine.RenderedPage
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * PDF pages are rendered to a temporary PNG in the app's cache (deleted when the viewer closes);
 * image uploads are shown from their private copy directly.
 */
class PdfPageImageRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PageImageRenderer {

    override suspend fun render(kind: SourceKind, paths: List<String>, page: Int): AppResult<RenderedPage> =
        withContext(ioDispatcher) {
            try {
                when (kind) {
                    SourceKind.IMAGES -> paths.getOrNull(page - 1)
                        ?.let { AppResult.Success(RenderedPage(it, isTemporary = false)) }
                        ?: AppResult.Failure(AppError.DocumentNotFound)
                    SourceKind.PDF -> renderPdfPage(File(paths.single()), page)
                }
            } catch (_: IOException) {
                AppResult.Failure(AppError.UnreadableFile)
            } catch (_: SecurityException) {
                AppResult.Failure(AppError.UnreadableFile)
            }
        }

    private fun renderPdfPage(file: File, page: Int): AppResult<RenderedPage> =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (page !in 1..renderer.pageCount) return AppResult.Failure(AppError.DocumentNotFound)
                val image = writePng(renderer.renderPage(page - 1))
                AppResult.Success(RenderedPage(image.absolutePath, isTemporary = true))
            }
        }

    /** Writes [bitmap] to a fresh cache file and recycles it. */
    private fun writePng(bitmap: Bitmap): File {
        val out = File(File(context.cacheDir, "page_view").apply { mkdirs() }, "p${System.nanoTime()}.png")
        try {
            out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        } finally {
            bitmap.recycle()
        }
        return out
    }

    override fun discard(page: RenderedPage) {
        if (page.isTemporary) File(page.imagePath).delete()
    }

    private companion object {
        const val PNG_QUALITY = 100
    }
}
