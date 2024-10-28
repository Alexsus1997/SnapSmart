package com.patusmaximus.snapsmart

import ImageAnalyzer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.patusmaximus.snapsmart.databinding.ActivityPrescanPhotosBinding
import kotlinx.coroutines.launch

class PreScanPhotosActivity : AppCompatActivity() {
    private lateinit var binding: ActivityPrescanPhotosBinding
    private var selectedFolderUri: String? = null
    private var selectedDestinationFolder: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivityPrescanPhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get selected folder URI
        selectedFolderUri = intent.getStringExtra("selectedFolderUri")
        if (selectedFolderUri != null) {
            val decodedUri = Uri.decode(selectedFolderUri)
            val displayUri = decodedUri?.substringAfter("raw:/")

            binding.selectedFolderTextView.text = displayUri

            lifecycleScope.launch {
                scanAndUpdateUI(Uri.parse(selectedFolderUri))
            }
        }

        // Buttons and click listeners
        binding.moveImagesCheckBox.setOnCheckedChangeListener { _, isChecked ->
            binding.selectFolderButton.isEnabled = isChecked
            binding.proceedButton.isEnabled = !isChecked
        }

        binding.selectFolderButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            startActivityForResult(intent, 2)
        }

        binding.proceedButton.setOnClickListener {
            val intent = Intent(this, ScanPhotosActivity::class.java)

            intent.putExtra("selectedFolderUri", selectedFolderUri)
            intent.putExtra("selectedMovedFolderUri", selectedDestinationFolder)
            startActivity(intent)
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
            val uri = data?.data

            if (uri != null) {
                // Take persistable permission
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Compare if the selected moved folder is different from the selected folder
                if (uri.toString() != selectedFolderUri) {
                    selectedDestinationFolder = uri.toString()
                    binding.selectedDestinationFolderTextView.text = uri.toString()
                    binding.destinationFolderGrid.visibility = View.VISIBLE
                    binding.proceedButton.isEnabled = true

                    // Save the moved folder URI in SharedPreferences if needed
                    val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
                    sharedPreferences.edit().putString("selectedMovedFolderUri", selectedDestinationFolder).apply()

                } else {
                    // Show a Toast if the selected folder is the same as the original folder
                    Toast.makeText(this, "Selected folder is the same as the original folder.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
