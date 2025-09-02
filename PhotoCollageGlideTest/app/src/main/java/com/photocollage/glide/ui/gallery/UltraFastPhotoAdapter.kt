package com.photocollage.glide.ui.gallery

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.LayoutInflater
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.Priority
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader
import com.bumptech.glide.util.FixedPreloadSizeProvider
import com.bumptech.glide.ListPreloader
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.photocollage.glide.R
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.databinding.ItemPhotoBinding
import com.photocollage.glide.selection.SelectionManager

class UltraFastPhotoAdapter : ListAdapter<PhotoModel, UltraFastPhotoAdapter.PhotoViewHolder>(PhotoDiffCallback()), ListPreloader.PreloadModelProvider<PhotoModel> {

    init {
        setHasStableIds(true)
    }
    
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
    private var preloadSizeProvider: ListPreloader.PreloadSizeProvider<PhotoModel>? = null
    private var currentRecyclerView: RecyclerView? = null
    private var currentPreloader: RecyclerViewPreloader<PhotoModel>? = null
    private var currentScrollListener: RecyclerView.OnScrollListener? = null

    // Computed size for grid items to let Glide decode to exact size
    private var targetWidthPx: Int = 200
    private var targetHeightPx: Int = 200
    
    // Ultra-aggressive options for instant display
    private val visibleOptions = RequestOptions()
        .centerCrop()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .priority(Priority.IMMEDIATE) // Highest priority for visible items
        .skipMemoryCache(false)
        .placeholder(ColorDrawable(0xFF424242.toInt())) // Solid gray
        .error(ColorDrawable(0xFF424242.toInt()))
    
    // Thumbnail options for ultra-fast preloading
    private val thumbnailOptions = RequestOptions()
        .centerCrop()
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

    override fun getItemId(position: Int): Long {
        return getItem(position).id
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
    private fun getPreloadCounts(): Int {
        return when (currentContext) {
            NavigationContext.ALL_PHOTOS -> 12 // Moderate, avoids jank
            NavigationContext.ALBUM_PHOTOS -> 10
            NavigationContext.SWITCHING_CONTEXT -> 12
        }
    }
    
    fun setupWithRecyclerView(recyclerView: RecyclerView) {
        // Clean up existing setup first to prevent multiple listeners
        cleanup()
        
        // Store references for proper cleanup
        currentRecyclerView = recyclerView
        context = recyclerView.context
        
        // Compute exact target size based on grid layout
        computeAndSetTargetSize(recyclerView)

        // Setup context-aware preloading with a fixed size provider
        preloadSizeProvider = FixedPreloadSizeProvider<PhotoModel>(targetWidthPx, targetHeightPx)

        val recyclerViewPreloadCount = getPreloadCounts()

        val preloader = RecyclerViewPreloader(
            Glide.with(recyclerView),
            this,
            preloadSizeProvider!!,
            recyclerViewPreloadCount
        )

        // Store references for cleanup
        currentPreloader = preloader

        // Double-check no duplicate listeners before adding
        recyclerView.removeOnScrollListener(preloader)

        recyclerView.addOnScrollListener(preloader)
    }

    private fun computeAndSetTargetSize(recyclerView: RecyclerView) {
        val lm = recyclerView.layoutManager as? GridLayoutManager
        val spanCount = lm?.spanCount ?: 3
        val metrics = recyclerView.resources.displayMetrics
        val totalWidth = recyclerView.width.takeIf { it > 0 } ?: metrics.widthPixels
        val spacingPx = 1 // matches SpaceItemDecoration(1)
        val totalSpacing = spacingPx * (spanCount + 1)
        val columnWidth = ((totalWidth - totalSpacing) / spanCount).coerceAtLeast(1)
        val heightDp = 120
        val heightPx = (heightDp * metrics.density).toInt()

        targetWidthPx = columnWidth
        targetHeightPx = heightPx

        // FixedPreloadSizeProvider already uses these values
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
            .apply(thumbnailOptions.override(targetWidthPx, targetHeightPx))
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
                    // Stronger haptic feedback for selection in grid (closer to single-view long press)
                    val hapticType = if (Build.VERSION.SDK_INT >= 30) {
                        // Use crisp confirm on newer devices when available
                        HapticFeedbackConstants.CONFIRM
                    } else {
                        HapticFeedbackConstants.LONG_PRESS
                    }
                    binding.root.performHapticFeedback(hapticType)
                    // Notify position tracking for view transitions
                    onPhotoClick?.invoke(position, photo)
                    // Update this item's UI
                    notifyItemChanged(position, SELECTION_PAYLOAD)
                }
            }
            
        }
        
        fun bind(photo: PhotoModel) {
            // Set solid background first to prevent flash
            binding.imageView.setBackgroundColor(0xFF424242.toInt())
            
            // Load with thumbnail-first approach for ultra-smooth loading
            Glide.with(binding.imageView.context)
                .load(photo.uri)
                .apply(visibleOptions.override(targetWidthPx, targetHeightPx))
                // Use fraction-based thumbnail to avoid duplicate loads
                .thumbnail(0.25f)
                .into(binding.imageView)
            
            // Update selection UI
            updateSelectionUI(photo.id)
        }
        
        fun updateSelectionUI(photoId: Long) {
            val isSelected = selectionManager.isPhotoSelected(photoId)
            
            // Show both selection overlay and hacked border when photo is selected
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
            binding.hackedBorder.visibility = if (isSelected) View.VISIBLE else View.GONE
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
