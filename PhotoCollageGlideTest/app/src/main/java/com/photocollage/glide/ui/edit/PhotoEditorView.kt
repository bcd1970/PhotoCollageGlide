package com.photocollage.glide.ui.edit

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Custom photo editor view with Canvas-based rendering for 60fps performance.
 * Supports pinch-to-zoom, pan, and boundary constraints with hardware acceleration.
 * 
 * Performance targets:
 * - Rendering: 60fps (16ms frame time)
 * - Touch response: <16ms
 * - Memory usage: <50MB for editor components
 * - Gesture latency: <1ms
 */
class PhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), EditorGestureHandler.EditorGestureListener {
    
    companion object {
        private const val DOUBLE_TAP_ZOOM_FACTOR = 2.0f
        private const val FLING_FRICTION = 0.98f
        private const val MIN_FLING_VELOCITY = 100f
        
        // Paint optimization flags
        private const val PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
    }
    
    // Core components
    private val transformationManager = TransformationManager()
    private val boundsCalculator = ViewportBoundsCalculator()
    private lateinit var gestureHandler: EditorGestureHandler
    
    // Rendering components
    private val imagePaint = Paint(PAINT_FLAGS)
    private val debugPaint = Paint(PAINT_FLAGS).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    // Current image
    private var currentBitmap: Bitmap? = null
    private var imageUri: Uri? = null
    private var isImageLoaded = false
    private var currentTarget: CustomTarget<Bitmap>? = null
    
    // Animation
    private var flingAnimator: ValueAnimator? = null
    private var snapBackAnimator: ValueAnimator? = null
    
    // Performance monitoring
    private var lastFrameTime = 0L
    private var frameCount = 0
    private var isDebugModeEnabled = false
    
    // Reusable objects for performance
    private val tempMatrix = Matrix()
    private val tempRectF = RectF()
    private val tempPointF = PointF()
    
    // Touch state
    private var isFirstLayout = true
    
    // Navigation overlay callback
    private var navigationOverlay: PhotoNavigationOverlay? = null
    
    // Glide request options optimized for editor
    private val editorImageOptions = RequestOptions()
        .fitCenter()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .skipMemoryCache(false)
        .format(DecodeFormat.PREFER_RGB_565)
        .dontTransform()
    
    init {
        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null)
        
        // Initialize gesture handler after context is available
        post { gestureHandler = EditorGestureHandler(context, this) }
        
        // Enable focus for touch events
        isFocusable = true
        isFocusableInTouchMode = true
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        if (w > 0 && h > 0) {
            boundsCalculator.initialize(
                viewportWidth = w.toFloat(),
                viewportHeight = h.toFloat()
            )
            
            // Initialize transformation if we have an image
            currentBitmap?.let { bitmap ->
                transformationManager.initialize(
                    imageWidth = bitmap.width.toFloat(),
                    imageHeight = bitmap.height.toFloat(),
                    viewportWidth = w.toFloat(),
                    viewportHeight = h.toFloat()
                )
                
                if (isFirstLayout) {
                    isFirstLayout = false
                    invalidate()
                }
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val startTime = System.nanoTime()
        
        currentBitmap?.let { bitmap ->
            // Save canvas state
            canvas.save()
            
            // Apply transformation matrix
            val matrix = transformationManager.getCurrentMatrix()
            canvas.concat(matrix)
            
            // Draw image
            canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)
            
            // Restore canvas state
            canvas.restore()
            
            // Update navigation overlay with current viewport
            updateNavigationOverlayViewport()
            
            // Draw debug info if enabled
            if (isDebugModeEnabled) {
                drawDebugInfo(canvas)
            }
        }
        
        // Performance monitoring
        if (isDebugModeEnabled) {
            updateFrameStats(startTime)
        }
    }
    
    /**
     * Load image from URI
     */
    fun loadImage(uri: Uri) {
        imageUri = uri
        isImageLoaded = false
        
        // Clear previous target if exists
        currentTarget?.let { 
            try {
                Glide.with(context.applicationContext).clear(it)
            } catch (e: Exception) {
                // Context may be invalid, ignore
            }
        }
        
        // Create new target and store reference
        currentTarget = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                currentBitmap = resource
                isImageLoaded = true
                
                if (width > 0 && height > 0) {
                    transformationManager.initialize(
                        imageWidth = resource.width.toFloat(),
                        imageHeight = resource.height.toFloat(),
                        viewportWidth = width.toFloat(),
                        viewportHeight = height.toFloat()
                    )
                    
                    // Ensure proper initial bounds after initialization
                    checkAndPerformSnapBack()
                }
                
                // Update navigation overlay with new image
                navigationOverlay?.setPhoto(imageUri!!, resource.width.toFloat(), resource.height.toFloat())
                
                invalidate()
            }
            
