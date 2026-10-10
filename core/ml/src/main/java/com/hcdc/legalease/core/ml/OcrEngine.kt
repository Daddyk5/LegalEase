package com.hcdc.legalease.core.ml

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/** Thin wrapper over ML Kit's on-device Latin text recognizer. Close it when done. */
class OcrEngine : Closeable {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): String =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text.trim()

    suspend fun recognize(context: Context, uri: Uri): String =
        recognizer.process(InputImage.fromFilePath(context, uri)).await().text.trim()

    override fun close() = recognizer.close()
}
