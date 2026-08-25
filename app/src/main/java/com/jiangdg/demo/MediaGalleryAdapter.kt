package com.jiangdg.demo

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.jiangdg.demo.databinding.ItemMediaBinding

class MediaGalleryAdapter(
    private val onOpen: (MediaRecord) -> Unit,
    private val onComment: (MediaRecord) -> Unit
) : ListAdapter<MediaRecord, MediaGalleryAdapter.MediaViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        return MediaViewHolder(
            ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class MediaViewHolder(
        private val binding: ItemMediaBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(record: MediaRecord) = with(binding) {
            val request = Glide.with(previewIv)
                .load(Uri.parse(record.uri))
                .placeholder(android.R.drawable.ic_menu_gallery)
            if (record.hasEmbeddedStamp) request.fitCenter() else request.centerCrop()
            request.into(previewIv)
            timestampTv.text = MediaRepository.displayDate(record.capturedAt)
            timestampTv.visibility = if (record.hasEmbeddedStamp) View.GONE else View.VISIBLE
            commentTv.text = record.comment
            commentTv.visibility = if (
                record.comment.isBlank() ||
                (record.hasEmbeddedStamp && record.comment == record.embeddedComment)
            ) View.GONE else View.VISIBLE
            videoBadgeIv.visibility = if (record.isVideo) View.VISIBLE else View.GONE
            root.setOnClickListener { onOpen(record) }
            commentBtn.setOnClickListener { onComment(record) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<MediaRecord>() {
            override fun areItemsTheSame(oldItem: MediaRecord, newItem: MediaRecord): Boolean =
                oldItem.uri == newItem.uri

            override fun areContentsTheSame(oldItem: MediaRecord, newItem: MediaRecord): Boolean =
                oldItem == newItem
        }
    }
}
