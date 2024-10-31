package com.patusmaximus.snapsmart.activity.scan

import ImageAnalyzer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.patusmaximus.snapsmart.activity.MainActivity
import com.patusmaximus.snapsmart.adapter.ScannedImagesAdapter
import com.patusmaximus.snapsmart.databinding.ActivityPostscanPhotosBinding
import com.patusmaximus.snapsmart.imageprocessing.model.ImageScanResult
import com.patusmaximus.snapsmart.imageprocessing.model.UserScanPreferences
import com.patusmaximus.snapsmart.model.blurModelType

class PostScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPostscanPhotosBinding
    private val recommendedImages = mutableListOf<ImageScanResult>()
    private val notRecommendedImages = mutableListOf<ImageScanResult>()
    private var userScanPreference: UserScanPreferences? = null
    private var imageScanResults: List<ImageScanResult>? = null
    private lateinit var imageAnalyzer: ImageAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imageAnalyzer = ImageAnalyzer(this)
        binding = ActivityPostscanPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageScanResults = intent.getParcelableArrayListExtra<ImageScanResult>("imagesScanResult")
        userScanPreference = intent.getParcelableExtra("userScanPreferences")

        imageScanResults?.forEach { result ->
            if (result.label == blurModelType.SHARP) {
                result.selected = true
            }
            if (result.selected) {
                recommendedImages.add(result)
            } else {
                notRecommendedImages.add(result)
            }
        }

        setBindings()
        setAdapters()
    }

    override fun onBackPressed() {
        super.onBackPressed()
        showCancelConfirmationDialog()
    }

    private fun setBindings()
    {
        binding.proceedButton.setOnClickListener {
            // Collect selected images from both recommended and not recommended lists
            val selectedImages = (recommendedImages + notRecommendedImages).filter { it.selected }
            val notSelectedImages = (recommendedImages + notRecommendedImages).filter { !it.selected }

            // Call the handleSelectedImages function process images
            imageAnalyzer.handleSelectedImages(this, selectedImages, notSelectedImages, userScanPreference)

            // Redirect to MainActivity after processing
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        binding.cancelButton.setOnClickListener {
            showCancelConfirmationDialog()
        }
    }

    private fun setAdapters()
    {
        val recommendedAdapter = ScannedImagesAdapter(this, recommendedImages)
        binding.recommendedRecyclerView.adapter = recommendedAdapter
        binding.recommendedRecyclerView.layoutManager = GridLayoutManager(this, 2)

        val notRecommendedAdapter = ScannedImagesAdapter(this, notRecommendedImages)
        binding.notRecommendedRecyclerView.adapter = notRecommendedAdapter
        binding.notRecommendedRecyclerView.layoutManager = GridLayoutManager(this, 2)
    }

    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Scanning")
            .setMessage("Are you sure you want to cancel quit scanning?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(this, MainActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                finish() // End the activity if the user confirms
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss the dialog if the user declines
            }
            .create()
            .show()
    }
}