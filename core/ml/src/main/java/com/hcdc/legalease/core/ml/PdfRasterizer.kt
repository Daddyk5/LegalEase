package com.hcdc.legalease.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import androidx.core.graphics.createBitmap
import kotlin.math.max
import kotlin.math.roundToInt

/** Longest bitmap side used for OCR: sharp enough for ML Kit, small enough to avoid OOM. */
const val MAX_RENDER_LONG_SIDE_PX = 2000

/** Pixel size of a rendered page. */
data class RenderSize(val width: Int, val height: Int)

/**
 * Scales a page (in PDF points) so its longest side is exactly [maxLongSide] pixels,
 * preserving aspect ratio. Small pages are scaled up and large pages scaled down.
 */
fun renderSizeFor(pageWidth: Int, pageHeight: Int, maxLongSide: Int = MAX_RENDER_LONG_SIDE_PX): RenderSize {
    require(pageWidth > 0 && pageHeight > 0) { "Page has no area: ${pageWidth}x$pageHeight" }
    val scale = maxLongSide.toDouble() / max(pageWidth, pageHeight)
    return RenderSize(
        width = (pageWidth * scale).roundToInt().coerceIn(1, maxLongSide),
        height = (pageHeight * scale).roundToInt().coerceIn(1, maxLongSide),
    )
}

/** Renders one page onto a white bitmap. The caller owns the bitmap and must recycle it. */
fun PdfRenderer.renderPage(index: Int): Bitmap = openPage(index).use { page ->
    val size = renderSizeFor(page.width, page.height)
    createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).also { bitmap ->
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    }
}
