package com.patusmaximus.snapsmart.backend

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.os.Parcelable
import androidx.documentfile.provider.DocumentFile
import com.patusmaximus.snapsmart.backend.model.BucketGranularity
import kotlinx.android.parcel.Parcelize
import java.text.SimpleDateFormat
import java.util.*

class ImageBucketizer {

    @Parcelize
    data class ImageData(
        val imageTitle: String?,
        val imagePath: Uri,
        val timestamp: Long
    ) : Parcelable

    @Parcelize
    data class Bucket(
        val label: String,
        val images: MutableList<ImageData>
    ) : Parcelable

    interface ProgressCallback {
        fun onProgressUpdate(progress: Int, imageTitle: String?)
    }

    // Function to process a folder and return buckets with progress updates
    fun processFolderToBuckets(
        context: Context,
        folderUri: Uri,
        granularity: BucketGranularity?,
        callback: ProgressCallback
    ): List<Bucket> {
        val images = getImagesFromFolder(context, folderUri)
        return bucketizeImagesByGranularity(images, granularity, callback)
    }

    // Function to retrieve all images from a folder using URI
    private fun getImagesFromFolder(context: Context, folderUri: Uri): List<ImageData> {
        val documentFile = DocumentFile.fromTreeUri(context, folderUri)
            ?: throw IllegalArgumentException("Invalid folder URI: $folderUri")

        val imageExtensions = setOf("jpg", "jpeg", "png", "bmp", "gif", "webp")
        val imageList = mutableListOf<ImageData>()

        // Iterate over all files in the folder
        documentFile.listFiles().forEach { file ->
            if (file.isFile && file.name?.substringAfterLast(".")?.lowercase() in imageExtensions) {
                val lastModified = file.lastModified()
                val imagePath = file.uri
                val imageTitle = file.name
                imageList.add(ImageData(imageTitle, imagePath, lastModified))
            }
        }

        return imageList
    }

    // Function to bucketize images with progress reporting
    private fun bucketizeImagesByGranularity(
        images: List<ImageData>,
        granularity: BucketGranularity?,
        callback: ProgressCallback
    ): List<Bucket> {
        val dateFormat = when (granularity) {
            BucketGranularity.Day -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            BucketGranularity.Month -> SimpleDateFormat("yyyy-MM", Locale.getDefault())
            BucketGranularity.Year -> SimpleDateFormat("yyyy", Locale.getDefault())
            else -> throw IllegalArgumentException("Invalid granularity: $granularity")
        }

        val buckets = mutableMapOf<String, Bucket>()
        val totalImages = images.size

        for ((index, image) in images.withIndex()) {
            val date = Date(image.timestamp)
            val label = dateFormat.format(date) // Generate the label based on granularity

            val bucket = buckets.getOrPut(label) { Bucket(label, mutableListOf()) }
            bucket.images.add(image)

            // Update progress
            val progress = ((index + 1) * 100) / totalImages
            callback.onProgressUpdate(progress, image.imageTitle)
        }

        return buckets.values.toList()
    }

    // Function to resize and decode an image URI into a Bitmap
    fun resizeImage(context: Context, uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
                BitmapFactory.decodeStream(inputStream, null, this)
                inSampleSize = calculateInSampleSize(this, targetWidth, targetHeight)
                inJustDecodeBounds = false
            }

            // Close the previous stream before opening a new one
            inputStream?.close()

            val resizedBitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            resizedBitmap?.let { adjustBitmapOrientation(context, uri, it) }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Helper function to rotate images using their exif data
    private fun adjustBitmapOrientation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val exif = inputStream?.use { ExifInterface(it) }
            val orientation = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            val rotationMatrix = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> android.graphics.Matrix().apply { postRotate(90f) }
                ExifInterface.ORIENTATION_ROTATE_180 -> android.graphics.Matrix().apply { postRotate(180f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> android.graphics.Matrix().apply { postRotate(270f) }
                else -> null
            }

            rotationMatrix?.let {
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, it, true)
            } ?: bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap // Return the original bitmap if rotation fails
        }
    }


    // Helper function to calculate inSampleSize for resizing
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    // Helper function to save images to folders based on the bucket labels
    fun distributeImagesToBuckets(
        context: Context,
        destinationFolder: Uri,
        buckets: List<Bucket>,
        keepOriginalImages: Boolean?,
        callback: (progress: Int, bucketName: String, imagesProcessed: Int) -> Unit
    ): Pair<Int, Int> {
        var totalImagesProcessed = 0
        val documentFile = DocumentFile.fromTreeUri(context, destinationFolder)
            ?: throw IllegalArgumentException("Invalid destination folder URI: $destinationFolder")

        buckets.forEachIndexed { index, bucket ->
            val bucketFolder = documentFile.createDirectory(bucket.label)
                ?: throw IllegalStateException("Failed to create directory for bucket: ${bucket.label}")

            bucket.images.forEach { image ->
                val sourceFile = DocumentFile.fromSingleUri(context, image.imagePath)
                val destinationFileName = sourceFile?.name ?: "image_${System.currentTimeMillis()}"

                sourceFile?.let {
                    val destinationFile = bucketFolder.createFile("image/*", destinationFileName)
                    if (destinationFile != null && context.contentResolver.openInputStream(it.uri) != null) {
                        context.contentResolver.openOutputStream(destinationFile.uri)?.use { output ->
                            context.contentResolver.openInputStream(it.uri)?.copyTo(output)

                            // Delete source file after copying only if user selected
                            if (keepOriginalImages == false)
                                it.delete()
                            totalImagesProcessed++
                        }
                    }
                }
            }
            // Callback to update progress
            callback(((index + 1) * 100) / buckets.size, bucket.label, bucket.images.size)
        }
        return Pair(buckets.size, totalImagesProcessed)
    }
}
