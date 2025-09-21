package com.hcdc.legalease.ml

import android.content.Context
import android.util.Log
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteClassifier(private val context: Context) {

    companion object {
        private const val MODEL_NAME = "FinalTfliteModel" // Must match the name in Firebase ML
        private const val NUM_CLASSES = 5 // Void, Voidable, Unenforceable, Rescissible, Enforceable
        private val LABELS = listOf("Void", "Voidable", "Unenforceable", "Rescissible", "Enforceable")
    }

    private var interpreter: Interpreter? = null

    /**
     * Download the model from Firebase ML and initialize Interpreter
     */
    fun loadModel(onReady: (Boolean) -> Unit) {
        val conditions = CustomModelDownloadConditions.Builder()
            .requireWifi() // download only on Wi-Fi
            .build()

        FirebaseModelDownloader.getInstance()
            .getModel(
                MODEL_NAME,
                com.google.firebase.ml.modeldownloader.DownloadType.LOCAL_MODEL, // ✅ fully qualified
                conditions
            )
            .addOnSuccessListener { model ->
                val file: File? = model.file
                if (file != null) {
                    interpreter = Interpreter(file)
                    Log.d("TFLiteClassifier", "Model loaded successfully from Firebase ML")
                    onReady(true)
                } else {
                    Log.e("TFLiteClassifier", "Model file is null")
                    onReady(false)
                }
            }
            .addOnFailureListener { e ->
                Log.e("TFLiteClassifier", "Model download failed", e)
                onReady(false)
            }
    }

    /**
     * Run classification and return (label, confidence)
     */
    fun classify(inputBuffer: ByteBuffer): Pair<String, Float>? {
        val output = FloatArray(NUM_CLASSES)
        interpreter?.run(inputBuffer, output)

        // Softmax normalization
        val expScores = output.map { kotlin.math.exp(it.toDouble()) }
        val sumExp = expScores.sum()
        val probs = expScores.map { (it / sumExp).toFloat() }

        val maxIndex = probs.indices.maxByOrNull { probs[it] } ?: return null
        return Pair(LABELS[maxIndex], probs[maxIndex])
    }

    /**
     * Convert IntArray input IDs into ByteBuffer (for TFLite)
     */
    fun convertToByteBuffer(inputIds: IntArray, maxLength: Int = 100): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(maxLength * 4)
        buffer.order(ByteOrder.nativeOrder())
        for (i in 0 until maxLength) {
            buffer.putInt(if (i < inputIds.size) inputIds[i] else 0)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Close interpreter
     */
    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
