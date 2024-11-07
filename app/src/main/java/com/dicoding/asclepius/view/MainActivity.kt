package com.dicoding.asclepius.view

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.dicoding.asclepius.R
import com.dicoding.asclepius.databinding.ActivityMainBinding
import com.dicoding.asclepius.helper.ImageClassifierHelper
import com.yalantis.ucrop.UCrop
import java.io.File

class MainActivity : AppCompatActivity(), ImageClassifierHelper.ClassifierListener {

    private lateinit var binding: ActivityMainBinding
    private var currentImageUri: Uri? = null
    var tempImageUri: Uri? = null
    private lateinit var imageClassifierHelper: ImageClassifierHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageClassifierHelper = ImageClassifierHelper(
            context = this,
            classifierListener = this
        )

        if (savedInstanceState != null) {
            val uriString = savedInstanceState.getString("currentImageUri")
            if (!uriString.isNullOrEmpty()) {
                currentImageUri = Uri.parse(uriString)
                showImage()
            }
        }

        binding.galleryButton.setOnClickListener { startGallery() }
        binding.analyzeButton.setOnClickListener {
            currentImageUri?.let {
                analyzeImage()
            } ?: showToast(getString(R.string.empty_image_warning))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        currentImageUri?.let { uri ->
            outState.putString("currentImageUri", uri.toString())
        }
    }

    private fun startGallery() {
        launcherGallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }


    private val launcherGallery = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            tempImageUri = uri
            showCropOptions(uri)
        } else {
            Log.d("Photo Picker", "No media selected")
        }
    }


    private fun showCropOptions(sourceUri: Uri) {
        val aspectRatios = listOf(
            "1:1" to Pair(1f, 1f),
            "4:3" to Pair(4f, 3f),
            "3:2" to Pair(3f, 2f),
            "16:9" to Pair(16f, 9f)
        )

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Select Aspect Ratio")
        val options = aspectRatios.map { it.first }.toTypedArray()

        builder.setItems(options) { _, which ->
            val (aspectX, aspectY) = aspectRatios[which].second
            startCropWithAspectRatio(sourceUri, aspectX, aspectY)
        }
        builder.show()
    }


    private fun startCropWithAspectRatio(sourceUri: Uri, aspectX: Float, aspectY: Float) {
        val destinationUri = Uri.fromFile(createImageFile())
        UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(aspectX, aspectY)
            .withMaxResultSize(800, 800)
            .start(this)
    }


    private fun createImageFile(): File {
        val imageFile = File(cacheDir, "cropped_image_${System.currentTimeMillis()}.jpg")
        imageFile.createNewFile()
        return imageFile
    }


    @Deprecated("This method has been deprecated in favor of using the Activity Result API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            data?.let {
                val outputUri = UCrop.getOutput(it)
                if (outputUri != null) {
                    currentImageUri = outputUri
                    showImage()
                } else {
                    showToast("Error: Cropped image URI is null")
                }
            } ?: showToast("Error: Data intent is null")
        } else if (resultCode != RESULT_OK && requestCode == UCrop.REQUEST_CROP) {
            showToast("Cropping cancelled or failed")
            Log.d("MainActivity", "Cropping cancelled, using previous image")

        }
    }

    private fun showImage() {
        currentImageUri?.let {
            Log.d("Image URI", "showImage: $it")
            binding.previewImageView.setImageURI(it)
        } ?: Log.d("Image URI", "No image URI to show")
    }

    private fun analyzeImage() {
        currentImageUri?.let { uri ->
            try {
                val source = ImageDecoder.createSource(contentResolver, uri)
                val bitmap = ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
                imageClassifierHelper.classifyImage(bitmap)
            } catch (e: Exception) {
                showToast("Failed to decode image")
                Log.e("MainActivity", "Error decoding image: ${e.message}")
            }
        } ?: showToast(getString(R.string.empty_image_warning))
    }

    override fun onError(error: String) {
        showToast("Error: $error")
        Log.e("MainActivity", error)
    }

    override fun onResults(results: List<FloatArray>?, inferenceTime: Long) {
        if (results != null && results.isNotEmpty()) {
            val labels = listOf("Non Cancer", "Cancer")
            val result = results[0]

            val maxIndex = result.indices.maxByOrNull { result[it] } ?: -1
            val maxLabel = labels[maxIndex]
            val maxConfidence = (result[maxIndex] * 100).toInt()

            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_RESULT, "$maxLabel: $maxConfidence%")
                putExtra(ResultActivity.EXTRA_IMAGE_URI, currentImageUri.toString())
            }
            startActivity(intent)
        } else {
            showToast("No results found")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
