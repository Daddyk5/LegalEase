package com.hcdc.legalease.ml

import android.content.Context
import android.util.Log
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import com.hcdc.legalease.data.Classification
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Classifier output: the top class, its probability, and the full distribution in [Classification] order. */
data class Prediction(
    val classification: Classification,
    val probability: Float,
    val probabilities: List<Float>,
)

/**
 * App-scoped offline classifier backed by the Firebase-hosted TFLite model.
 *
 * Create exactly one instance per process. The model is fetched with
 * [DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND]: a cached copy is used immediately and
 * a newer one is downloaded for the next launch. Callers suspend in [classify] until the
 * interpreter is ready; inference runs on [inferenceDispatcher].
 */
class TFLiteClassifier(
    context: Context,
    private val appScope: CoroutineScope,
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val appContext = context.applicationContext
    private val loadMutex = Mutex()
    private val inferenceMutex = Mutex()

    @Volatile private var loaded: LoadedModel? = null

    /** Starts loading in the background so the first [classify] call does not pay for it. */
    fun warmUp() {
        appScope.launch {
            try {
                awaitReady()
            } catch (e: ClassifierException) {
                // Not fatal: classify() retries the load and reports the error to the user.
                Log.w(TAG, "Classifier warm-up failed", e)
            }
        }
    }

    /**
     * Classifies [text].
     *
     * @throws ClassifierException if the model or vocabulary cannot be loaded.
     */
    suspend fun classify(text: String): Prediction {
        val model = awaitReady()
        return withContext(inferenceDispatcher) {
            val ids = preprocessTextToIds(text, model.vocab, model.inputLength)
            val input = encodeInput(ids, model.inputType)
            val output = Array(1) { FloatArray(NUM_CLASSES) }
            inferenceMutex.withLock { model.interpreter.run(input, output) }

            val probabilities = toProbabilities(output[0])
            val topIndex = probabilities.indices.maxBy { probabilities[it] }
            val classification = checkNotNull(Classification.fromIndexOrNull(topIndex))
            Prediction(classification, probabilities[topIndex], probabilities.toList())
        }
    }

    private suspend fun awaitReady(): LoadedModel = loaded ?: loadMutex.withLock {
        loaded ?: withContext(ioDispatcher) {
            val vocab = loadVocab(appContext)
            val interpreter = createInterpreter(fetchModelFile())
            describe(interpreter, vocab)
        }.also { loaded = it }
    }

    // Firebase ML is deprecated (shutdown 2027-06-15); hosting moves to Cloud Storage for Firebase.
    @Suppress("DEPRECATION")
    private suspend fun fetchModelFile(): File = suspendCancellableCoroutine { cont ->
        val conditions = CustomModelDownloadConditions.Builder().build()
        FirebaseModelDownloader.getInstance()
            .getModel(MODEL_NAME, DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND, conditions)
            .addOnSuccessListener { model ->
                val file = model.file
                if (file != null) {
                    cont.resume(file)
                } else {
                    cont.resumeWithException(ClassifierException.ModelUnavailable("Model file is missing"))
                }
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(ClassifierException.ModelUnavailable("Model download failed", e))
            }
    }

    private fun createInterpreter(file: File): Interpreter {
        val options = Interpreter.Options().apply {
            setUseXNNPACK(true)
            setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(MAX_THREADS))
        }
        return try {
            Interpreter(file, options)
        } catch (e: IllegalArgumentException) {
            throw ClassifierException.IncompatibleModel("Model file could not be opened", e)
        }
    }

    /** Validates tensor shapes once at load time so inference never fails on a shape mismatch. */
    private fun describe(interpreter: Interpreter, vocab: Map<String, Int>): LoadedModel {
        val input = interpreter.getInputTensor(0)
        val output = interpreter.getOutputTensor(0)
        val inputShape = input.shape()
        val outputShape = output.shape()

        val compatible = inputShape.size == 2 && inputShape[0] == 1 &&
            input.dataType() in SUPPORTED_INPUT_TYPES &&
            outputShape.contentEquals(intArrayOf(1, NUM_CLASSES)) &&
            output.dataType() == DataType.FLOAT32
        if (!compatible) {
            interpreter.close()
            throw ClassifierException.IncompatibleModel(
                "Unexpected tensors: input ${inputShape.contentToString()} ${input.dataType()}, " +
                    "output ${outputShape.contentToString()} ${output.dataType()}"
            )
        }
        return LoadedModel(interpreter, vocab, inputLength = inputShape[1], inputType = input.dataType())
    }

    private class LoadedModel(
        val interpreter: Interpreter,
        val vocab: Map<String, Int>,
        val inputLength: Int,
        val inputType: DataType,
    )

    companion object {
        private const val TAG = "TFLiteClassifier"
        private const val MODEL_NAME = "model_small_int8"
        private const val MAX_THREADS = 4
        private val NUM_CLASSES = Classification.entries.size
        private val SUPPORTED_INPUT_TYPES = setOf(DataType.INT32, DataType.FLOAT32)

        /** Encodes token IDs as a [1, ids.size] tensor of [type]. */
        internal fun encodeInput(ids: IntArray, type: DataType): ByteBuffer {
            val buffer = ByteBuffer.allocateDirect(ids.size * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
            for (id in ids) {
                if (type == DataType.FLOAT32) buffer.putFloat(id.toFloat()) else buffer.putInt(id)
            }
            buffer.rewind()
            return buffer
        }
    }
}
