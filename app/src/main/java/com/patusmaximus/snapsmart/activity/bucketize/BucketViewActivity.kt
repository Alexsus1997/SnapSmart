package com.patusmaximus.snapsmart.activity.bucketize

import android.app.Dialog
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.adapter.BucketDetailAdapter
import com.patusmaximus.snapsmart.backend.ImageBucketizer

class BucketViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bucketize_bucket_view)

        val bucket = intent.getParcelableExtra<ImageBucketizer.Bucket>("bucket")!!
        findViewById<TextView>(R.id.bucketTitle).text = bucket.label

        val recyclerView = findViewById<RecyclerView>(R.id.bucketImagesRecyclerView)
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        recyclerView.adapter = BucketDetailAdapter(this, bucket.images) { image ->
            showImageDialog(image)
        }

        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun showImageDialog(imageData: ImageBucketizer.ImageData) {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_bucket_image_preview)

        val imageView = dialog.findViewById<ImageView>(R.id.fullSizeImageView)
        val resizedBitmap = ImageBucketizer().resizeImage(this, imageData.imagePath, 800, 800)
        if (resizedBitmap != null) {
            imageView.setImageBitmap(resizedBitmap)
        } else {
            imageView.setImageResource(R.mipmap.botlogo)
        }

        dialog.findViewById<ImageButton>(R.id.closeButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
}
