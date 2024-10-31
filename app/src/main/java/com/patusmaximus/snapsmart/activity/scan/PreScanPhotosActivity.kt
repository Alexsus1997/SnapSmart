package com.patusmaximus.snapsmart.activity.scan

import ImageAnalyzer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.patusmaximus.snapsmart.databinding.ActivityPrescanPhotosBinding
import com.patusmaximus.snapsmart.imageprocessing.model.UserScanPreferences
import kotlinx.coroutines.launch

class PreScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrescanPhotosBinding
    private var userScanPreferences: UserScanPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivityPrescanPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve User Scan Preferences from Intent
        userScanPreferences = intent.getParcelableExtra("userScanPreferences")

        // Set up button actions
        setBindings()

        // Get selected folder URI
        if (userScanPreferences?.sourceFolder != null) {

            val decodedUri = userScanPreferences?.sourceFolder.toString()
            val displayUri = decodedUri?.substringAfter("raw:/")

            // Set the selected folder URI in the TextView
            binding.selectedFolderTextView.text = displayUri

            lifecycleScope.launch {
                scanAndUpdateUI(Uri.parse(userScanPreferences?.sourceFolder.toString()))
            }
        }
    }

    private fun setBindings() {
        // Buttons and click listeners
        binding.moveImagesCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.selectFolderButton.isEnabled = isChecked
            binding.proceedButton.isEnabled = !isChecked
            binding.proceedButton.setTextColor(if (isChecked) resources.getColor(android.R.color.darker_gray) else resources.getColor(android.R.color.white))
        }

        binding.deleteNotRecommendedPhotosCheckbox.setOnCheckedChangeListener { _, isChecked ->
            binding.proceedButton.isEnabled = isChecked
            userScanPreferences?.deleteNotRecommendedPhotos = isChecked
        }

        binding.selectFolderButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            startActivityForResult(intent, 2)
        }

        binding.proceedButton.setOnClickListener {
            val intent = Intent(this, ScanPhotosActivity::class.java)

            intent.putExtra("userScanPreferences", userScanPreferences)
            startActivity(intent)
        }

        binding.cancelButton.setOnClickListener {
            // Handle cancel button click
            finish()
        }
    }

    private suspend fun scanAndUpdateUI(folderUri: Uri) {
        val imageAnalyzer = ImageAnalyzer(this)
        val scanResult = imageAnalyzer.scanFolder(folderUri)

        // Update the UI based on scan results
        runOnUiThread {
            binding.photoCountTextView.text = scanResult.photoCount.toString()
            binding.processingTimeTextView.text = "${scanResult.estimatedProcessingTime} seconds"

            // Set thumbnails for the first four images
            val thumbnails = scanResult.thumbnails

            if (thumbnails.size > 0) binding.imagePreview1.setImageBitmap(thumbnails[0])
            if (thumbnails.size > 1) binding.imagePreview2.setImageBitmap(thumbnails[1])
            if (thumbnails.size > 2) binding.imagePreview3.setImageBitmap(thumbnails[2])
            if (thumbnails.size > 3) binding.imagePreview4.setImageBitmap(thumbnails[3])
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 2 && resultCode == RESULT_OK) {
            val destinationFolderUri = data?.data

            if (destinationFolderUri != null) {
                // Take persistable permission
                contentResolver.takePersistableUriPermission(
                    destinationFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Compare if the selected moved folder is different from the selected folder
                if (destinationFolderUri.toString() != userScanPreferences?.sourceFolder.toString()) {
                    userScanPreferences?.destinationFolder = destinationFolderUri
                    binding.selectedDestinationFolderTextView.text = destinationFolderUri.toString()
                    binding.destinationFolderGrid.visibility = View.VISIBLE
                    binding.proceedButton.isEnabled = true
                    binding.proceedButton.setTextColor(resources.getColor(android.R.color.white))

                    // Save the moved folder URI in SharedPreferences if needed
                    val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
                    sharedPreferences.edit().putString("selectedMovedFolderUri", destinationFolderUri.toString()).apply()

                } else {
                    // Show a Toast if the selected folder is the same as the original folder
                    Toast.makeText(this, "Selected folder is the same as the original folder.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
