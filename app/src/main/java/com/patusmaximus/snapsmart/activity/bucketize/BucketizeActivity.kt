package com.patusmaximus.snapsmart.activity.bucketize

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.patusmaximus.snapsmart.backend.ImageBucketizer
import com.patusmaximus.snapsmart.backend.model.UserBucketPreferences
import com.patusmaximus.snapsmart.databinding.ActivityBucketizeBucketizePhotosBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BucketizeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBucketizeBucketizePhotosBinding
    private lateinit var imageBucketizer: ImageBucketizer
    private var userBucketPreferences: UserBucketPreferences? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBucketizeBucketizePhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize ImageBucketizer
        imageBucketizer = ImageBucketizer()

        // Retrieve User Scan Preferences from Intent
        userBucketPreferences = intent.getParcelableExtra("userBucketPreferences")

        // Set bindings
        setBindings()

        // Initialize Image Processing
        userBucketPreferences?.sourceFolder?.let {
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
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Process images and update progress
                var buckets = imageBucketizer.processFolderToBuckets(
                    context = this@BucketizeActivity,
                    folderUri = folderUri,
                    granularity = userBucketPreferences?.granularity,
                    callback = object : ImageBucketizer.ProgressCallback {
                        override fun onProgressUpdate(progress: Int, imageTitle: String?) {
                            lifecycleScope.launch {
                                updateProgressUI(progress, imageTitle)
                            }
                        }
                    }
                )

                // Navigate to PostBucketizeActivity when complete
                withContext(Dispatchers.Main) {
                    val intent = Intent(this@BucketizeActivity, PostBucketizeActivity::class.java)
                    intent.putExtra("userBucketPreferences", userBucketPreferences)
                    intent.putParcelableArrayListExtra("userBuckets", ArrayList(buckets))
                    startActivity(intent)
                    finish() // Close this activity
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BucketizeActivity, "Error processing images: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    private suspend fun updateProgressUI(progress: Int, imageTitle: String?) = withContext(Dispatchers.Main) {
        binding.circularProgressIndicator.progress = progress
        binding.horizontalProgressBar.progress = progress
        binding.progressPercentageText.text = "$progress%"
        binding.dynamicImageNameTextView.text = imageTitle ?: "Image Name"
    }
}