            override fun onLoadCleared(placeholder: Drawable?) {
                // Clean up safely when Glide clears the request
                currentBitmap = null
                isImageLoaded = false
                invalidate()
            }
        }
        
        // Load image into the custom target
        Glide.with(context)
            .asBitmap()
            .load(uri)
            .apply(editorImageOptions)
            .into(currentTarget!!)
    }
    
    /**
     * Handle touch events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isImageLoaded || !::gestureHandler.isInitialized) {
            return super.onTouchEvent(event)
        }
        
        // Stop any ongoing animations
        stopAllAnimations()
        
        return gestureHandler.onTouchEvent(event) || super.onTouchEvent(event)
    }
    
    // EditorGestureListener implementations
    
    override fun onScaleBegin(focusX: Float, focusY: Float): Boolean {
        stopAllAnimations()
        return true
    }
    
    override fun onScale(scaleFactor: Float, focusX: Float, focusY: Float): Boolean {
        if (transformationManager.applyScale(scaleFactor, focusX, focusY)) {
            invalidate()
            return true
        }
        return false
    }
    
    override fun onScaleEnd() {
        // Check if image needs to snap back to bounds
        checkAndPerformSnapBack()
    }
    
    override fun onPanBegin(startX: Float, startY: Float): Boolean {
        stopAllAnimations()
        return true
    }
    
    override fun onPan(deltaX: Float, deltaY: Float): Boolean {
        val imageBounds = transformationManager.getTransformedImageBounds()
        val constrainedDelta = boundsCalculator.constrainTranslationDelta(
            imageBounds, deltaX, deltaY, allowElastic = true
        )
        
        if (transformationManager.applyTranslation(constrainedDelta.x, constrainedDelta.y, constrainBounds = true)) {
            invalidate()
            return true
        }
        return false
    }
    
    override fun onPanEnd() {
        checkAndPerformSnapBack()
    }
    
    override fun onFling(velocityX: Float, velocityY: Float): Boolean {
        val velocity = sqrt(velocityX * velocityX + velocityY * velocityY)
        if (velocity < MIN_FLING_VELOCITY) {
            return false
        }
        
        startFlingAnimation(velocityX, velocityY)
        return true
    }
    
    override fun onDoubleTap(x: Float, y: Float): Boolean {
        val currentZoom = transformationManager.getZoomLevel()
        val targetZoom = if (currentZoom > 1.5f) 1.0f else DOUBLE_TAP_ZOOM_FACTOR
        
        animateZoomToLevel(targetZoom, x, y)
        return true
    }
    
    /**
     * Start fling animation
     */
    private fun startFlingAnimation(velocityX: Float, velocityY: Float) {
        flingAnimator?.cancel()
        
        var currentVelX = velocityX
        var currentVelY = velocityY
        
        flingAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            interpolator = DecelerateInterpolator(1.5f)
            
            addUpdateListener { animator ->
                val fraction = animator.animatedFraction
                val deltaTime = 16f // Assume 16ms per frame
                
                // Apply friction
                currentVelX *= FLING_FRICTION
                currentVelY *= FLING_FRICTION
                
                // Calculate movement delta
                val deltaX = currentVelX * deltaTime / 1000f
                val deltaY = currentVelY * deltaTime / 1000f
                
                // Stop if velocity is too low
                val currentVelocity = sqrt(currentVelX * currentVelX + currentVelY * currentVelY)
                if (currentVelocity < MIN_FLING_VELOCITY) {
                    animator.cancel()
                    checkAndPerformSnapBack()
                    return@addUpdateListener
                }
                
                // Apply constrained movement
                val imageBounds = transformationManager.getTransformedImageBounds()
                val constrainedDelta = boundsCalculator.constrainTranslationDelta(
                    imageBounds, deltaX, deltaY, allowElastic = false
                )
                
                if (abs(constrainedDelta.x) > 0.1f || abs(constrainedDelta.y) > 0.1f) {
                    transformationManager.applyTranslation(constrainedDelta.x, constrainedDelta.y, constrainBounds = true)
                    invalidate()
                } else {
                    // Hit boundary, stop fling
                    animator.cancel()
                    checkAndPerformSnapBack()
                }
            }
            
            start()
        }
    }
    
    /**
     * Animate zoom to specific level
     */
    private fun animateZoomToLevel(targetZoom: Float, focusX: Float, focusY: Float) {
        val startZoom = transformationManager.getZoomLevel()
        
        ValueAnimator.ofFloat(startZoom, targetZoom).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val currentZoom = animator.animatedValue as Float
                transformationManager.setZoomLevel(currentZoom, focusX, focusY)
                invalidate()
            }
            
            start()
        }
    }
    
    /**
     * Check if image needs snap-back and perform it
     */
    private fun checkAndPerformSnapBack() {
        val imageBounds = transformationManager.getTransformedImageBounds()
        val snapBackDelta = boundsCalculator.calculateSnapBackTranslation(imageBounds)
        
        if (snapBackDelta != null && (abs(snapBackDelta.x) > 1f || abs(snapBackDelta.y) > 1f)) {
            animateSnapBack(snapBackDelta.x, snapBackDelta.y)
        }
    }
    
    /**
     * Animate snap-back to bounds
     */
    private fun animateSnapBack(deltaX: Float, deltaY: Float) {
        snapBackAnimator?.cancel()
        
        snapBackAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val currentDeltaX = deltaX * fraction
                val currentDeltaY = deltaY * fraction
                
                // Calculate remaining delta
                val remainingDeltaX = deltaX - currentDeltaX
                val remainingDeltaY = deltaY - currentDeltaY
                
                if (abs(remainingDeltaX) > 0.1f || abs(remainingDeltaY) > 0.1f) {
                    transformationManager.applyTranslation(
                        remainingDeltaX * 0.1f, // Apply 10% of remaining delta per frame
                        remainingDeltaY * 0.1f,
                        constrainBounds = true
                    )
                    invalidate()
                }
            }
            
            start()
        }
    }
    
    /**
     * Stop all animations
     */
    private fun stopAllAnimations() {
        flingAnimator?.cancel()
        snapBackAnimator?.cancel()
    }
    
    /**
     * Draw debug information
     */
    private fun drawDebugInfo(canvas: Canvas) {
        val imageBounds = transformationManager.getTransformedImageBounds()
        val viewport = boundsCalculator.getViewportBounds()
        
        // Draw image bounds
        debugPaint.color = Color.GREEN
        canvas.drawRect(imageBounds, debugPaint)
        
        // Draw viewport bounds
        debugPaint.color = Color.RED
        canvas.drawRect(viewport, debugPaint)
        
        // Draw transformation info
        debugPaint.color = Color.WHITE
        debugPaint.textSize = 24f
        val info = "Scale: %.2f, Pos: (%.1f, %.1f)".format(
            transformationManager.getCurrentScale(),
            transformationManager.getCurrentTranslation().x,
            transformationManager.getCurrentTranslation().y
        )
        canvas.drawText(info, 20f, 50f, debugPaint)
    }
    
    /**
     * Update frame statistics for performance monitoring
     */
    private fun updateFrameStats(startTime: Long) {
        val frameTime = (System.nanoTime() - startTime) / 1_000_000f // Convert to ms
        frameCount++
        
        if (frameTime > 16.67f) { // Slower than 60fps
            android.util.Log.w("PhotoEditorView", "Slow frame: ${frameTime}ms")
        }
        
        // Log fps every 60 frames
        if (frameCount % 60 == 0) {
            val currentTime = System.currentTimeMillis()
            if (lastFrameTime > 0) {
                val fps = 60000f / (currentTime - lastFrameTime)
                android.util.Log.d("PhotoEditorView", "FPS: $fps")
            }
            lastFrameTime = currentTime
        }
    }
    
    /**
     * Reset image to initial fit-to-screen state
     */
    fun resetImageTransformation() {
        stopAllAnimations()
        transformationManager.resetToInitial()
        invalidate()
    }
    
    /**
     * Enable/disable debug mode
     */
    fun setDebugMode(enabled: Boolean) {
        isDebugModeEnabled = enabled
        invalidate()
    }
    
    /**
     * Get current image URI
     */
    fun getCurrentImageUri(): Uri? = imageUri
    
    /**
     * Check if image is loaded
     */
    fun isImageLoaded(): Boolean = isImageLoaded && currentBitmap != null
    
    /**
     * Set navigation overlay for viewport updates
     */
    fun setNavigationOverlay(overlay: PhotoNavigationOverlay) {
        navigationOverlay = overlay
        
        // Set up click listener to navigate to clicked position
        overlay.setOnNavigationClickListener { imageX, imageY ->
            navigateToPosition(imageX, imageY)
        }
        
        // Initialize overlay with current image if loaded
        android.util.Log.d("PhotoEditorView", "setNavigationOverlay: currentBitmap=${currentBitmap != null}, imageUri=${imageUri}")
        currentBitmap?.let { bitmap ->
            imageUri?.let { uri ->
                android.util.Log.d("PhotoEditorView", "Initializing overlay with existing image: ${bitmap.width}x${bitmap.height}")
                overlay.setPhoto(uri, bitmap.width.toFloat(), bitmap.height.toFloat())
                updateNavigationOverlayViewport()
            }
        }
    }
    
    /**
     * Navigate to specific position in image
     */
    private fun navigateToPosition(imageX: Float, imageY: Float) {
        if (!isImageLoaded || width <= 0 || height <= 0) return
        
        val currentZoom = transformationManager.getZoomLevel()
        
        // Calculate the position to center the clicked point in the viewport
        val targetTranslationX = (width / 2f) - (imageX * currentZoom)
        val targetTranslationY = (height / 2f) - (imageY * currentZoom)
        
        // Apply the translation directly
        transformationManager.setTranslation(targetTranslationX, targetTranslationY)
        
        // Check bounds and snap back if needed
        checkAndPerformSnapBack()
        
        invalidate()
    }
    
    /**
     * Update navigation overlay with current viewport bounds
     */
    private fun updateNavigationOverlayViewport() {
        val overlay = navigationOverlay ?: return
        val bitmap = currentBitmap ?: return
        
        if (width <= 0 || height <= 0) return
        
        // Check current zoom level to determine visibility
        val currentZoom = transformationManager.getZoomLevel()
        val shouldShowOverlay = currentZoom > 1.0f
        
        android.util.Log.d("PhotoEditorView", "updateNavigationOverlayViewport: zoom=$currentZoom, shouldShow=$shouldShowOverlay")
        
        // Calculate viewport bounds in image coordinates
        val currentMatrix = transformationManager.getCurrentMatrix()
        val inverseMatrix = Matrix()
        
        if (currentMatrix.invert(inverseMatrix)) {
            // Get viewport corners in screen coordinates
            val viewportCorners = floatArrayOf(
                0f, 0f,           // top-left
                width.toFloat(), 0f,           // top-right
                width.toFloat(), height.toFloat(),     // bottom-right
                0f, height.toFloat()          // bottom-left
            )
            
            // Transform to image coordinates
            inverseMatrix.mapPoints(viewportCorners)
            
            // Find bounds
            val minX = viewportCorners.filterIndexed { index, _ -> index % 2 == 0 }.minOrNull() ?: 0f
            val maxX = viewportCorners.filterIndexed { index, _ -> index % 2 == 0 }.maxOrNull() ?: bitmap.width.toFloat()
            val minY = viewportCorners.filterIndexed { index, _ -> index % 2 == 1 }.minOrNull() ?: 0f
            val maxY = viewportCorners.filterIndexed { index, _ -> index % 2 == 1 }.maxOrNull() ?: bitmap.height.toFloat()
            
            // Constrain to image bounds
            val constrainedMinX = maxOf(0f, minOf(minX, bitmap.width.toFloat()))
            val constrainedMaxX = maxOf(0f, minOf(maxX, bitmap.width.toFloat()))
            val constrainedMinY = maxOf(0f, minOf(minY, bitmap.height.toFloat()))
            val constrainedMaxY = maxOf(0f, minOf(maxY, bitmap.height.toFloat()))
            
            // Update overlay with zoom-based visibility
            tempRectF.set(constrainedMinX, constrainedMinY, constrainedMaxX, constrainedMaxY)
            overlay.updateViewport(tempRectF, shouldShowOverlay)
        }
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimations()
        
        // Clear Glide CustomTarget request properly
        currentTarget?.let { target ->
            try {
                // Use application context to avoid "destroyed activity" error
                Glide.with(context.applicationContext).clear(target)
            } catch (e: Exception) {
                // Context may be invalid during cleanup, ignore
            }
        }
        
        // Clear references without manual recycling
        currentTarget = null
        currentBitmap = null
        imageUri = null
        isImageLoaded = false
    }
}