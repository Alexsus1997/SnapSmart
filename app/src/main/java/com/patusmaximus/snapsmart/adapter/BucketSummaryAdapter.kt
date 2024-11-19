import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.backend.ImageBucketizer

class BucketSummaryAdapter(
    private val buckets: List<ImageBucketizer.Bucket>, // List of buckets
    private val onBucketClick: (ImageBucketizer.Bucket) -> Unit // Click handler
) : RecyclerView.Adapter<BucketSummaryAdapter.BucketViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BucketViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_item_bucket, parent, false)
        return BucketViewHolder(view)
    }

    override fun onBindViewHolder(holder: BucketViewHolder, position: Int) {
        val bucket = buckets[position]

        // Set the bucket label
        holder.bucketLabel.text = bucket.label

        // List of ImageViews in the grid
        val imageViews = listOf(holder.image1, holder.image2, holder.image3, holder.image4)

        // Populate images or set placeholders
        bucket.images.take(4).forEachIndexed { index, image ->
            val uri = image.imagePath
            try {
                val bitmap = decodeSampledBitmapFromUri(uri, 100, 100, holder.itemView.context)
                if (bitmap != null) {
                    imageViews[index].setImageBitmap(bitmap)
                } else {
                    imageViews[index].setImageResource(R.mipmap.botlogo) // Fallback placeholder
                }
            } catch (e: Exception) {
                Log.e("BucketSummaryAdapter", "Error loading image: $uri", e)
                imageViews[index].setImageResource(R.mipmap.botlogo) // Fallback placeholder
            }
        }

        // If fewer than 4 images, set placeholders for remaining ImageViews
        for (i in bucket.images.size until 4) {
            imageViews[i].setImageResource(R.mipmap.botlogo) // Set a default placeholder
        }
    }


    private fun decodeSampledBitmapFromUri(uri: Uri, reqWidth: Int, reqHeight: Int, context: Context): Bitmap? {
        try {
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true

            // Open InputStream to get image dimensions
            val inputStream = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false

            // Decode bitmap with inSampleSize set
            val resizedInputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(resizedInputStream, null, options)
            resizedInputStream?.close()
            return bitmap
        } catch (e: Exception) {
            Log.e("BucketSummaryAdapter", "Failed to decode image", e)
            return null
        }
    }

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


    inner class BucketViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bucketLabel: TextView = view.findViewById(R.id.bucketLabel)
        val image1: ImageView = view.findViewById(R.id.image1)
        val image2: ImageView = view.findViewById(R.id.image2)
        val image3: ImageView = view.findViewById(R.id.image3)
        val image4: ImageView = view.findViewById(R.id.image4)
    }


    override fun getItemCount(): Int = buckets.size
}
