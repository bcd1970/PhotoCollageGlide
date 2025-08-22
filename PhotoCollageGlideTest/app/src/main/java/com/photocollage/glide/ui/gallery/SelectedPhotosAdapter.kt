package com.photocollage.glide.ui.gallery

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.databinding.ItemSelectedPhotoThumbnailBinding
import com.photocollage.glide.selection.SelectionManager

class SelectedPhotosAdapter : ListAdapter<PhotoModel, SelectedPhotosAdapter.ThumbnailViewHolder>(PhotoDiffCallback()) {
    
    private val selectionManager = SelectionManager.getInstance()
    
    // Optimized thumbnail options for small images
    private val thumbnailOptions = RequestOptions()
        .centerCrop()
        .override(48, 48)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .skipMemoryCache(false)
        .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThumbnailViewHolder {
        val binding = ItemSelectedPhotoThumbnailBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ThumbnailViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: ThumbnailViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    inner class ThumbnailViewHolder(
        private val binding: ItemSelectedPhotoThumbnailBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            // Remove button click handler
            binding.removeButton.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val photo = getItem(position)
                    selectionManager.removePhotoFromSelection(photo.id)
                }
            }
        }
        
        fun bind(photo: PhotoModel) {
            // Load thumbnail with Glide
            Glide.with(binding.thumbnailImage.context)
                .load(photo.uri)
                .apply(thumbnailOptions)
                .into(binding.thumbnailImage)
        }
    }
}