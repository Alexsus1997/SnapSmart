package com.patusmaximus.snapsmart.adapter

import android.content.Context
import android.graphics.Bitmap
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
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.fragment.ImagePopupDialogFragment
import com.patusmaximus.snapsmart.imageprocessing.model.ImageScanResult
import com.patusmaximus.snapsmart.model.blurModelType

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
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_scan_grid_image_item, parent, false)
        return ScannedImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScannedImageViewHolder, position: Int) {
        val imageResult = scannedImages[position]

        // Set bindings
        setBindings(holder, imageResult)

        // Set other UI elements
        holder.labelTextView.text = imageResult.label.toString()
        holder.imageNameTextView.text = imageResult.imageName
        holder.imageScoreTextView.text = imageResult.calculatedScore.toString()

        // Set background color based on score
        val backgroundColor = when {
            imageResult.calculatedScore >= 8 -> Color.GREEN
            imageResult.calculatedScore > 4 -> Color.YELLOW
            else -> Color.RED
        }
        holder.imageScoreContainer.setBackgroundColor(backgroundColor)

        // Load and downscale the image with logging
        loadDownscaledImage(holder, Uri.parse(imageResult.uriString))
    }

    override fun getItemCount() = scannedImages.size

    private fun setBindings(holder: ScannedImageViewHolder, imageResult: ImageScanResult) {
        // Click listener to show full-size image in a popup
        holder.imageThumbnail.setOnClickListener {
            val activity = context as AppCompatActivity
            val reason = if (imageResult.label == blurModelType.SHARP) "Recommended: Sharp Image" else "Not Recommended: ${imageResult.label}"
            val dialogFragment = ImagePopupDialogFragment.newInstance(
                imageResult.uriString, imageResult.imageName, reason
            )
            dialogFragment.show(activity.supportFragmentManager, "ImagePopupDialog")
        }

        // Set checkbox state and listener
        holder.selectCheckBox.isChecked = imageResult.selected
        holder.selectCheckBox.setOnCheckedChangeListener { _, isChecked ->
            imageResult.selected = isChecked
        }
    }

    private fun loadDownscaledImage(holder: ScannedImageViewHolder, uri: Uri) {
        // Target width and height for the thumbnail
        val targetWidth = 200
        val targetHeight = 200

        // Options for loading only dimensions
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, options)
        }

        // Calculate sample size based on target dimensions
        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inJustDecodeBounds = false
        options.inScaled = true
        options.inDensity = options.outWidth
        options.inTargetDensity = targetWidth * options.inSampleSize

        // Load and scale the bitmap with the calculated sample size
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)

            if (originalBitmap != null) {
                // Apply matrix transformation for precise scaling
                val matrix = android.graphics.Matrix().apply {
                    setScale(
                        targetWidth / originalBitmap.width.toFloat(),
                        targetHeight / originalBitmap.height.toFloat()
                    )
                }
                val transformedBitmap = Bitmap.createBitmap(
                    originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                )
                holder.imageThumbnail.setImageBitmap(transformedBitmap)
                Log.d("ScannedImagesAdapter", "Bitmap successfully loaded and transformed.")

                // Recycle original if it's larger
                if (transformedBitmap != originalBitmap) {
                    originalBitmap.recycle()
                }
            } else {
                Log.e("ScannedImagesAdapter", "Failed to decode bitmap from URI: $uri, loading placeholder.")
                holder.imageThumbnail.setImageResource(R.mipmap.botlogo
                ) // Replace with actual placeholder
            }
        } ?: Log.e("ScannedImagesAdapter", "Unable to access image at URI: $uri")
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        Log.d("ScannedImagesAdapter", "Calculated inSampleSize: $inSampleSize for target ${reqWidth}x$reqHeight")
        return inSampleSize
    }
}
