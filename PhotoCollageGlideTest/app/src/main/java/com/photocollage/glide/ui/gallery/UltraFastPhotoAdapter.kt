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
import com.photocollage.glide.selection.SelectionManager

class UltraFastPhotoAdapter : ListAdapter<PhotoModel, UltraFastPhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()), ListPreloader.PreloadModelProvider<PhotoModel> {
    
    private val selectionManager = SelectionManager.getInstance()
    
    // Callback for photo clicks to track position
    var onPhotoClick: ((position: Int, photo: PhotoModel) -> Unit)? = null
    
    // Navigation context awareness for optimized preloading
    enum class NavigationContext {
        ALL_PHOTOS,      // Direct access to all photos
        ALBUM_PHOTOS,    // Photos from a specific album  
        SWITCHING_CONTEXT // Transitioning between contexts
    }
    
    private var currentContext: NavigationContext = NavigationContext.ALL_PHOTOS
    
    private lateinit var context: Context
    private var preloadSizeProvider: ViewPreloadSizeProvider<PhotoModel>? = null
    private var currentRecyclerView: RecyclerView? = null
    private var currentPreloader: RecyclerViewPreloader<PhotoModel>? = null
    private var currentScrollListener: RecyclerView.OnScrollListener? = null
    
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
    
    fun getSelectedPhotos(): List<PhotoModel> {
        return selectionManager.getSelectedPhotoModels()
    }
    
    // Set navigation context for optimized preloading
    fun setNavigationContext(context: NavigationContext) {
        currentContext = context
    }
    
    // Get optimized preload counts based on navigation context
    private fun getPreloadCounts(): Triple<Int, Int, Int> {
        return when (currentContext) {
            NavigationContext.ALL_PHOTOS -> Triple(50, 50, 20) // RecyclerViewPreloader, thumbnail, full
            NavigationContext.ALBUM_PHOTOS -> Triple(30, 30, 15) // Reduced for album context
            NavigationContext.SWITCHING_CONTEXT -> Triple(40, 40, 18) // Maintain aggressive preloading during transitions
        }
    }
    
    fun setupWithRecyclerView(recyclerView: RecyclerView) {
        // Clean up existing setup first to prevent multiple listeners
        cleanup()
        
        // Store references for proper cleanup
        currentRecyclerView = recyclerView
        context = recyclerView.context
        
        // Setup context-aware preloading with Glide's RecyclerView integration
        preloadSizeProvider = ViewPreloadSizeProvider<PhotoModel>()
        
        val (recyclerViewPreloadCount, _, _) = getPreloadCounts()
        
        val preloader = RecyclerViewPreloader(
            Glide.with(recyclerView),
            this,
            preloadSizeProvider!!,
            recyclerViewPreloadCount
        )
        
        val scrollListener = getOptimizedScrollListener()
        
        // Store references for cleanup
        currentPreloader = preloader
        currentScrollListener = scrollListener
        
        // Double-check no duplicate listeners before adding
        recyclerView.removeOnScrollListener(preloader)
        recyclerView.removeOnScrollListener(scrollListener)
        
        recyclerView.addOnScrollListener(preloader)
        recyclerView.addOnScrollListener(scrollListener)
    }
    
    private fun getOptimizedScrollListener(): RecyclerView.OnScrollListener {
        return object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = layoutManager.findLastVisibleItemPosition()
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                
                val (_, thumbnailCount, fullImageCount) = getPreloadCounts()
                
                // Context-aware bidirectional preloading
                if (dy > 0) { // Scrolling down
                    for (i in lastVisible..minOf(lastVisible + thumbnailCount, itemCount - 1)) {
                        preloadThumbnail(i)
                    }
                } else if (dy < 0) { // Scrolling up
                    for (i in maxOf(0, firstVisible - thumbnailCount)..firstVisible) {
                        preloadThumbnail(i)
                    }
                }
                
                // Preload full-size images for closer items (context-aware count)
                for (i in lastVisible..minOf(lastVisible + fullImageCount, itemCount - 1)) {
                    if (i >= 0) preloadFullImage(i)
                }
                for (i in maxOf(0, firstVisible - fullImageCount)..firstVisible) {
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
    
    // Clean up existing listeners and references
    fun cleanup() {
        currentRecyclerView?.let { recyclerView ->
            currentPreloader?.let { preloader ->
                recyclerView.removeOnScrollListener(preloader)
            }
            currentScrollListener?.let { scrollListener ->
                recyclerView.removeOnScrollListener(scrollListener)
            }
        }
        
        // Clear references
        currentRecyclerView = null
        currentPreloader = null
        currentScrollListener = null
        preloadSizeProvider = null
    }
    
    // Smart cache warming for context transitions
    fun prepareForContextTransition(newContext: NavigationContext, shouldClearCache: Boolean = false) {
        // Set switching context temporarily for optimized transition preloading
        currentContext = NavigationContext.SWITCHING_CONTEXT
        
        // Clear memory cache if switching between significantly different contexts
        if (shouldClearCache && ::context.isInitialized) {
            Glide.get(context).clearMemory()
        }
        
        // Set the new context after RecyclerView setup completes for smoother transition
        currentRecyclerView?.post {
            // Use a second post to ensure the transition completes smoothly
            currentRecyclerView?.post {
                currentContext = newContext
            }
        }
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
                    // Always allow selection on tap
                    selectionManager.togglePhotoSelection(photo)
                    // Notify position tracking for view transitions
                    onPhotoClick?.invoke(position, photo)
                    // Update this item's UI
                    notifyItemChanged(position, SELECTION_PAYLOAD)
                }
            }
            
            // Tell preload size provider about our fixed size
            preloadSizeProvider?.setView(binding.imageView)
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
            val isSelected = selectionManager.isPhotoSelected(photoId)
            val hasAnySelection = selectionManager.hasSelection()
            
            // Show checkbox when there are any selections
            binding.checkbox.visibility = if (hasAnySelection) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
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