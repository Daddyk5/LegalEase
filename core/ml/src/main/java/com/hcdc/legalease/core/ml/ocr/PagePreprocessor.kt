package com.hcdc.legalease.core.ml.ocr

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.hcdc.legalease.core.ml.MAX_RENDER_LONG_SIDE_PX
import com.hcdc.legalease.core.ml.renderSizeFor
import java.io.File

/** Android adapter for [ImageOps]: bitmaps in, a cleaned-up grayscale bitmap out. */
object PagePreprocessor {

    private const val MARGIN_PADDING_PX = 24

    /** Grayscale, contrast stretch, deskew, and margin crop. Recycles [source]. */
    fun prepare(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val argb = IntArray(width * height)
        source.getPixels(argb, 0, width, 0, 0, width, height)
        source.recycle()
        val prepared = ImageOps.prepareForOcr(GrayImage.fromArgb(argb, width, height), MARGIN_PADDING_PX)
        return Bitmap.createBitmap(prepared.toArgb(), prepared.width, prepared.height, Bitmap.Config.ARGB_8888)
    }

    /**
     * Decodes a photo or scan, applying its EXIF orientation and downscaling so the longest side is
     * at most [MAX_RENDER_LONG_SIDE_PX], as for PDF pages.
     */
    fun decodeImage(file: File): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val size = info.size
            if (maxOf(size.width, size.height) > MAX_RENDER_LONG_SIDE_PX) {
                val target = renderSizeFor(size.width, size.height)
                decoder.setTargetSize(target.width, target.height)
            }
        }
}
