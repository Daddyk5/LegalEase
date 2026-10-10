package com.hcdc.legalease.core.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.hcdc.legalease.core.ml.ocr.OcrLine
import com.hcdc.legalease.core.ml.ocr.OcrQuality
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/** Recognised text for one page and its quality score in [0, 1]. */
data class OcrPage(val text: String, val quality: Float)

/** Thin wrapper over ML Kit's on-device Latin text recognizer. Close it when done. */
class OcrEngine : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrPage {
        val result = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        val lines = result.textBlocks.flatMap { block -> block.lines.map { OcrLine(it.text, it.confidence) } }
        return OcrPage(result.text.trim(), OcrQuality.score(lines))
    }

    override fun close() = recognizer.close()
}
