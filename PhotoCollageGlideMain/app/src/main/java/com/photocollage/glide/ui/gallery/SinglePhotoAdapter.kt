package com.photocollage.glide.ui.gallery

import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.Priority
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.databinding.ItemSinglePhotoBinding
import com.photocollage.glide.selection.SelectionManager

class SinglePhotoAdapter : ListAdapter<PhotoModel, SinglePhotoAdapter.SinglePhotoViewHolder>(PhotoDiffCallback()) {
    
    companion object {
        private const val TAG = "SinglePhotoAdapter"
        private const val ENABLE_DETAILED_LOGGING = true
        private const val SELECTION_PAYLOAD = "selection_changed"
    }
    
    private val selectionManager = SelectionManager.getInstance()
    
    // Callback for photo clicks to track position  
    var onPhotoClick: ((position: Int, photo: PhotoModel) -> Unit)? = null
    
    private fun log(message: String) {
        if (ENABLE_DETAILED_LOGGING) {
            Log.d(TAG, message)
        }
    }
    
    // Optimized options for single photo display (Samsung Gallery style)
    private val fullPhotoOptions = RequestOptions()
        .fitCenter()
        .override(Target.SIZE_ORIGINAL) // Full resolution
        .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache everything
        .dontAnimate() // Instant display
        .priority(Priority.HIGH) // Load photos first
        .skipMemoryCache(false) // Use Glide's memory cache
        .format(DecodeFormat.PREFER_RGB_565) // 50% less memory, still good quality
        .dontTransform() // No transformations for speed
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SinglePhotoViewHolder {
        val binding = ItemSinglePhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SinglePhotoViewHolder(binding)
    }
    
    override fun onBindViewHolder(holder: SinglePhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    override fun onBindViewHolder(
        holder: SinglePhotoViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        if (payloads.contains(SELECTION_PAYLOAD)) {
            // Only update selection UI, not the image - 0ms performance
            holder.updateSelectionUI(getItem(position).id)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }
    
    override fun onViewRecycled(holder: SinglePhotoViewHolder) {
        super.onViewRecycled(holder)
        
        // Let Glide handle cleanup efficiently
        Glide.with(holder.itemView.context).clear(holder.binding.singlePhotoImageView)
        holder.binding.singlePhotoImageView.setImageDrawable(null)
        
        log("VIEW RECYCLED - Glide cleanup complete")
    }
    
    // Clean up resources when adapter is destroyed
    fun cleanup() {
        // Glide handles all cleanup automatically - no manual management needed
        log("CLEANUP - Glide will handle resource cleanup automatically")
    }
    
    fun getSelectedPhotos(): List<PhotoModel> {
        return selectionManager.getSelectedPhotoModels()
    }
    
    inner class SinglePhotoViewHolder(
        val binding: ItemSinglePhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        init {
            // Set click listener once in init for performance
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val photo = getItem(position)
                    // Toggle selection on tap
                    selectionManager.togglePhotoSelection(photo)
                    // Notify position tracking for view transitions
                    onPhotoClick?.invoke(position, photo)
                    // Update this item's UI with payload for 0ms performance
                    notifyItemChanged(position, SELECTION_PAYLOAD)
                }
            }
        }
        
        fun bind(photo: PhotoModel) {
            val startTime = SystemClock.elapsedRealtime()
            
            log("BIND START - Photo: ${photo.displayName} (${photo.size / (1024*1024)}MB)")
            
            // Use Glide for ALL photos - let it handle optimization
            Glide.with(binding.singlePhotoImageView.context)
                .load(photo.uri)
                .thumbnail(0.1f) // Small thumbnail for instant display
                .apply(fullPhotoOptions)
                .into(binding.singlePhotoImageView)
            
            // Update selection UI with O(1) performance
            updateSelectionUI(photo.id)
            
            val bindTime = SystemClock.elapsedRealtime() - startTime
            log("BIND END - Photo: ${photo.displayName} took ${bindTime}ms")
        }
        
        fun updateSelectionUI(photoId: Long) {
            val isSelected = selectionManager.isPhotoSelected(photoId)
            val hasAnySelection = selectionManager.hasSelection()
            
            // Show checkbox when there are any selections - smooth UX
            binding.checkbox.visibility = if (hasAnySelection) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        }
    }
}