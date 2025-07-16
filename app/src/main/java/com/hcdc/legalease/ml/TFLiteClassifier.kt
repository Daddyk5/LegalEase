package com.hcdc.legalease.ml

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteOrder

class TFLiteClassifier(context: Context) {

    companion object {
        private const val MODEL_NAME = "risk_model.tflite"
        private const val NUM_CLASSES = 3 // ✅ Change if your model uses a different output size
    }

    private val interpreter: Interpreter

    init {
        val modelBuffer = loadModelFile(context, MODEL_NAME)
        interpreter = Interpreter(modelBuffer)
    }

    /**
     * Loads the .tflite model file from the assets folder
     */
    private fun loadModelFile(context: Context, modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Classifies the input using a FloatArray output
     */
    fun classify(inputBuffer: ByteBuffer): FloatArray {
        val output = FloatArray(NUM_CLASSES)
        interpreter.run(inputBuffer, output)
        return output
    }

    /**
     * Alternate method: if your model returns a ByteBuffer
     */
    fun classifyToBuffer(inputBuffer: ByteBuffer): ByteBuffer {
        val outputBuffer = ByteBuffer.allocateDirect(NUM_CLASSES * Float.SIZE_BYTES)
        outputBuffer.order(ByteOrder.nativeOrder())
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()
        return outputBuffer
    }

    /**
     * Always close the interpreter to free resources
     */
    fun close() {
        interpreter.close()
    }
}
