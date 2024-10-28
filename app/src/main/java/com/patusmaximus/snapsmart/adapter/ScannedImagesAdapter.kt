package com.patusmaximus.snapsmart.adapter

import ImageScanResult
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R

class ScannedImagesAdapter(
    private val context: Context,
    private val scannedImages: List<ImageScanResult>
) : RecyclerView.Adapter<ScannedImagesAdapter.ScannedImageViewHolder>() {

    inner class ScannedImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageThumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
        val labelTextView: TextView = view.findViewById(R.id.labelTextView)
        val imageNameTextView: TextView = view.findViewById(R.id.imageNameTextView)
        val imageScoreTextView: TextView = view.findViewById(R.id.imageScoreTextView)
        val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)
        val imageScoreContainer: View = view.findViewById(R.id.imageScoreContainer) // Container for background color
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScannedImageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.grid_image_item, parent, false)
        return ScannedImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScannedImageViewHolder, position: Int) {
        val imageResult = scannedImages[position]
        holder.selectCheckBox.isChecked = imageResult.selected
        holder.selectCheckBox.setOnCheckedChangeListener { _, isChecked ->
            imageResult.selected = isChecked
        }

        // Set other UI elements
        holder.labelTextView.text = imageResult.label
        holder.imageNameTextView.text = imageResult.imageName
        holder.imageScoreTextView.text = imageResult.calculatedScore.toString()

        // Set background color based on score
        val backgroundColor = when {
            imageResult.calculatedScore >= 8 -> Color.GREEN
            imageResult.calculatedScore > 4 -> Color.YELLOW
            else -> Color.RED
        }
        holder.imageScoreContainer.setBackgroundColor(backgroundColor)

        // Load and downscale the image
        val uri = Uri.parse(imageResult.uriString)
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            holder.imageThumbnail.setImageBitmap(bitmap)
        }
    }


    override fun getItemCount() = scannedImages.size
}