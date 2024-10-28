package com.patusmaximus.snapsmart

import ImageAnalyzer
import ImageScanResult
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.patusmaximus.snapsmart.adapter.ScannedImagesAdapter
import com.patusmaximus.snapsmart.databinding.ActivityPostscanPhotosBinding

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
            if (result.label == "SHARP") {
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

            // Get the destination folder URI if provided
            val destinationFolderUri = intent.getStringExtra("selectedMovedFolderUri")?.let { Uri.parse(it) }

            // Call the handleSelectedImages function with the selected images and destination URI
            imageAnalyzer.handleSelectedImages(selectedImages, destinationFolderUri)
        }

        val recommendedAdapter = ScannedImagesAdapter(this, recommendedImages)
        binding.recommendedRecyclerView.adapter = recommendedAdapter
        binding.recommendedRecyclerView.layoutManager = GridLayoutManager(this, 2)

        val notRecommendedAdapter = ScannedImagesAdapter(this, notRecommendedImages)
        binding.notRecommendedRecyclerView.adapter = notRecommendedAdapter
        binding.notRecommendedRecyclerView.layoutManager = GridLayoutManager(this, 2)


    }
}
