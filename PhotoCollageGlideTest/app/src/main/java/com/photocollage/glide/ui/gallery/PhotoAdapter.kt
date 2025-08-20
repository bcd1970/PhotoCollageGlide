package com.photocollage.glide.ui.gallery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.photocollage.glide.R
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.databinding.ItemPhotoBinding

class PhotoAdapter : ListAdapter<PhotoModel, PhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()) {
    
    private val selectedPhotos = mutableSetOf<Long>()
    var isSelectionMode = false
        private set
    
    // Glide options for maximum performance
    private val glideOptions = RequestOptions()
        .centerCrop()
        .override(200, 200) // Smaller size for faster loading
        .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache everything for instant display
        .dontAnimate() // No animations for instant display
        .encodeQuality(85) // Good quality/size balance
        .priority(Priority.HIGH) // High priority for visible images
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        context = parent.context
        val binding = ItemPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PhotoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun onViewRecycled(holder: PhotoViewHolder) {
        super.onViewRecycled(holder)
        // Clear Glide request to free memory
        Glide.with(holder.itemView.context).clear(holder.binding.imageView)
    }
    
    fun toggleSelectionMode() {
        isSelectionMode = !isSelectionMode
        if (!isSelectionMode) {
            selectedPhotos.clear()
        }
        notifyItemRangeChanged(0, itemCount, SELECTION_PAYLOAD)
    }
    
    fun getSelectedPhotos(): List<PhotoModel> {
        return currentList.filter { selectedPhotos.contains(it.id) }
    }
    
    fun getScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                // Don't pause loading - let Glide handle it for instant images
                // Pausing was causing grey squares
            }
            
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Preload images in the direction of scroll
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                
                // Preload next items based on scroll direction
                if (dy > 0) { // Scrolling down
                    for (i in lastVisible..minOf(lastVisible + 15, itemCount - 1)) {
                        preloadImage(i)
                    }
                } else if (dy < 0) { // Scrolling up
                    for (i in maxOf(0, firstVisible - 15)..firstVisible) {
                        preloadImage(i)
                    }
                }
            }
        }
    }
    
    private fun preloadImage(position: Int) {
        if (position in 0 until itemCount) {
            val photo = getItem(position)
            // Simple preloading with Glide
            Glide.with(context)
                .load(photo.uri)
                .apply(glideOptions)
                .preload(200, 200)
        }
    }
    
    private lateinit var context: android.content.Context
    
    inner class PhotoViewHolder(
        val binding: ItemPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            // Set click listener once in init
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val photo = getItem(position)
                    if (isSelectionMode) {
                        toggleSelection(photo.id, position)
                    }
                }
            }
            
            binding.checkbox.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val photo = getItem(position)
                    toggleSelection(photo.id, position)
                }
            }
        }
        
        fun bind(photo: PhotoModel) {
            // Simple Glide loading - let Glide handle everything
            Glide.with(binding.imageView.context)
                .load(photo.uri)
                .apply(glideOptions)
                .placeholder(R.color.surface_variant)
                .into(binding.imageView)
            
            // Update selection UI
            updateSelectionUI(photo.id)
        }
        
        fun updateSelectionUI(photoId: Long) {
            val isSelected = selectedPhotos.contains(photoId)
            binding.checkbox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
        
        private fun toggleSelection(photoId: Long, position: Int) {
            if (selectedPhotos.contains(photoId)) {
                selectedPhotos.remove(photoId)
            } else {
                selectedPhotos.add(photoId)
            }
            notifyItemChanged(position, SELECTION_PAYLOAD)
        }
    }
    
    override fun onBindViewHolder(
        holder: PhotoViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            // Only update selection UI, not the image
            holder.updateSelectionUI(getItem(position).id)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
    
    companion object {
        private const val SELECTION_PAYLOAD = "selection_changed"
    }
}

class PhotoDiffCallback : DiffUtil.ItemCallback<PhotoModel>() {
    override fun areItemsTheSame(oldItem: PhotoModel, newItem: PhotoModel): Boolean {
        return oldItem.id == newItem.id
    }
    
    override fun areContentsTheSame(oldItem: PhotoModel, newItem: PhotoModel): Boolean {
        return oldItem == newItem
    }
}