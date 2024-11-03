package com.patusmaximus.snapsmart.activity.scan

import ImageAnalyzer
import ScannedImagesAdapter
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.activity.MainActivity
import com.patusmaximus.snapsmart.databinding.ActivityScanPostscanPhotosBinding
import com.patusmaximus.snapsmart.imageprocessing.model.ImageScanResult
import com.patusmaximus.snapsmart.imageprocessing.model.UserScanPreferences
import com.patusmaximus.snapsmart.model.blurModelType

class PostScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanPostscanPhotosBinding
    private val recommendedImages = mutableListOf<ImageScanResult>()
    private val notRecommendedImages = mutableListOf<ImageScanResult>()
    private var userScanPreference: UserScanPreferences? = null
    private var imageScanResults: List<ImageScanResult>? = null
    private lateinit var imageAnalyzer: ImageAnalyzer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        imageAnalyzer = ImageAnalyzer(this)
        binding = ActivityScanPostscanPhotosBinding.inflate(layoutInflater)
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

            // Display summary of scan
            showSummaryDialog(selectedImages.size + notSelectedImages.size, selectedImages.size, notSelectedImages.size, userScanPreference?.sourceFolder, userScanPreference?.destinationFolder)
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

    private fun showSummaryDialog(
        processedCount: Int,
        movedCount: Int,
        deletedCount: Int,
        sourceFolderUri: Uri?,
        targetFolderUri: Uri?
    ) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_scan_scan_summary)

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        dialog.window?.setGravity(android.view.Gravity.CENTER)


        val processedValue: TextView = dialog.findViewById(R.id.processedImagesValue)
        val movedValue: TextView = dialog.findViewById(R.id.movedImagesValue)
        val deletedValue: TextView = dialog.findViewById(R.id.deletedImagesValue)
        val sourceFolderValue: TextView = dialog.findViewById(R.id.sourceFolderValue)
        val targetFolderValue: TextView = dialog.findViewById(R.id.targetFolderValue)
        val okButton: Button = dialog.findViewById(R.id.okButton)

        // Set text for each dynamic value
        processedValue.text = processedCount.toString()
        movedValue.text = movedCount.toString()
        deletedValue.text = deletedCount.toString()

        sourceFolderValue.text = sourceFolderUri?.toString() ?: "N/A"
        targetFolderValue.text = targetFolderUri?.toString() ?: "N/A"

        // Dismiss dialog and navigate to MainActivity on OK click
        okButton.setOnClickListener {
            dialog.dismiss()

            // Redirect to MainActivity
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        dialog.show()
    }
}