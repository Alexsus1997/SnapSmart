package com.patusmaximus.snapsmart.adapter

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.backend.ImageBucketizer

class BucketDetailAdapter(
    private val context: Context,
    private val images: List<ImageBucketizer.ImageData>,
    private val onImageClick: (ImageBucketizer.ImageData) -> Unit
) : RecyclerView.Adapter<BucketDetailAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.imageThumbnail)
        val imageTitle: TextView = view.findViewById(R.id.imageTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        holder.imageTitle.text = image.imageTitle

        // Use centralized resizing logic
        val resizedBitmap = ImageBucketizer().resizeImage(context, image.imagePath, 200, 200)
        if (resizedBitmap != null) {
            holder.imageView.setImageBitmap(resizedBitmap)
        } else {
            holder.imageView.setImageResource(R.mipmap.botlogo) // Placeholder image
        }

        holder.imageView.setOnClickListener { onImageClick(image) }
    }

    override fun getItemCount(): Int = images.size
}
