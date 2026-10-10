package com.hcdc.legalease.core.ml

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.engine.TextExtractor
import com.hcdc.legalease.core.domain.model.ExtractedText
import com.hcdc.legalease.core.domain.model.SourceKind
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import com.hcdc.legalease.core.domain.telemetry.PerformanceTracer
import com.hcdc.legalease.core.domain.telemetry.TraceNames
import com.hcdc.legalease.core.domain.telemetry.trace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** OCR over app-private copies of an uploaded PDF (one file) or set of images (one file per page). */
class MlKitTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val tracer: PerformanceTracer,
) : TextExtractor {

    override suspend fun extract(
        kind: SourceKind,
        paths: List<String>,
        onPage: suspend (page: Int, pageCount: Int) -> Unit,
    ): AppResult<ExtractedText> = withContext(ioDispatcher) {
        try {
            AppResult.Success(
                when (kind) {
                    SourceKind.PDF -> extractPdf(File(paths.single()), onPage)
                    SourceKind.IMAGES -> extractImages(paths.map(::File), onPage)
                }
            )
        } catch (_: IOException) {
            AppResult.Failure(AppError.UnreadableFile)
        } catch (_: SecurityException) {
            // PdfRenderer throws this for password-protected PDFs.
            AppResult.Failure(AppError.UnreadableFile)
        }
    }

    /** Renders and reads one page at a time, recycling each bitmap before the next is created. */
    private suspend fun extractPdf(file: File, onPage: suspend (Int, Int) -> Unit): ExtractedText =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                OcrEngine().use { ocr ->
                    val pageCount = renderer.pageCount
                    ocrPages(pageCount, onPage = { onPage(it, pageCount) }) { index ->
                        tracedPage(SourceKind.PDF) {
                            val bitmap = renderer.renderPage(index)
                            try {
                                ocr.recognize(bitmap)
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        }

    private suspend fun extractImages(files: List<File>, onPage: suspend (Int, Int) -> Unit): ExtractedText =
        OcrEngine().use { ocr ->
            ocrPages(files.size, onPage = { onPage(it, files.size) }) { index ->
                tracedPage(SourceKind.IMAGES) {
                    try {
                        ocr.recognize(context, Uri.fromFile(files[index]))
                    } catch (_: IOException) {
                        "" // One undecodable image is an unreadable page, not a failed document.
                    }
                }
            }
        }

    /** Times one page of OCR. Records only the source kind and character count, never the text. */
    private suspend inline fun tracedPage(kind: SourceKind, read: () -> String): String =
        tracer.trace(TraceNames.OCR_PAGE) { trace ->
            trace.putAttribute("source_kind", kind.name)
            read().also { trace.putMetric("chars", it.length.toLong()) }
        }
}
