package com.patusmaximus.snapsmart.activity.bucketize

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.activity.MainActivity
import com.patusmaximus.snapsmart.adapter.BucketSectionAdapter
import com.patusmaximus.snapsmart.backend.ImageBucketizer
import com.patusmaximus.snapsmart.backend.model.BucketGranularity
import com.patusmaximus.snapsmart.backend.model.UserBucketPreferences
import com.patusmaximus.snapsmart.databinding.ActivityBucketizePostbucketizePhotosBinding

class PostBucketizeActivity : AppCompatActivity() {
    private lateinit var binding: ActivityBucketizePostbucketizePhotosBinding
    private lateinit var sectionAdapter: BucketSectionAdapter
    private var sections: List<BucketSectionAdapter.Section> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBucketizePostbucketizePhotosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Retrieve buckets and user preferences from the intent
        val buckets: ArrayList<ImageBucketizer.Bucket> =
            intent.getParcelableArrayListExtra("userBuckets") ?: arrayListOf()
        val userBucketPreferences: UserBucketPreferences? = intent.getParcelableExtra("userBucketPreferences")
        val granularity = userBucketPreferences?.granularity
            ?: BucketGranularity.Day // Default to "Day" if granularity is not provided

        // Group buckets by granularity
        sections = ImageBucketizer().groupBucketsByGranularity(buckets, granularity)

        // Set up the RecyclerView for sections
        sectionAdapter = BucketSectionAdapter(this, sections) { bucket ->
            openBucketView(bucket)
        }
        binding.sectionsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.sectionsRecyclerView.adapter = sectionAdapter

        // Handle the cancel button
        binding.cancelButton.setOnClickListener {
            finish() // Close the activity
        }

        // Handle the proceed button
        binding.proceedButton.setOnClickListener {
            saveSelectedPhotos()
        }
    }

    private fun openBucketView(bucket: ImageBucketizer.Bucket) {
        val intent = Intent(this, BucketViewActivity::class.java)
        intent.putExtra("bucket", bucket)
        startActivity(intent)
    }

    private fun saveSelectedPhotos() {
        val userPreferences = intent.getParcelableExtra<UserBucketPreferences>("userBucketPreferences")
        val destinationFolder = userPreferences?.destinationFolder
            ?: throw IllegalArgumentException("Destination folder not set")

        // Create and show progress dialog
        val progressDialog = Dialog(this).apply {
            setContentView(R.layout.dialog_image_processing)
            setCancelable(false)
            show()
        }

        val circularProgressIndicator = progressDialog.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.circularProgressIndicator)
        val horizontalProgressBar = progressDialog.findViewById<ProgressBar>(R.id.horizontalProgressBar)
        val progressPercentageText = progressDialog.findViewById<TextView>(R.id.progressPercentageText)
        val dynamicImageNameTextView = progressDialog.findViewById<TextView>(R.id.dynamicImageNameTextView)

        // Run the image distribution process in a background thread
        Thread {
            try {
                val (bucketCount, imagesProcessed) = ImageBucketizer().distributeImagesToBuckets(
                    context = this,
                    destinationFolder = destinationFolder,
                    buckets = sections.flatMap { it.buckets },
                    keepOriginalImages = userPreferences.keepOriginalFiles
                ) { progress, bucketName, imagesInBucket ->
                    runOnUiThread {
                        // Update progress dialog
                        circularProgressIndicator.progress = progress
                        horizontalProgressBar.progress = progress
                        progressPercentageText.text = "$progress%"
                        dynamicImageNameTextView.text = "Processing: $bucketName ($imagesInBucket images)"
                    }
                }

                // Dismiss dialog and show summary once processing is done
                runOnUiThread {
                    progressDialog.dismiss()
                    showSummaryDialog(bucketCount, imagesProcessed, userPreferences.granularity, destinationFolder)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Log.e("PostBucketizeActivity", "Error processing images: ${e.message}")
                }
            }
        }.start()
    }

    // Display the summary dialog
    private fun showSummaryDialog(bucketCount: Int, imagesProcessed: Int, granularity: BucketGranularity?, destinationFolder: Uri) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_bucket_bucket_summary)

        // Set dialog width to match parent
        dialog.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        // Populate dialog views
        dialog.findViewById<TextView>(R.id.valueBucketsCreated).text = bucketCount.toString()
        dialog.findViewById<TextView>(R.id.valueImagesProcessed).text = imagesProcessed.toString()
        dialog.findViewById<TextView>(R.id.valueGranularity).text = granularity?.name ?: "N/A"

        val destinationTextView = dialog.findViewById<TextView>(R.id.valueDestinationFolder)
        destinationTextView.text = destinationFolder.toString()

        // Handle folder opening
        destinationTextView.setOnClickListener {
            try {
                val folderPath = convertTreeUriToFilePath(destinationFolder)
                if (folderPath != null) {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse("file://$folderPath"), "*/*")
                        setPackage("com.sec.android.app.myfiles")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    startActivity(intent)
                } else {
                    Log.w("PostBucketizeActivity", "Failed to convert URI to file path.")
                }
            } catch (e: Exception) {
                Log.e("PostBucketizeActivity", "Error opening folder: ${e.message}")
            }
        }

        dialog.findViewById<Button>(R.id.summaryOkButton).setOnClickListener {
            dialog.dismiss()
            val intent = Intent(this, MainActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            finish()
        }

        dialog.show()
    }


    private fun convertTreeUriToFilePath(uri: Uri): String? {
        // Extract the document ID from the URI
        val documentId = DocumentsContract.getTreeDocumentId(uri)

        // Parse the document ID (e.g., "primary:DCIM/SnapSmart/Buckets")
        val split = documentId.split(":")
        val type = split[0]
        val path = split.getOrNull(1)

        // Build the file path
        return if (type.equals("primary", ignoreCase = true) && path != null) {
            "/storage/emulated/0/$path" // Path for primary storage
        } else {
            null // Unsupported or secondary storage
        }
    }
}
