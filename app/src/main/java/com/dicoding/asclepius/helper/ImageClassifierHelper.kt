package com.dicoding.asclepius.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.dicoding.asclepius.R
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageClassifierHelper(
    private var threshold: Float = 0.1f,
    private var maxResults: Int = 3,
    private val modelName: String = "cancer_classification.tflite",
    val context: Context,
    val classifierListener: ClassifierListener?
) {
    private lateinit var interpreter: Interpreter

    init {
        setupImageClassifier()
    }

    private fun setupImageClassifier() {
        try {
            val model = loadModelFile(modelName)
            interpreter = Interpreter(model)
        } catch (e: Exception) {
            classifierListener?.onError(context.getString(R.string.image_classifier_failed))
            Log.e(TAG, "Error initializing model: ${e.message}")
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val assetFileDescriptor = context.assets.openFd(modelPath)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, assetFileDescriptor.startOffset, assetFileDescriptor.declaredLength)
    }

    fun classifyImage(bitmap: Bitmap) {
        val imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(224, 224, ResizeOp.ResizeMethod.NEAREST_NEIGHBOR))
            .build()

        var tensorImage = TensorImage(DataType.FLOAT32)
        tensorImage.load(bitmap)
        tensorImage = imageProcessor.process(tensorImage)

        val normalizedBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4) // 4 bytes per float
        normalizedBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = tensorImage.buffer.asFloatBuffer()

        while (floatBuffer.hasRemaining()) {
            val pixelValue = floatBuffer.get()
            normalizedBuffer.putFloat(pixelValue / 127.5f - 1.0f)
        }

        val outputBuffer = Array(1) { FloatArray(2) }

        var inferenceTime = SystemClock.uptimeMillis()
        // Run inference
        interpreter.run(normalizedBuffer, outputBuffer)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        classifierListener?.onResults(outputBuffer.toList(), inferenceTime)
    }

    private fun decodeUriToBitmap(uri: Uri): Bitmap {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source)
        } else {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            } ?: throw IllegalArgumentException("Unable to decode image")
        }
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(results: List<FloatArray>?, inferenceTime: Long)
    }

    companion object {
        private const val TAG = "ImageClassifierHelper"
    }
}
