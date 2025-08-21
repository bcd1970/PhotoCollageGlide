package com.photocollage.glide.ui.gallery

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.ViewPreloadSizeProvider
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.photocollage.glide.R
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.databinding.ItemPhotoBinding

class UltraFastPhotoAdapter : ListAdapter<PhotoModel, UltraFastPhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()), ListPreloader.PreloadModelProvider<PhotoModel> {
    
    private val selectedPhotos = mutableSetOf<Long>()
    var isSelectionMode = false
        private set
    
    private lateinit var context: Context
    private lateinit var preloadSizeProvider: ViewPreloadSizeProvider<PhotoModel>
    
    // Ultra-aggressive options for instant display
    private val visibleOptions = RequestOptions()
        .centerCrop()
        .override(200, 200)
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .priority(Priority.IMMEDIATE) // Highest priority for visible items
        .skipMemoryCache(false)
        .placeholder(ColorDrawable(0xFF424242.toInt())) // Solid gray
        .error(ColorDrawable(0xFF424242.toInt()))
    
    // Thumbnail options for ultra-fast preloading
    private val thumbnailOptions = RequestOptions()
        .centerCrop()
        .override(50, 50) // Tiny thumbnails for instant preload
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .priority(Priority.LOW)
        .skipMemoryCache(false)
        .format(com.bumptech.glide.load.DecodeFormat.PREFER_RGB_565)
    
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
    
    fun setupWithRecyclerView(recyclerView: RecyclerView) {
        // Setup ultra-aggressive preloading with Glide's RecyclerView integration
        preloadSizeProvider = ViewPreloadSizeProvider<PhotoModel>()
        
        val preloader = RecyclerViewPreloader(
            Glide.with(recyclerView),
            this,
            preloadSizeProvider,
            75 // Preload 75 items ahead for ultra-smooth scrolling!
        )
        
        recyclerView.addOnScrollListener(preloader)
        recyclerView.addOnScrollListener(getUltraAggressiveScrollListener())
    }
    
    private fun getUltraAggressiveScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                
                // Ultra-aggressive bidirectional preloading - 100 images ahead!
                if (dy > 0) { // Scrolling down
                    for (i in lastVisible..minOf(lastVisible + 100, itemCount - 1)) {
                        preloadThumbnail(i)
                    }
                } else if (dy < 0) { // Scrolling up
                    for (i in maxOf(0, firstVisible - 100)..firstVisible) {
                        preloadThumbnail(i)
                    }
                }
                
                // Preload full-size images for closer items (30 ahead)
                for (i in lastVisible..minOf(lastVisible + 30, itemCount - 1)) {
                    if (i >= 0) preloadFullImage(i)
                }
                for (i in maxOf(0, firstVisible - 30)..firstVisible) {
                    preloadFullImage(i)
                }
            }
        }
    }
    
    private fun preloadThumbnail(position: Int) {
        if (position in 0 until itemCount) {
            val photo = getItem(position)
            Glide.with(context)
                .load(photo.uri)
                .apply(thumbnailOptions)
                .preload(50, 50) // Tiny thumbnails
        }
    }
    
    private fun preloadFullImage(position: Int) {
        if (position in 0 until itemCount) {
            val photo = getItem(position)
            Glide.with(context)
                .load(photo.uri)
                .apply(visibleOptions.priority(Priority.LOW))
                .preload(200, 200)
        }
    }
    
    // Implement PreloadModelProvider for Glide's RecyclerView integration
    override fun getPreloadItems(position: Int): List<PhotoModel> {
        return if (position >= 0 && position < itemCount) {
            listOf(getItem(position))
        } else {
            emptyList()
        }
    }
    
    override fun getPreloadRequestBuilder(photo: PhotoModel): com.bumptech.glide.RequestBuilder<*> {
        return Glide.with(context)
            .load(photo.uri)
            .apply(thumbnailOptions)
    }
    
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
            
            // Tell preload size provider about our fixed size
            if (::preloadSizeProvider.isInitialized) {
                preloadSizeProvider.setView(binding.imageView)
            }
        }
        
        fun bind(photo: PhotoModel) {
            // Set solid background first to prevent flash
            binding.imageView.setBackgroundColor(0xFF424242.toInt())
            
            // Load with thumbnail-first approach for ultra-smooth loading
            Glide.with(binding.imageView.context)
                .load(photo.uri)
                .apply(visibleOptions)
                .thumbnail(
                    Glide.with(binding.imageView.context)
                        .load(photo.uri)
                        .apply(thumbnailOptions)
                )
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