package com.patusmaximus.snapsmart.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.backend.ImageBucketizer

class BucketPreviewAdapter(
    private val context: Context,
    private val buckets: List<ImageBucketizer.Bucket>,
    private val onBucketClick: (ImageBucketizer.Bucket) -> Unit
) : RecyclerView.Adapter<BucketPreviewAdapter.BucketViewHolder>() {

    inner class BucketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bucketLabel: TextView = view.findViewById(R.id.bucketLabel)
        val imageGrid: List<ImageView> = listOf(
            view.findViewById(R.id.image1),
            view.findViewById(R.id.image2),
            view.findViewById(R.id.image3),
            view.findViewById(R.id.image4)
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BucketViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_item_bucket, parent, false)
        return BucketViewHolder(view)
    }

    override fun onBindViewHolder(holder: BucketViewHolder, position: Int) {
        val bucket = buckets[position]
        holder.bucketLabel.text = bucket.label

        // Load up to 4 images
        val images = bucket.images.take(4)
        holder.imageGrid.forEachIndexed { index, imageView ->
            if (index < images.size) {
                imageView.visibility = View.VISIBLE

                // Use centralized resizing logic
                val resizedBitmap = ImageBucketizer().resizeImage(context, images[index].imagePath, 100, 100)
                if (resizedBitmap != null) {
                    imageView.setImageBitmap(resizedBitmap)
                } else {
                    // Use Placeholder image
                    imageView.setImageResource(R.mipmap.botlogo)
                }
            } else {
                imageView.visibility = View.GONE
            }
        }

        holder.itemView.setOnClickListener { onBucketClick(bucket) }
    }

    override fun getItemCount(): Int = buckets.size
}