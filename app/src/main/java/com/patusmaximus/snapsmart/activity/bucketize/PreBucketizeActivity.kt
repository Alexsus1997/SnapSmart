package com.patusmaximus.snapsmart.activity.bucketize

import ImageAnalyzer
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.patusmaximus.snapsmart.backend.Utilities
import com.patusmaximus.snapsmart.backend.model.BucketGranularity
import com.patusmaximus.snapsmart.backend.model.UserBucketPreferences
import com.patusmaximus.snapsmart.databinding.ActivityBucketizePrebucketizePhotosBinding
import kotlinx.coroutines.launch

class PreBucketizeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBucketizePrebucketizePhotosBinding
    private var userBucketPreferences: UserBucketPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize binding
        binding = ActivityBucketizePrebucketizePhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve User Scan Preferences from Intent
        userBucketPreferences = intent.getParcelableExtra("userBucketPreferences")

        // Set up button actions
        setBindings()

        // Get selected folder URI
        if (userBucketPreferences?.sourceFolder != null) {

            val decodedUri = userBucketPreferences?.sourceFolder.toString()
            val displayUri = Utilities().decodeAndFormatUri(decodedUri)

            // Set the selected folder URI in the TextView
            binding.selectedFolderTextView.text = displayUri

            lifecycleScope.launch {
                scanAndUpdateUI(Uri.parse(userBucketPreferences?.sourceFolder.toString()))
            }
        }
    }

    // Set Buttons and click listeners
    private fun setBindings() {
        // Set up the SeekBar for Bucket Granularity
        binding.granularitySeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val granularity = when (progress) {
                    0 -> BucketGranularity.Day
                    1 -> BucketGranularity.Month
                    2 -> BucketGranularity.Year
                    else -> BucketGranularity.Month
                }

                // Update the TextView with the selected granularity
                binding.granularityTextView.text = granularity.toString()

                // Update user preferences or logic based on granularity
                userBucketPreferences?.granularity = granularity
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Not needed
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Not needed
            }
        })

        // Select folder button
        binding.selectFolderButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            startActivityForResult(intent, 2)
        }

        // Proceed button
        binding.proceedButton.setOnClickListener {
            val intent = Intent(this, BucketizeActivity::class.java)
            intent.putExtra("userBucketPreferences", userBucketPreferences)
            startActivity(intent)
        }

        // Cancel button
        binding.cancelButton.setOnClickListener {
            finish()
        }

        // Keep original images checkbox
        binding.keepOriginalImagesCheckbox.setOnCheckedChangeListener { _, isChecked ->
            userBucketPreferences?.keepOriginalFiles = isChecked
        }
    }

    private suspend fun scanAndUpdateUI(folderUri: Uri) {
        val imageAnalyzer = ImageAnalyzer(this)
        val scanResult = imageAnalyzer.scanFolder(folderUri)

        runOnUiThread {
            binding.photoCountTextView.text = scanResult.photoCount.toString()
            binding.processingTimeTextView.text = String.format("%.2f seconds", scanResult.estimatedProcessingTime)

            val thumbnails = scanResult.thumbnails

            // Ensure thumbnails are loaded with proper aspect ratio correction
            if (thumbnails.isNotEmpty()) {
                binding.imagePreview1.setImageBitmap(thumbnails[0])
                if (thumbnails.size > 1) binding.imagePreview2.setImageBitmap(thumbnails[1])
                if (thumbnails.size > 2) binding.imagePreview3.setImageBitmap(thumbnails[2])
                if (thumbnails.size > 3) binding.imagePreview4.setImageBitmap(thumbnails[3])
            }
        }
    }

    private fun toggleProceedButton(flag: Boolean)
    {
        binding.proceedButton.isEnabled = flag
        binding.proceedButton.setTextColor(if (flag) resources.getColor(android.R.color.white) else resources.getColor(android.R.color.darker_gray))
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)

        // Folder selection result
        if (requestCode == 2 && resultCode == RESULT_OK)
        {
            val destinationFolderUri = data?.data

            if (destinationFolderUri != null)
            {
                // Take persistable permission
                contentResolver.takePersistableUriPermission(
                    destinationFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Compare if the selected moved folder is different from the selected folder
                if (destinationFolderUri.toString() != userBucketPreferences?.sourceFolder.toString()) {
                    userBucketPreferences?.destinationFolder = destinationFolderUri
                    binding.selectedDestinationFolderTextView.text = Utilities().decodeAndFormatUri(destinationFolderUri.toString())
                    binding.destinationFolderGrid.visibility = View.VISIBLE
                    toggleProceedButton(true)

                    // Save the moved folder URI in SharedPreferences if needed
                    val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
                    sharedPreferences.edit().putString("selectedMovedFolderUri", destinationFolderUri.toString()).apply()

                } else
                {
                    // Show a Toast if the selected folder is the same as the original folder
                    Toast.makeText(this, "Selected folder is the same as the original folder.", Toast.LENGTH_SHORT).show()
                    toggleProceedButton(false)
                }
            }
        }
    }
}
