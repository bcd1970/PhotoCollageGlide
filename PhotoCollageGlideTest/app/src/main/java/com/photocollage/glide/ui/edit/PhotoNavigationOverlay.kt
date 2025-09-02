package com.photocollage.glide.ui.edit

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.max
import kotlin.math.min

/**
 * Thumbnail navigation overlay for photo editor showing miniature representation
 * with viewport position indicator and touch navigation support.
 * 
 * Performance targets:
 * - Rendering: 60fps (16ms frame time)
 * - Memory usage: <5MB for thumbnail
 * - Touch response: <16ms
 */
class PhotoNavigationOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val OVERLAY_SIZE_DP = 80f // Reduced by 1/3 (was 120f)
        private const val THUMBNAIL_MAX_SIZE = 70f // Reduced proportionally (was 100f)
        private const val VIEWPORT_INDICATOR_STROKE_WIDTH = 1.5f // Slightly thinner for smaller size
        private const val OVERLAY_PADDING_DP = 5f // Reduced padding
        private const val CORNER_RADIUS_DP = 6f
        private const val SHADOW_RADIUS_DP = 4f // For elevation effect
        private const val SHADOW_OFFSET_DP = 2f
        
        // Animation constants
        private const val FADE_IN_DURATION_MS = 500L // Longer, smoother fade-in duration
        private const val FADE_OUT_DURATION_MS = 250L // Slightly longer fade-out duration
        
        // Colors - removed border, enhanced shadow
        private const val OVERLAY_BACKGROUND_COLOR = 0xF0000000.toInt() // Darker for better contrast
        private const val VIEWPORT_INDICATOR_COLOR = 0xFF00FF00.toInt() // Bright green
        private const val SHADOW_COLOR = 0x80000000.toInt() // Black shadow with transparency
    }
    
    // Dimensions in pixels (converted from dp)
    private val overlaySize: Float
    private val thumbnailMaxSize: Float
    private val overlayPadding: Float
    private val cornerRadius: Float
    private val shadowRadius: Float
    private val shadowOffset: Float
    
    // Paint objects for rendering - no background paint needed
    private val viewportIndicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = VIEWPORT_INDICATOR_COLOR
        style = Paint.Style.STROKE
        strokeWidth = VIEWPORT_INDICATOR_STROKE_WIDTH
    }
    
    // Image paint with shadow for elevation effect
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    
    // Thumbnail data
    private var photoUri: Uri? = null
    private var thumbnailBitmap: Bitmap? = null
    private var thumbnailPosition = RectF()
    private var currentTarget: CustomTarget<Bitmap>? = null
    private var thumbnailLayoutCalculated = false
    
    // Image dimensions for scaling calculations
    private var imageWidth = 0f
    private var imageHeight = 0f
    private var thumbnailScale = 1f
    
    // Viewport tracking
    private var viewportBounds = RectF()
    private var isViewportVisible = false
    
    // Animation support - using View.animate() instead
    
    // Touch handling
    private var onNavigationClickListener: ((Float, Float) -> Unit)? = null
    
    // Reusable objects for performance
    private val tempRectF = RectF()
    private val sourceRect = Rect()
    
    // Glide options for thumbnail - optimized for small image
    private val thumbnailOptions = RequestOptions()
        .centerInside() // Better for thumbnails
        .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
        .dontAnimate()
        .skipMemoryCache(false)
        .format(DecodeFormat.PREFER_RGB_565)
        .override(200, 200) // Larger size for better quality
    
    init {
        val density = context.resources.displayMetrics.density
        overlaySize = OVERLAY_SIZE_DP * density
        thumbnailMaxSize = THUMBNAIL_MAX_SIZE * density
        overlayPadding = OVERLAY_PADDING_DP * density
        cornerRadius = CORNER_RADIUS_DP * density
        shadowRadius = SHADOW_RADIUS_DP * density
        shadowOffset = SHADOW_OFFSET_DP * density
        
        // No shadow layer - removed for smooth alpha animations
        
        // Start transparent - fade animations will make it visible
        visibility = VISIBLE
        alpha = 0f // Start invisible, fade-in will make it visible
        
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Fixed square size for corner overlay
        val size = overlaySize.toInt()
        setMeasuredDimension(size, size)
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && thumbnailBitmap != null) {
            calculateThumbnailLayout()
            invalidate()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        
        // No background - clean elevation-only appearance
        
        thumbnailBitmap?.let { bitmap ->
            // Draw thumbnail image with elevation shadow only
            sourceRect.set(0, 0, bitmap.width, bitmap.height)
            canvas.drawBitmap(bitmap, sourceRect, thumbnailPosition, imagePaint)
            
            // Draw viewport indicator if visible
            if (isViewportVisible) {
                drawViewportIndicator(canvas)
            }
        }
        // No loading indicator when no background - keeps it clean
    }
    
    /**
     * Set photo for thumbnail generation
     */
    fun setPhoto(uri: Uri, originalImageWidth: Float, originalImageHeight: Float) {
        
        // Clear previous target only if we have one
        currentTarget?.let { target ->
            try {
                Glide.with(context).clear(target)
            } catch (e: Exception) {
                // Context may be invalid, ignore
            }
        }
        
        photoUri = uri
        imageWidth = originalImageWidth
        imageHeight = originalImageHeight
        
        // Load new thumbnail (don't clear all data first)
        loadThumbnail()
    }
    
    /**
     * Update viewport bounds for indicator with fade animation
     */
    fun updateViewport(viewportBounds: RectF, isVisible: Boolean) {
        this.viewportBounds.set(viewportBounds)
        val wasVisible = this.isViewportVisible
        this.isViewportVisible = isVisible
        
        
        // Animate fade in/out when visibility changes
        if (isVisible && !wasVisible) {
            // Fade in when becoming visible
            animateFadeIn()
        } else if (!isVisible && wasVisible) {
            // Fade out when becoming invisible
            animateFadeOut()
        } else if (isVisible) {
            // Just update position if already visible
            invalidate()
        }
    }
    
    /**
     * Set navigation click listener
     */
    fun setOnNavigationClickListener(listener: (Float, Float) -> Unit) {
        this.onNavigationClickListener = listener
    }
    
    /**
     * Simple manual fade in animation
     */
    private fun animateFadeIn() {
        
        // Ensure thumbnail layout is calculated
        if (thumbnailBitmap != null && !thumbnailLayoutCalculated && width > 0 && height > 0) {
            calculateThumbnailLayout()
        }
        
        // Simple manual fade: 10 steps over 500ms = 50ms per step
        alpha = 0f
        var step = 0
        val fadeStep = Runnable {
            step++
            alpha = step / 10f
            invalidate()
        }
        
        // Schedule 10 fade steps
        for (i in 1..10) {
            postDelayed({
                step++
                alpha = step / 10f
                    invalidate()
            }, (i * 50).toLong())
        }
    }
    
    /**
     * Simple manual fade out animation
     */
    private fun animateFadeOut() {
        
        // Simple manual fade: 10 steps over 250ms = 25ms per step
        var step = 0
        
        // Schedule 10 fade steps (reverse)
        for (i in 1..10) {
            postDelayed({
                step++
                alpha = 1f - (step / 10f)
                invalidate()
            }, (i * 25).toLong())
        }
    }
    
    /**
     * Load thumbnail from photo URI
     */
    private fun loadThumbnail() {
        val uri = photoUri ?: return
        
        
        // Create new target and store reference
        currentTarget = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                thumbnailBitmap = resource
                thumbnailLayoutCalculated = false
                
                if (width > 0 && height > 0) {
                    calculateThumbnailLayout()
                    invalidate()
                }
            }
            
            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                thumbnailBitmap = null
                invalidate()
            }
            
            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                thumbnailBitmap = null
                invalidate()
            }
        }
        
        // Load thumbnail
        try {
            Glide.with(context)
                .asBitmap()
                .load(uri)
                .apply(thumbnailOptions)
                .into(currentTarget!!)
        } catch (e: Exception) {
            // Error starting Glide load, ignore
        }
    }
    
    /**
     * Calculate thumbnail layout within overlay
     */
    private fun calculateThumbnailLayout() {
        val bitmap = thumbnailBitmap ?: return
        
        
        if (imageWidth <= 0 || imageHeight <= 0) {
            return
        }
        
        // Calculate scale to fit thumbnail within square overlay, maintaining aspect ratio
        val availableSize = min(width, height) - (2 * overlayPadding)
        val maxThumbnailSize = min(availableSize, thumbnailMaxSize)
        
        // Use image aspect ratio to calculate thumbnail size
        val imageAspectRatio = imageWidth / imageHeight
        
        // Calculate size based on fitting within square space
        val thumbnailWidth: Float
        val thumbnailHeight: Float
        
        if (imageAspectRatio > 1f) {
            // Landscape image - fit by width
            thumbnailWidth = maxThumbnailSize
            thumbnailHeight = thumbnailWidth / imageAspectRatio
        } else {
            // Portrait or square image - fit by height
            thumbnailHeight = maxThumbnailSize
            thumbnailWidth = thumbnailHeight * imageAspectRatio
        }
        
        // Center thumbnail in overlay
        val startX = (width - thumbnailWidth) / 2f
        val startY = (height - thumbnailHeight) / 2f
        
        thumbnailPosition.set(
            startX,
            startY,
            startX + thumbnailWidth,
            startY + thumbnailHeight
        )
        
        thumbnailLayoutCalculated = true
    }
    
    /**
     * Draw viewport indicator rectangle
     */
    private fun drawViewportIndicator(canvas: Canvas) {
        if (imageWidth <= 0 || imageHeight <= 0 || thumbnailPosition.isEmpty) return
        
        // Calculate viewport position relative to original image
        val relativeLeft = viewportBounds.left / imageWidth
        val relativeTop = viewportBounds.top / imageHeight
        val relativeRight = viewportBounds.right / imageWidth
        val relativeBottom = viewportBounds.bottom / imageHeight
        
        // Map to thumbnail coordinate space
        val indicatorLeft = thumbnailPosition.left + (relativeLeft * thumbnailPosition.width())
        val indicatorTop = thumbnailPosition.top + (relativeTop * thumbnailPosition.height())
        val indicatorRight = thumbnailPosition.left + (relativeRight * thumbnailPosition.width())
        val indicatorBottom = thumbnailPosition.top + (relativeBottom * thumbnailPosition.height())
        
        // Draw viewport rectangle
        tempRectF.set(indicatorLeft, indicatorTop, indicatorRight, indicatorBottom)
        canvas.drawRect(tempRectF, viewportIndicatorPaint)
    }
    
    /**
     * Handle touch events for navigation
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && !thumbnailPosition.isEmpty) {
            
            if (thumbnailPosition.contains(event.x, event.y)) {
                // Calculate relative position within thumbnail
                val relativeX = (event.x - thumbnailPosition.left) / thumbnailPosition.width()
                val relativeY = (event.y - thumbnailPosition.top) / thumbnailPosition.height()
                
                // Map to image coordinates
                val imageX = relativeX * imageWidth
                val imageY = relativeY * imageHeight
                
                // Notify listener
                onNavigationClickListener?.invoke(imageX, imageY)
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    /**
     * Clear thumbnail data
     */
    private fun clearThumbnail() {
        // Clear Glide target
        currentTarget?.let { target ->
            try {
                Glide.with(context).clear(target)
            } catch (e: Exception) {
                // Context may be invalid, ignore
                // Failed to clear Glide target, context may be invalid
            }
        }
        
        thumbnailBitmap = null
        currentTarget = null
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animate().cancel()
        clearThumbnail()
    }
}