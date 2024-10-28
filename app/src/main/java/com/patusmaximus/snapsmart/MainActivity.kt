package com.patusmaximus.snapsmart

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.patusmaximus.snapsmart.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val REQUEST_CODE_PICK_FOLDER = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
        val savedUri = sharedPreferences.getString("selectedFolderUri", null)

        if (savedUri != null) {
            val uri = Uri.parse(savedUri)
            // Use the saved URI without asking for folder access again
            println("Using saved folder URI: $uri")
        }

        initializeUI()
    }

    private fun initializeUI() {
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up button actions
        binding.scanPhotosButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            intent.addCategory(Intent.CATEGORY_DEFAULT)
            startActivityForResult(intent, REQUEST_CODE_PICK_FOLDER)
        }

        binding.viewCategoriesButton.setOnClickListener {
            val intent = Intent(this, ViewCategoriesActivity::class.java)
            startActivity(intent)
        }

        binding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_CODE_PICK_FOLDER && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                // Take persistable permission
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Save the URI in SharedPreferences
                val sharedPreferences = getSharedPreferences("SnapSmartPrefs", MODE_PRIVATE)
                sharedPreferences.edit().putString("selectedFolderUri", uri.toString()).apply()

                // Use the saved URI in future access
                println("Persisted folder URI: $uri")

                // Launch Pre scan intent
                val intent = Intent(this, PreScanPhotosActivity::class.java)
                intent.putExtra("selectedFolderUri", uri.toString())
                startActivity(intent)
            }
        }
    }
}
