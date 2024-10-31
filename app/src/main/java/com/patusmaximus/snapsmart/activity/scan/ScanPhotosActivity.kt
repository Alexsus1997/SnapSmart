package com.patusmaximus.snapsmart.activity.scan

import ImageAnalyzer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.patusmaximus.snapsmart.databinding.ActivityScanScanPhotosBinding
import com.patusmaximus.snapsmart.imageprocessing.model.UserScanPreferences
import kotlinx.coroutines.launch

class ScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanScanPhotosBinding
    private lateinit var imageAnalyzer: ImageAnalyzer
    private var userScanPreferences: UserScanPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanScanPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ImageAnalyzer
        imageAnalyzer = ImageAnalyzer(this)

        // Retrieve User Scan Preferences from Intent
        userScanPreferences = intent.getParcelableExtra("userScanPreferences")

        // Set bindings
        setBindings()

        // Initialize Image Processing
        userScanPreferences?.sourceFolder?.let {
            startImageProcessing(it)
        }
    }

    private fun setBindings() {
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
            intent.putExtra("userScanPreferences", userScanPreferences)
            intent.putParcelableArrayListExtra("imagesScanResult", ArrayList(imageScanResults))

            startActivity(intent)
        }
    }
}
