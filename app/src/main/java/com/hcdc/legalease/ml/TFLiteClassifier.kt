package com.hcdc.legalease.ml

import android.content.Context
import android.util.Log
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import com.google.firebase.ml.modeldownloader.DownloadType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.hcdc.legalease.data.Classification

class TFLiteClassifier(private val context: Context) {

    companion object {
        private const val MODEL_NAME = "model_small_int8" // your Firebase model name
        private const val MAX_LEN = 100 // keep in sync with preprocessTextToIds
        private const val NUM_CLASSES = 5
    }

    private var interpreter: Interpreter? = null

    fun load(onReady: (Boolean) -> Unit = {}) {
        val conditions = CustomModelDownloadConditions.Builder()
            // .requireWifi() // optional; remove if you want cellular updates
            .build()

        FirebaseModelDownloader.getInstance()
            .getModel(MODEL_NAME, DownloadType.LOCAL_MODEL /* or LATEST_MODEL */, conditions)
            .addOnSuccessListener { model ->
                val file: File? = model.file
                if (file == null) {
                    Log.e("TFLiteClassifier", "Model file is null")
                    onReady(false)
                    return@addOnSuccessListener
                }
                try {
                    val opts = Interpreter.Options().apply {
                        setUseXNNPACK(true)
                        setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
                    }
                    interpreter = Interpreter(file, opts)
                    Log.d("TFLiteClassifier", "Model loaded from Firebase ML")
                    onReady(true)
                } catch (t: Throwable) {
                    Log.e("TFLiteClassifier", "Interpreter init failed", t)
                    interpreter = null
                    onReady(false)
                }
            }
            .addOnFailureListener {
                Log.e("TFLiteClassifier", "Model download failed", it)
                onReady(false)
            }
    }

    fun convertToByteBuffer(inputIds: IntArray, maxLength: Int = MAX_LEN): ByteBuffer {
        require(inputIds.size <= maxLength) {
            "inputIds length (${inputIds.size}) exceeds maxLength ($maxLength)"
        }
        val buffer = ByteBuffer.allocateDirect(maxLength * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until maxLength) {
            buffer.putInt(if (i < inputIds.size) inputIds[i] else 0) // PAD=0
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Runs inference and returns (label, probability) or null if not ready.
     */
    fun classify(inputBuffer: ByteBuffer): Pair<String, Float>? {
        val itp = interpreter ?: return null
        inputBuffer.rewind()

        val logits = FloatArray(NUM_CLASSES)
        itp.run(inputBuffer, logits)

        // numerically stable softmax
        var maxLogit = Float.NEGATIVE_INFINITY
        for (v in logits) if (v > maxLogit) maxLogit = v
        var sum = 0.0
        val probs = FloatArray(NUM_CLASSES)
        for (i in logits.indices) {
            val e = kotlin.math.exp((logits[i] - maxLogit).toDouble())
            probs[i] = e.toFloat()
            sum += e
        }
        var maxIdx = 0
        for (i in probs.indices) {
            probs[i] = (probs[i] / sum).toFloat()
            if (probs[i] > probs[maxIdx]) maxIdx = i
        }

        val label = Classification.LABELS.getOrNull(maxIdx) ?: Classification.ENFORCEABLE.label
        return label to probs[maxIdx]
    }

    fun close() {
        try {
            interpreter?.close()
        } catch (_: Throwable) { }
        interpreter = null
    }
}
