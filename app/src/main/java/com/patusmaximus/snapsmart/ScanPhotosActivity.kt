package com.patusmaximus.snapsmart

import ImageAnalyzer
import ImageScanResult
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.patusmaximus.snapsmart.databinding.ActivityScanPhotosBinding
import kotlinx.coroutines.launch

class ScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanPhotosBinding
    private lateinit var imageAnalyzer: ImageAnalyzer
    private var selectedFolderUri: String? = null
    private var selectedMovedFolderUri: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScanPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageAnalyzer = ImageAnalyzer(this)

        selectedFolderUri = intent.getStringExtra("selectedFolderUri")
        selectedMovedFolderUri = intent.getStringExtra("selectedMovedFolderUri")
        selectedFolderUri?.let {
            startImageProcessing(Uri.parse(it))
        }

        binding.cancelButton.setOnClickListener {
            showCancelConfirmationDialog()
        }
    }

    private fun showCancelConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Cancel Scanning")
            .setMessage("Are you sure you want to cancel the scanning process?")
            .setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                finish() // End the activity if the user confirms
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Dismiss the dialog if the user declines
            }
            .create()
            .show()
    }

    private fun startImageProcessing(folderUri: Uri) {
        lifecycleScope.launch {
            val scanResult = imageAnalyzer.scanFolder(folderUri)
            val totalImages = scanResult.totalImages

            val imageScanResults = imageAnalyzer.processImagesWithProgress(folderUri) { processedCount, imageName ->
                val overallProgress = (processedCount * 100) / totalImages
                binding.horizontalProgressBar.progress = overallProgress
                binding.circularProgressIndicator.progress = overallProgress
                binding.dynamicImageNameTextView.text = imageName
                binding.progressPercentageText.text = "$overallProgress%"
            }

            val intent = Intent(this@ScanPhotosActivity, PostScanPhotosActivity::class.java)
            intent.putParcelableArrayListExtra("imageScanResults", ArrayList(imageScanResults))

            selectedFolderUri?.let { intent.putExtra("selectedFolderUri", it) }
            selectedMovedFolderUri?.let { intent.putExtra("selectedMovedFolderUri", it) }

            startActivity(intent)
        }
    }
}
