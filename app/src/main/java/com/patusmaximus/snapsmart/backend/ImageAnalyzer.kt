import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.patusmaximus.snapsmart.backend.model.FolderScanResult
import com.patusmaximus.snapsmart.backend.model.ImageScanResult
import com.patusmaximus.snapsmart.backend.model.UserScanPreferences
import com.patusmaximus.snapsmart.model.blurModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class ImageAnalyzer(context: Context) {

    private val tfliteModel: Interpreter = Interpreter(loadModelFile(context, "Models/blur_model.tflite"))
    private val contentResolver: ContentResolver = context.contentResolver
    private val inputSize = 224
    private val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3).apply {
        order(ByteOrder.nativeOrder())
    }

    // Method to load and correct image orientation
    fun loadAndCorrectImage(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }

        options.inSampleSize = calculateInSampleSize(options, targetWidth, targetHeight)
        options.inJustDecodeBounds = false

        val bitmap = contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null

        return correctImageOrientation(bitmap, uri)
    }

    private fun correctImageOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        var exif: ExifInterface? = null
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                exif = ExifInterface(inputStream)
            }
        } catch (e: IOException) {
            Log.e("ImageAnalyzer", "Error reading EXIF data", e)
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

    // Function to scan a folder
    fun scanFolder(folderUri: Uri): FolderScanResult {
        val imageUris = mutableListOf<Uri>()
        val thumbnails = mutableListOf<Bitmap>()
        var photoCount = 0
        var totalImages = 0

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri, DocumentsContract.getTreeDocumentId(folderUri)
        )

        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null,
            null,
            null
        )

        cursor?.use {
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex)
                val mimeType = cursor.getString(mimeTypeIndex)

                if (mimeType.startsWith("image/")) {
                    totalImages++
                    val imageUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                    imageUris.add(imageUri)
                    photoCount++

                    if (photoCount <= 4) {
                        val bitmap = loadAndCorrectImage(imageUri, 200, 200)
                        bitmap?.let {
                            val thumbnail = createThumbnail(it, 200, 200)
                            thumbnails.add(thumbnail)
                        }
                    }
                }
            }
        }

        val estimatedProcessingTime = getEstimatedProcessingTime(totalImages)
        return FolderScanResult(photoCount, thumbnails, estimatedProcessingTime, totalImages)
    }


    // Function to create a thumbnail while maintaining the aspect ratio
    private fun createThumbnail(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat()

        // Calculate the target dimensions while maintaining aspect ratio
        val (scaledWidth, scaledHeight) = if (bitmap.width >= bitmap.height) {
            val width = targetWidth
            val height = (width / aspectRatio).toInt()
            Pair(width, height)
        } else {
            val height = targetHeight
            val width = (height * aspectRatio).toInt()
            Pair(width, height)
        }

        return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
    }

    // Function to process images with progress status
    suspend fun processImagesWithProgress(
        folderUri: Uri,
        onProgressUpdate: (processedCount: Int, imageName: String) -> Unit
    ): List<ImageScanResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ImageScanResult>()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri, DocumentsContract.getTreeDocumentId(folderUri)
        )
        var processedCount = 0

        contentResolver.query(childrenUri, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeTypeIndex = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)

            while (cursor.moveToNext()) {
                val documentId = cursor.getString(idIndex)
                val imageName = cursor.getString(nameIndex)
                val mimeType = cursor.getString(mimeTypeIndex)

                if (mimeType.startsWith("image/")) {
                    val imageUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, documentId)
                    val bitmap = loadAndCorrectImage(imageUri, inputSize, inputSize)

                    bitmap?.let {
                        val imageResult = analyzeImage(it, imageName, imageUri.toString())
                        results.add(imageResult)
                    }

                    processedCount++
                    withContext(Dispatchers.Main) {
                        onProgressUpdate(processedCount, imageName)
                    }
                }
            }
        }
        results
    }

    // Function to handle selected images
    fun handleSelectedImages(context: Context, selectedImages: List<ImageScanResult>, deleteImages: List<ImageScanResult>, userScanPreferences: UserScanPreferences?) {
        if (userScanPreferences?.deleteNotRecommendedPhotos == true) {
            deleteImages.forEach { image ->
                val imageUri = Uri.parse(image.uriString)
                deleteImage(context, imageUri)
            }
        }

        if (userScanPreferences?.destinationFolder != null) {
            selectedImages.forEach { image ->
                val imageUri = Uri.parse(image.uriString)
                moveImage(context, imageUri, userScanPreferences.destinationFolder)
            }
        }
    }

    // Load the model from assets
    private fun loadModelFile(context: Context, modelFileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelFileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Function to preprocess the image
    private fun preprocessImage(bitmap: Bitmap): ByteBuffer {
        byteBuffer.clear()
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val intValues = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        for (pixel in intValues) {
            val r = (pixel shr 16 and 0xFF) / 255.0f
            val g = (pixel shr 8 and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }

    // Function to analyze a single image
    private fun analyzeImage(bitmap: Bitmap, imageName: String, uriString: String): ImageScanResult {
        val inputBuffer = preprocessImage(bitmap)
        val output = Array(1) { FloatArray(3) }
        tfliteModel.run(inputBuffer, output)
        val score = output[0].indices.maxByOrNull { output[0][it] } ?: -1

        val label = when (score) {
            0 -> blurModelType.DEFOCUSED_BLUR
            1 -> blurModelType.MOTION_BLUR
            2 -> blurModelType.SHARP
            else -> blurModelType.ERROR
        }

        val calculatedScore = when (label) {
            blurModelType.DEFOCUSED_BLUR -> 2
            blurModelType.MOTION_BLUR -> 5
            blurModelType.SHARP -> 10
            else -> 0
        }

        return ImageScanResult(imageName, score, label, uriString, calculatedScore)
    }

    // Function to calculate estimated processing time
    private fun getEstimatedProcessingTime(photoCount: Int): Double {
        return photoCount * 0.3
    }

    // Helper function to move image to a different folder
    private fun moveImage(context: Context, imageUri: Uri, destinationFolderUri: Uri?) {
        val documentFile = DocumentFile.fromSingleUri(context, imageUri)
        val destinationFolder = destinationFolderUri?.let { DocumentFile.fromTreeUri(context, it) }

        if (documentFile != null && documentFile.exists() && destinationFolder != null && destinationFolder.isDirectory && destinationFolder.canWrite()) {
            val fileName = documentFile.name ?: return
            val destinationFile = destinationFolder.createFile("image/*", fileName)

            context.contentResolver.openInputStream(imageUri)?.use { inputStream ->
                context.contentResolver.openOutputStream(destinationFile!!.uri)?.use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            deleteImage(context, imageUri)
        }
    }

    // Helper function to delete image from the source folder
    private fun deleteImage(context: Context, imageUri: Uri) {
        val documentFile = DocumentFile.fromSingleUri(context, imageUri)
        if (documentFile != null && documentFile.exists() && documentFile.canWrite()) {
            documentFile.delete()
        }
    }
}
