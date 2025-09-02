package com.photocollage.glide.ui.gallery

import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewConfiguration
import android.view.HapticFeedbackConstants
import android.os.Build
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.photocollage.glide.data.PhotoModel
import com.photocollage.glide.databinding.ItemSinglePhotoBinding
import com.photocollage.glide.selection.SelectionManager

class SinglePhotoAdapter : ListAdapter<PhotoModel, SinglePhotoAdapter.SinglePhotoViewHolder>(PhotoDiffCallback()) {
    
    companion object {
        private const val TAG = "SinglePhotoAdapter"
        private const val SELECTION_PAYLOAD = "selection_changed"
    }
    
    private val selectionManager = SelectionManager.getInstance()
    
    // Callback for photo clicks to track position  
    var onPhotoClick: ((position: Int, photo: PhotoModel) -> Unit)? = null
    
    
    // No Glide options needed for SubsamplingScaleImageView
    
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
        
        // Release tiles/bitmap from SubsamplingScaleImageView
        holder.binding.singlePhotoImageView.recycle()
        
    }
    
    // Clean up resources when adapter is destroyed
    fun cleanup() {
        // Glide handles all cleanup automatically - no manual management needed
    }
    
    fun getSelectedPhotos(): List<PhotoModel> {
        return selectionManager.getSelectedPhotoModels()
    }
    
    inner class SinglePhotoViewHolder(
        val binding: ItemSinglePhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        
        private val touchSlop = ViewConfiguration.get(binding.root.context).scaledTouchSlop
        private val longPressTimeoutMs = 250L
        private var suppressNextClick = false
        private var longPressPosted = false
        private var longPressTriggered = false
        private var downX = 0f
        private var downY = 0f
        private var currentPhoto: PhotoModel? = null
        private var longPressRunnable: Runnable? = null

        init {
            // Tap toggles selection (unless consumed by a prior long-press)
            binding.root.setOnClickListener {
                if (suppressNextClick) {
                    suppressNextClick = false
                    return@setOnClickListener
                }
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val photo = getItem(position)
                    // Toggle selection on tap
                    selectionManager.togglePhotoSelection(photo)
                    // Haptic feedback for tap selection toggle
                    val hapticType = if (Build.VERSION.SDK_INT >= 23) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.VIRTUAL_KEY
                    binding.root.performHapticFeedback(hapticType)
                    // Notify position tracking for view transitions
                    onPhotoClick?.invoke(position, photo)
                    // Update this item's UI with payload for 0ms performance
                    notifyItemChanged(position, SELECTION_PAYLOAD)
                }
            }
        }
        
        fun bind(photo: PhotoModel) {
            val startTime = SystemClock.elapsedRealtime()
            // Remember bound photo for long-press selection
            currentPhoto = photo

            // Configure SubsamplingScaleImageView
            binding.singlePhotoImageView.apply {
                // Respect EXIF orientation and start centered inside
                setOrientation(SubsamplingScaleImageView.ORIENTATION_USE_EXIF)
                setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
                // Reasonable zoom limits
                maxScale = 5.0f
                setDoubleTapZoomScale(2.5f)
                setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER)
                // Load image from content Uri
                setImage(ImageSource.uri(photo.uri))
                // Allow panning/zoom
                isZoomEnabled = true
                isPanEnabled = true
            }
            // Custom touch handling to (1) reduce long-press timeout and (2) prevent ViewPager interception during zoom
            binding.singlePhotoImageView.setOnTouchListener { v, event ->
                val parent = binding.root.parent
                try {
                    val ssv = binding.singlePhotoImageView
                    val disallow = event.pointerCount > 1 || ssv.scale > 1.01f
                    parent?.requestDisallowInterceptTouchEvent(disallow)
                } catch (_: Throwable) {}

                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        longPressTriggered = false
                        longPressPosted = true
                        downX = event.x
                        downY = event.y
                        val pos = adapterPosition
                        longPressRunnable = Runnable {
                            if (longPressPosted && pos != RecyclerView.NO_POSITION) {
                                // Select photo on custom long-press
                                currentPhoto?.let { p ->
                                    selectionManager.selectPhoto(p)
                                    notifyItemChanged(pos, SELECTION_PAYLOAD)
                                    // Haptic feedback for long-press selection
                                    binding.root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    suppressNextClick = true
                                    longPressTriggered = true
                                }
                            }
                        }
                        // Schedule custom long press
                        v.postDelayed(longPressRunnable!!, longPressTimeoutMs)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(event.x - downX)
                        val dy = kotlin.math.abs(event.y - downY)
                        if ((dx > touchSlop || dy > touchSlop) || event.pointerCount > 1) {
                            // Cancel if moved too much or multi-touch started
                            if (longPressPosted && longPressRunnable != null) v.removeCallbacks(longPressRunnable!!)
                            longPressPosted = false
                        }
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        if (longPressPosted && longPressRunnable != null) v.removeCallbacks(longPressRunnable!!)
                        longPressPosted = false
                    }
                }
                // Allow SSV to also handle the gesture
                false
            }
            
            // Update selection UI with O(1) performance
            updateSelectionUI(photo.id)
            
            val bindTime = SystemClock.elapsedRealtime() - startTime
        }
        
        fun updateSelectionUI(photoId: Long) {
            val isSelected = selectionManager.isPhotoSelected(photoId)
            val hasAnySelection = selectionManager.hasSelection()
            
            // Show checkbox when there are any selections - smooth UX
            binding.checkbox.visibility = if (hasAnySelection) View.VISIBLE else View.GONE
            binding.checkbox.isChecked = isSelected
            binding.selectionOverlay.visibility = if (isSelected) View.VISIBLE else View.GONE
        }

        fun resetZoomToFit() {
            try {
                binding.singlePhotoImageView.resetScaleAndCenter()
            } catch (_: Throwable) {
                // Safe-guard for cases where tiles not ready yet
                binding.singlePhotoImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_INSIDE)
            }
        }
    }
}
