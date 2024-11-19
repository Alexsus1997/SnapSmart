import android.content.Context
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
import com.patusmaximus.snapsmart.backend.model.ImageScanResult
import com.patusmaximus.snapsmart.model.blurModelType

class ScannedImagesAdapter(
    private val context: Context,
    private val scannedImages: List<ImageScanResult>
) : RecyclerView.Adapter<ScannedImagesAdapter.ScannedImageViewHolder>() {

    private val imageAnalyzer = ImageAnalyzer(context)

    inner class ScannedImageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageThumbnail: ImageView = view.findViewById(R.id.imageThumbnail)
        val labelTextView: TextView = view.findViewById(R.id.labelTextView)
        val imageNameTextView: TextView = view.findViewById(R.id.imageNameTextView)
        val imageScoreTextView: TextView = view.findViewById(R.id.imageScoreTextView)
        val selectCheckBox: CheckBox = view.findViewById(R.id.selectCheckBox)
        val imageScoreContainer: View = view.findViewById(R.id.imageScoreContainer)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScannedImageViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_scan_grid_image_item, parent, false)
        return ScannedImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ScannedImageViewHolder, position: Int) {
        val imageResult = scannedImages[position]

        setBindings(holder, imageResult)

        holder.labelTextView.text = imageResult.label.toString()
        holder.imageNameTextView.text = imageResult.imageName
        holder.imageScoreTextView.text = imageResult.calculatedScore.toString()

        val backgroundColor = when {
            imageResult.calculatedScore >= 8 -> Color.GREEN
            imageResult.calculatedScore > 4 -> Color.YELLOW
            else -> Color.RED
        }
        holder.imageScoreContainer.setBackgroundColor(backgroundColor)

        val bitmap = imageAnalyzer.loadAndCorrectImage(Uri.parse(imageResult.uriString), 200, 200)
        if (bitmap != null) {
            holder.imageThumbnail.setImageBitmap(bitmap)
            Log.d("ScannedImagesAdapter", "Bitmap successfully loaded and corrected.")
        } else {
            Log.e("ScannedImagesAdapter", "Failed to load bitmap from URI: ${imageResult.uriString}")
            holder.imageThumbnail.setImageResource(R.mipmap.botlogo)
        }
    }

    override fun getItemCount() = scannedImages.size

    private fun setBindings(holder: ScannedImageViewHolder, imageResult: ImageScanResult) {
        holder.imageThumbnail.setOnClickListener {
            val activity = context as AppCompatActivity
            val reason = if (imageResult.label == blurModelType.SHARP) "Recommended: Sharp Image" else "Not Recommended: ${imageResult.label}"
            val dialogFragment = ImagePopupDialogFragment.newInstance(
                imageResult.uriString, imageResult.imageName, reason
            )
            dialogFragment.show(activity.supportFragmentManager, "ImagePopupDialog")
        }

        holder.selectCheckBox.isChecked = imageResult.selected
        holder.selectCheckBox.setOnCheckedChangeListener { _, isChecked ->
            imageResult.selected = isChecked
        }
    }
}
