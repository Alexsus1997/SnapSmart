import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.patusmaximus.snapsmart.imageprocessing.model.FolderScanResult
import com.patusmaximus.snapsmart.imageprocessing.model.ImageScanResult
import com.patusmaximus.snapsmart.imageprocessing.model.UserScanPreferences
import com.patusmaximus.snapsmart.model.blurModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
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

    // Public functions

    // Function to scan a folder
    fun scanFolder(folderUri: Uri): FolderScanResult
    {
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
                        val bitmap = contentResolver.openInputStream(imageUri)?.use { inputStream ->
                            BitmapFactory.decodeStream(inputStream)
                        }
                        bitmap?.let {
                            val thumbnail = Bitmap.createScaledBitmap(it, 200, 200, true)
                            thumbnails.add(thumbnail)
                        }
                    }
                }
            }
        }

        val estimatedProcessingTime = getEstimatedProcessingTime(totalImages)
        return FolderScanResult(photoCount, thumbnails, estimatedProcessingTime, totalImages)
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
                    val bitmap = contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }

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
        // Delete not selected images if the user has chosen to do so
        if (userScanPreferences?.deleteNotRecommendedPhotos == true)
        {
            deleteImages.forEach { image ->
                val imageUri = Uri.parse(image.uriString)
                deleteImage(context, imageUri)
            }
        }

        // Move selected images (if destination folder URI is provided)
        if (userScanPreferences?.destinationFolder != null) {
            selectedImages.forEach { image ->
                val imageUri = Uri.parse(image.uriString)
                moveImage(context, imageUri, userScanPreferences.destinationFolder)
            }
        }
    }


    // Private helper functions

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

        // Calculate initial score of 10, deducting points based on the label
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