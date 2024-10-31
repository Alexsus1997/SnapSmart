package com.patusmaximus.snapsmart.fragment

import android.app.Dialog
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

        // Load the full-size image
        val uri = Uri.parse(imageUri)
        fullSizeImageView.setImageURI(uri)

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
}