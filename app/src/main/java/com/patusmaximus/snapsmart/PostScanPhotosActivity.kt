package com.patusmaximus.snapsmart

import ImageAnalyzer
import ImageScanResult
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.patusmaximus.snapsmart.adapter.ScannedImagesAdapter
import com.patusmaximus.snapsmart.databinding.ActivityPostscanPhotosBinding
import com.patusmaximus.snapsmart.model.blurModelType

class PostScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPostscanPhotosBinding
    private val recommendedImages = mutableListOf<ImageScanResult>()
    private val notRecommendedImages = mutableListOf<ImageScanResult>()
    private lateinit var imageAnalyzer: ImageAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imageAnalyzer = ImageAnalyzer(this)
        binding = ActivityPostscanPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageScanResults = intent.getParcelableArrayListExtra<ImageScanResult>("imageScanResults") ?: arrayListOf()

        imageScanResults.forEach { result ->
            if (result.label == blurModelType.SHARP) {
                result.selected = true
            }
            if (result.selected) {
                recommendedImages.add(result)
            } else {
                notRecommendedImages.add(result)
            }
        }

        binding.proceedButton.setOnClickListener {
            // Collect selected images from both recommended and not recommended lists
            val selectedImages = (recommendedImages + notRecommendedImages).filter { it.selected }
            val notSelectedImages = (recommendedImages + notRecommendedImages).filter { !it.selected }

            val sourceFolderUri = intent.getStringExtra("selectedFolderUri")?.let { Uri.parse(it) }
            val destinationFolderUri = intent.getStringExtra("selectedMovedFolderUri")?.let { Uri.parse(it) }

            // Call the handleSelectedImages function with the selected images and destination URI
            imageAnalyzer.handleSelectedImages(this, selectedImages, notSelectedImages, destinationFolderUri, sourceFolderUri)

            // Redirect to MainActivity after processing
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        binding.cancelButton.setOnClickListener {
            showCancelConfirmationDialog()
        }

        val recommendedAdapter = ScannedImagesAdapter(this, recommendedImages)
        binding.recommendedRecyclerView.adapter = recommendedAdapter
        binding.recommendedRecyclerView.layoutManager = GridLayoutManager(this, 2)

        val notRecommendedAdapter = ScannedImagesAdapter(this, notRecommendedImages)
        binding.notRecommendedRecyclerView.adapter = notRecommendedAdapter
        binding.notRecommendedRecyclerView.layoutManager = GridLayoutManager(this, 2)
    }

    override fun onBackPressed() {
        super.onBackPressed()
        showCancelConfirmationDialog()
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