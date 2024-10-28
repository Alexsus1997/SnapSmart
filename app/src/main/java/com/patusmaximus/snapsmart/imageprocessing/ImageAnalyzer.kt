import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import android.provider.DocumentsContract
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
            0 -> "DEFOCUSED BLUR"
            1 -> "MOTION BLUR"
            2 -> "SHARP"
            else -> "UNKNOWN"
        }

        // Calculate initial score of 10, deducting points based on the label
        val calculatedScore = when (label) {
            "DEFOCUSED BLUR" -> 2
            "MOTION BLUR" -> 5
            "SHARP" -> 10
            else -> 0
        }

        return ImageScanResult(imageName, score, label, uriString, calculatedScore)
    }

    // Function to scan a folder
    suspend fun scanFolder(folderUri: Uri): FolderScanResult {
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

    private fun getEstimatedProcessingTime(photoCount: Int): Double {
        return photoCount * 0.3
    }

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

    fun handleSelectedImages(selectedImages: List<ImageScanResult>, destinationFolderUri: Uri?) {
        val contentResolver = this.contentResolver

        selectedImages.forEach { image ->
            val imageUri = Uri.parse(image.uriString)

            if (destinationFolderUri != null) {
                // Move image to destination folder
                moveImage(contentResolver, imageUri, destinationFolderUri)
            } else {
                // Delete image if not selected
                deleteImage(contentResolver, imageUri)
            }
        }
    }

    private fun moveImage(contentResolver: ContentResolver, imageUri: Uri, destinationFolderUri: Uri) {
        // Extract the file name from the URI
        val documentId = DocumentsContract.getDocumentId(imageUri)
        val fileName = documentId.substringAfterLast(":")
        val destinationUri = Uri.withAppendedPath(destinationFolderUri, fileName)

        // Use a simple copy and delete approach
        contentResolver.openInputStream(imageUri)?.use { inputStream ->
            contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }

        // After copying, delete the original file
        deleteImage(contentResolver, imageUri)
    }

    private fun deleteImage(contentResolver: ContentResolver, imageUri: Uri) {
        // Delete the file using the content resolver
        contentResolver.delete(imageUri, null, null)
    }
}


// Data class for scan results
data class FolderScanResult(
    val photoCount: Int,
    val thumbnails: List<Bitmap>,
    val estimatedProcessingTime: Double,
    val totalImages: Int
)


// Data class for scan results
data class ImageScanResult(
    val imageName: String,
    val score: Int,
    val label: String,
    val uriString: String,
    val calculatedScore: Int,
    var selected: Boolean = false
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString() ?: "",
        parcel.readInt(),
        parcel.readString() ?: "",
        parcel.readString() ?: "",
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(imageName)
        parcel.writeInt(score)
        parcel.writeString(label)
        parcel.writeString(uriString)
        parcel.writeInt(calculatedScore)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ImageScanResult> {
        override fun createFromParcel(parcel: Parcel): ImageScanResult {
            return ImageScanResult(parcel)
        }

        override fun newArray(size: Int): Array<ImageScanResult?> {
            return arrayOfNulls(size)
        }
    }
}

