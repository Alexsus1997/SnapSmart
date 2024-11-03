package com.patusmaximus.snapsmart.fragment

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.patusmaximus.snapsmart.R
import android.util.Log
import java.io.IOException

class ImagePopupDialogFragment : DialogFragment() {

    private lateinit var imageUri: String
    private lateinit var pictureName: String
    private lateinit var reason: String

    companion object {
        fun newInstance(imageUri: String, pictureName: String, reason: String): ImagePopupDialogFragment {
            val fragment = ImagePopupDialogFragment()
            val args = Bundle()
            args.putString("imageUri", imageUri)
            args.putString("pictureName", pictureName)
            args.putString("reason", reason)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            setCancelable(true)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_scan_image_popup, container, false)

        // Retrieve arguments
        imageUri = arguments?.getString("imageUri") ?: ""
        pictureName = arguments?.getString("pictureName") ?: "Unknown"
        reason = arguments?.getString("reason") ?: "Unknown"

        // Initialize views
        val fullSizeImageView = view.findViewById<ImageView>(R.id.fullSizeImageView)
        val pictureNameTextView = view.findViewById<TextView>(R.id.pictureNameTextView)
        val reasonTextView = view.findViewById<TextView>(R.id.reasonTextView)
        val closeButton = view.findViewById<ImageButton>(R.id.closeButton)

        // Load the full-size image but scaled down
        val uri = Uri.parse(imageUri)
        loadDownscaledImage(uri, fullSizeImageView)

        // Set information
        pictureNameTextView.text = pictureName
        reasonTextView.text = reason

        // Close dialog on button click
        closeButton.setOnClickListener { dismiss() }

        return view
    }

    override fun onStart() {
        super.onStart()
        // Resize dialog to 90% width and 80% height of the screen
        dialog?.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            (resources.displayMetrics.heightPixels * 0.8).toInt()
        )
    }

    private fun loadDownscaledImage(uri: Uri, imageView: ImageView) {
        val targetWidth = 1024 // You can adjust this value as per your requirement
        val targetHeight = 1024

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        requireContext().contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inJustDecodeBounds = false

        requireContext().contentResolver.openInputStream(uri)?.use {
            val bitmap = BitmapFactory.decodeStream(it, null, options)
            if (bitmap != null) {
                val rotatedBitmap = correctImageOrientation(bitmap, uri)
                imageView.setImageBitmap(rotatedBitmap)
                Log.d("ImagePopupDialogFragment", "Loaded downscaled image with corrected orientation.")
            } else {
                Log.e("ImagePopupDialogFragment", "Failed to decode bitmap from URI: $uri")
            }
        }
    }

    private fun correctImageOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        var exif: ExifInterface? = null
        try {
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                exif = ExifInterface(inputStream)
            }
        } catch (e: IOException) {
            Log.e("ImagePopupDialogFragment", "Error reading EXIF data", e)
        }

        val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
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

        return inSampleSize
    }
}
