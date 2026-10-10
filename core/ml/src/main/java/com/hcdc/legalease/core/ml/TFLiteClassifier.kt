package com.hcdc.legalease.core.ml

import android.content.Context
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions
import com.google.firebase.ml.modeldownloader.DownloadType
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader
import com.hcdc.legalease.core.domain.di.DefaultDispatcher
import com.hcdc.legalease.core.domain.di.IoDispatcher
import com.hcdc.legalease.core.domain.engine.ContractClassifier
import com.hcdc.legalease.core.domain.model.Classification
import com.hcdc.legalease.core.domain.model.Prediction
import com.hcdc.legalease.core.domain.result.AppError
import com.hcdc.legalease.core.domain.result.AppResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * App-scoped offline classifier backed by the Firebase-hosted TFLite model.
 *
 * The model is fetched with [DownloadType.LOCAL_MODEL_UPDATE_IN_BACKGROUND]: a cached copy is
 * used immediately and a newer one is downloaded for the next launch. Callers suspend until the
 * interpreter is ready; inference runs on the default dispatcher.
 */
@Singleton
class TFLiteClassifier @Inject constructor(
    @ApplicationContext private val context: Context,
    @DefaultDispatcher private val inferenceDispatcher: CoroutineDispatcher,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ContractClassifier {

    private val loadMutex = Mutex()
    private val inferenceMutex = Mutex()

    @Volatile private var loaded: LoadedModel? = null

    override suspend fun prepare(): AppResult<Unit> = guard { awaitReady() }

    override suspend fun classify(text: String): AppResult<Prediction> = guard {
        val model = awaitReady()
        withContext(inferenceDispatcher) {
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

    /** Maps classifier failures to [AppError]; anything else is a programming error and propagates. */
    private inline fun <T> guard(block: () -> T): AppResult<T> = try {
        AppResult.Success(block())
    } catch (_: ClassifierException.ModelUnavailable) {
        AppResult.Failure(AppError.ModelUnavailable)
    } catch (_: ClassifierException) {
        AppResult.Failure(AppError.ModelIncompatible)
    }

    private suspend fun awaitReady(): LoadedModel = loaded ?: loadMutex.withLock {
        loaded ?: withContext(ioDispatcher) {
            val vocab = loadVocab(context)
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
