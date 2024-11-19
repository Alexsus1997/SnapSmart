package com.patusmaximus.snapsmart.activity.bucketize

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.patusmaximus.snapsmart.R
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
        sections = groupBucketsByGranularity(buckets, granularity)

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

    private fun groupBucketsByGranularity(
        buckets: List<ImageBucketizer.Bucket>,
        granularity: BucketGranularity
    ): List<BucketSectionAdapter.Section> {
        val groupedBuckets = when (granularity) {
            BucketGranularity.Day -> {
                // Group by month (e.g., "YYYY-MM")
                buckets.groupBy { it.label.substring(0, 7) }
            }
            BucketGranularity.Month -> {
                // Group by year (e.g., "YYYY")
                buckets.groupBy { it.label.substring(0, 4) }
            }
            BucketGranularity.Year -> {
                // All buckets in one group labeled as "All Buckets"
                mapOf("All Buckets" to buckets)
            }
        }

        // Convert grouped buckets into sections
        return groupedBuckets.map { (title, groupedBuckets) ->
            BucketSectionAdapter.Section(
                title = title,
                buckets = groupedBuckets
            )
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

        // Run the image distribution process
        val (bucketCount, imagesProcessed) = ImageBucketizer().distributeImagesToBuckets(
            context = this,
            destinationFolder = destinationFolder,
            buckets = sections.flatMap { it.buckets },
            keepOriginalImages = userPreferences.keepOriginalFiles
        ) { progress, bucketName, imagesInBucket ->
            Log.d("PostBucketizeActivity", "Processed bucket: $bucketName with $imagesInBucket images ($progress% complete)")
        }

        // Show summary dialog
        showSummaryDialog(bucketCount, imagesProcessed, userPreferences.granularity, destinationFolder)
    }

    private fun showSummaryDialog(bucketCount: Int, imagesProcessed: Int, granularity: BucketGranularity?, destinationFolder: Uri) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_bucket_bucket_summary)

        // Populate dialog views
        dialog.findViewById<TextView>(R.id.valueBucketsCreated).text = bucketCount.toString()
        dialog.findViewById<TextView>(R.id.valueImagesProcessed).text = imagesProcessed.toString()
        dialog.findViewById<TextView>(R.id.valueGranularity).text = granularity?.name ?: "N/A"

        val destinationTextView = dialog.findViewById<TextView>(R.id.valueDestinationFolder)
        destinationTextView.text = destinationFolder.toString()

        // Handle folder opening
        destinationTextView.setOnClickListener {
            try {
                // Convert the tree URI to a file path
                val folderPath = convertTreeUriToFilePath(destinationFolder)

                if (folderPath != null) {
                    // Use ACTION_VIEW with file:// URI
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(Uri.parse("file://$folderPath"), "*/*")
                        setPackage("com.sec.android.app.myfiles") // Samsung My Files package
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }

                    // Start the intent
                    startActivity(intent)
                } else {
                    Log.w("PostBucketizeActivity", "Failed to convert URI to file path.")
                }
            } catch (e: Exception) {
                Log.e("PostBucketizeActivity", "Error opening folder with My Files: ${e.message}")
            }
        }

        dialog.findViewById<Button>(R.id.summaryOkButton).setOnClickListener {
            dialog.dismiss()
            finish() // Return to the main activity
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
