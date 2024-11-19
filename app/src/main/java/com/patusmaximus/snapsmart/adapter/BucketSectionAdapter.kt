package com.patusmaximus.snapsmart.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.patusmaximus.snapsmart.R
import com.patusmaximus.snapsmart.backend.ImageBucketizer

class BucketSectionAdapter(
    private val context: Context,
    private val sections: List<Section>,
    private val onBucketClick: (ImageBucketizer.Bucket) -> Unit
) : RecyclerView.Adapter<BucketSectionAdapter.SectionViewHolder>() {

    data class Section(
        val title: String,
        val buckets: List<ImageBucketizer.Bucket>,
        var isExpanded: Boolean = true
    )

    inner class SectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sectionTitle: TextView = view.findViewById(R.id.sectionTitle)
        val dropdownIcon: ImageView = view.findViewById(R.id.dropdownIcon)
        val bucketsRecyclerView: RecyclerView = view.findViewById(R.id.bucketsRecyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SectionViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.adapter_bucket_section, parent, false)
        return SectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: SectionViewHolder, position: Int) {
        val section = sections[position]
        holder.sectionTitle.text = section.title
        holder.dropdownIcon.setImageResource(
            if (section.isExpanded) R.mipmap.dropdown_down_foreground else R.mipmap.dropdown_up_foreground
        )

        // Show/hide RecyclerView based on expanded state
        holder.bucketsRecyclerView.visibility = if (section.isExpanded) View.VISIBLE else View.GONE
        holder.bucketsRecyclerView.layoutManager = GridLayoutManager(context, 2)
        holder.bucketsRecyclerView.adapter = BucketPreviewAdapter(context, section.buckets, onBucketClick)

        // Toggle expand/collapse on click
        holder.sectionTitle.setOnClickListener {
            section.isExpanded = !section.isExpanded
            notifyItemChanged(position)
        }

        holder.dropdownIcon.setOnClickListener {
            section.isExpanded = !section.isExpanded
            notifyItemChanged(position)
        }
    }

    override fun getItemCount(): Int = sections.size
}
