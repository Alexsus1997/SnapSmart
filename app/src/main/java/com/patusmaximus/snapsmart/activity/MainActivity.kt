package com.patusmaximus.snapsmart.activity

import com.patusmaximus.snapsmart.activity.scan.PreScanPhotosActivity
import com.patusmaximus.snapsmart.activity.settings.SettingsActivity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.patusmaximus.snapsmart.activity.bucketize.PreBucketizeActivity
import com.patusmaximus.snapsmart.backend.model.UserBucketPreferences
import com.patusmaximus.snapsmart.databinding.ActivityMainBinding
import com.patusmaximus.snapsmart.backend.model.UserScanPreferences

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_CODE_PICK_FOLDER_SCAN = 1
    private val REQUEST_CODE_PICK_FOLDER_BUCKET = 2

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
        val savedUri = sharedPreferences.getString("selectedFolderUri", null)

        if (savedUri != null) {
            val uri = Uri.parse(savedUri)
            // Use the saved URI without asking for folder access again
            println("Using saved folder URI: $uri")
        }

        setBindings()
    }

    private fun setBindings() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up button actions
        binding.scanPhotosButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER_SCAN)
        }

        binding.bucketizePhotosButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER_BUCKET)
        }

        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            val sourceFolderUri = data?.data
            if (sourceFolderUri != null) {
                // Take persistable permission
                contentResolver.takePersistableUriPermission(
                    sourceFolderUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Save the URI in SharedPreferences
                val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
                sharedPreferences.edit().putString("selectedFolderUri", sourceFolderUri.toString()).apply()

                // Use the saved URI in future access
                println("Persisted folder URI: $sourceFolderUri")

                // Launch Scan or Bucket activity based on the request code
                var intent: Intent? = null
                if (requestCode == REQUEST_CODE_PICK_FOLDER_SCAN)
                {
                    intent = Intent(this, PreScanPhotosActivity::class.java)
                    val userScanPreferences = UserScanPreferences(sourceFolderUri)
                    intent.putExtra("userScanPreferences", userScanPreferences)
                }
                else
                {
                    intent = Intent(this, PreBucketizeActivity::class.java)
                    val userBucketPreferences = UserBucketPreferences(sourceFolderUri)
                    intent.putExtra("userBucketPreferences", userBucketPreferences)
                }

                startActivity(intent)
            }
        }
    }
}
