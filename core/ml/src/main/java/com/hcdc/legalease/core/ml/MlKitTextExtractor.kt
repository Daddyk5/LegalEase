package com.hcdc.legalease.core.ml

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
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
import com.hcdc.legalease.core.ml.ocr.PagePreprocessor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject

/** OCR over app-private copies of an uploaded PDF (one file) or set of images (one file per page). */
class MlKitTextExtractor @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val tracer: PerformanceTracer,
) : TextExtractor {

    override suspend fun extract(
        kind: SourceKind,
        paths: List<String>,
        onPage: suspend (page: Int, pageCount: Int) -> Unit,
    ): AppResult<ExtractedText> = withContext(ioDispatcher) {
        try {
            when (kind) {
                SourceKind.PDF -> AppResult.Success(extractPdf(File(paths.single()), onPage))
                SourceKind.IMAGES -> AppResult.Success(extractImages(paths.map(::File), onPage))
                // Generated contracts store their exact text when created; there is nothing to read.
                SourceKind.GENERATED -> AppResult.Failure(AppError.DocumentNotFound)
            }
        } catch (_: IOException) {
            AppResult.Failure(AppError.UnreadableFile)
        } catch (_: SecurityException) {
            // PdfRenderer throws this for password-protected PDFs.
            AppResult.Failure(AppError.UnreadableFile)
        }
    }

    /** Renders, preprocesses, and reads one page at a time; each bitmap is recycled before the next. */
    private suspend fun extractPdf(file: File, onPage: suspend (Int, Int) -> Unit): ExtractedText =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                OcrEngine().use { ocr ->
                    val pageCount = renderer.pageCount
                    ocrPages(pageCount, onPage = { onPage(it, pageCount) }) { index ->
                        tracedPage(SourceKind.PDF) { ocr.readPrepared(renderer.renderPage(index)) }
                    }
                }
            }
        }

    private suspend fun extractImages(files: List<File>, onPage: suspend (Int, Int) -> Unit): ExtractedText =
        OcrEngine().use { ocr ->
            ocrPages(files.size, onPage = { onPage(it, files.size) }) { index ->
                tracedPage(SourceKind.IMAGES) {
                    try {
                        ocr.readPrepared(PagePreprocessor.decodeImage(files[index]))
                    } catch (_: IOException) {
                        UNREADABLE_PAGE // One undecodable image is an unreadable page, not a failed document.
                    }
                }
            }
        }

    /** Preprocesses [page] (recycling it), then runs OCR and recycles the prepared bitmap. */
    private suspend fun OcrEngine.readPrepared(page: Bitmap): OcrPage {
        val prepared = PagePreprocessor.prepare(page)
        return try {
            recognize(prepared)
        } finally {
            prepared.recycle()
        }
    }

    /** Times one page of OCR. Records only the source kind, character count, and quality, never the text. */
    private suspend inline fun tracedPage(kind: SourceKind, read: () -> OcrPage): OcrPage =
        tracer.trace(TraceNames.OCR_PAGE) { trace ->
            trace.putAttribute("source_kind", kind.name)
            read().also {
                trace.putMetric("chars", it.text.length.toLong())
                trace.putMetric("quality_pct", (it.quality * PERCENT).toLong())
            }
        }

    private companion object {
        const val PERCENT = 100
        val UNREADABLE_PAGE = OcrPage("", 0f)
    }
}
