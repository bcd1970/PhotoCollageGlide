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
import android.widget.OverScroller
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Unified photo editor view that adapts to single photo or collage mode.
 * Supports pinch-to-zoom, pan, and boundary constraints with hardware acceleration.
 * 
 * Performance targets:
 * - Rendering: 60fps (16ms frame time)
 * - Touch response: <16ms
 * - Memory usage: <50MB for editor components
 * - Gesture latency: <1ms
 */
class UnifiedPhotoEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr), EditorGestureHandler.EditorGestureListener {
    
    /**
     * Editor mode determines layout and behavior
     */
    enum class EditorMode {
        SINGLE,    // One photo - behaves like PhotoEditorView
        COLLAGE    // Multiple photos - horizontal array layout
    }
    
    companion object {
        private const val DOUBLE_TAP_ZOOM_FACTOR = 2.0f
        private const val MIN_FLING_VELOCITY = 100f
        
        // Paint optimization flags
        private const val PAINT_FLAGS = Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG
        
        // Collage layout constants
        private const val COLLAGE_PHOTO_GAP = 0f // No gaps between photos
    }
    
    // Mode and photo data
    private var editorMode = EditorMode.SINGLE
    private val photoBitmaps = mutableListOf<Bitmap?>()
    private val photoUris = mutableListOf<Uri>()
    private val photoPositions = mutableListOf<RectF>() // Positions within collage
    private var currentTargets = mutableListOf<CustomTarget<Bitmap>?>()
    
    // Pre-scaled bitmaps for collage mode (performance optimization)
    private val collageScaledBitmaps = mutableListOf<Bitmap>()
    
    // Layout calculations
    private var totalCollageWidth = 0f
    private var totalCollageHeight = 0f
    private var uniformPhotoHeight = 0f // Height for collage mode (all photos same height)
    
    // First photo dimensions for proper scaling
    private var firstPhotoWidth = 0f
    private var firstPhotoHeight = 0f
    
    // Core components
    private var transformationManager: UnifiedTransformationManager? = null
    private val boundsCalculator = ViewportBoundsCalculator()
    private lateinit var gestureHandler: EditorGestureHandler
    
    // Rendering components
    private val imagePaint = Paint(PAINT_FLAGS)
    private val debugPaint = Paint(PAINT_FLAGS).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    
    // State tracking
    private var isPhotosLoaded = false
    private var isFirstLayout = true
    
    // Simple momentum scrolling
    private val scroller = OverScroller(context)
    private var lastScrollX = 0
    private var lastScrollY = 0
    
    // Performance monitoring
    private var lastFrameTime = 0L
    private var frameCount = 0
    private var isDebugModeEnabled = false
    
    // Navigation overlay listener (for collage mode)
    private var viewportChangeListener: ((RectF?, Boolean) -> Unit)? = null
    
    // Photo navigation overlay for single photo mode
    private var photoNavigationOverlay: PhotoNavigationOverlay? = null
    
    // Reusable objects for performance
    private val tempMatrix = Matrix()
    private val tempRectF = RectF()
    private val tempPointF = PointF()
    
    // Glide request options optimized for editor
    private val editorImageOptions = RequestOptions()
        .fitCenter()
        .diskCacheStrategy(DiskCacheStrategy.ALL)
        .dontAnimate()
        .skipMemoryCache(true)  // CRITICAL: Don't cache in memory to prevent accumulation
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
            
            // Initialize transformation if we have photos loaded
            if (isPhotosLoaded) {
                initializeTransformation()
                
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
        
        if (photoBitmaps.isNotEmpty()) {
            // Save canvas state
            canvas.save()
            
            // Apply transformation matrix
            transformationManager?.getCurrentMatrix()?.let { matrix ->
                canvas.concat(matrix)
            }
            
            // Draw photos based on mode
            when (editorMode) {
                EditorMode.SINGLE -> drawSinglePhoto(canvas)
                EditorMode.COLLAGE -> drawCollage(canvas)
            }
            
            // Restore canvas state
            canvas.restore()
            
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
     * Set photos for the editor
     */
    fun setPhotos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        
        // Clear existing data
        clearPhotos()
        
        // Set mode based on photo count
        editorMode = if (uris.size == 1) EditorMode.SINGLE else EditorMode.COLLAGE
        photoUris.addAll(uris)
        
        // Initialize target list
        currentTargets = MutableList(uris.size) { null }
        
        // Load photos
        uris.forEachIndexed { index, uri ->
            loadPhotoAtIndex(uri, index)
        }
    }
    
    /**
     * Load a single photo at specific index
     */
    private fun loadPhotoAtIndex(uri: Uri, index: Int) {
        // Clear previous target if exists
        currentTargets.getOrNull(index)?.let { target ->
            try {
                Glide.with(context.applicationContext).clear(target)
            } catch (e: Exception) {
                // Context may be invalid, ignore
            }
        }
        
        // Create new target and store reference
        val target = object : CustomTarget<Bitmap>() {
            override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                // Ensure we still have space for this photo
                if (index < currentTargets.size) {
                    // Extend lists if necessary
                    while (photoBitmaps.size <= index) {
                        photoBitmaps.add(resource) // Temporary, will be replaced
                        photoPositions.add(RectF())
                    }
                    
                    photoBitmaps[index] = resource
                    
                    // For single photo mode, initialize navigation overlay immediately
                    if (editorMode == EditorMode.SINGLE && index == 0 && photoNavigationOverlay != null) {
                        val photoUri = if (index < photoUris.size) photoUris[index] else null
                        photoUri?.let { uri ->
                            android.util.Log.d("UnifiedPhotoEditorView", "First photo loaded in single mode, initializing navigation overlay")
                            photoNavigationOverlay?.setPhoto(uri, resource.width.toFloat(), resource.height.toFloat())
                        }
                    }
                    
                    // Check if all photos are loaded
                    checkAllPhotosLoaded()
                }
            }
            
            override fun onLoadCleared(placeholder: Drawable?) {
                // DO NOT recycle bitmap - let Glide handle its own lifecycle!
                // Just clear the reference to allow garbage collection
                if (index < photoBitmaps.size) {
                    photoBitmaps[index] = null
                }
                invalidate()
            }
        }
        
        currentTargets[index] = target
        
        // Load image into the custom target using application context to prevent leaks
        Glide.with(context.applicationContext)
            .asBitmap()
            .load(uri)
            .apply(editorImageOptions)
            .into(target)
    }
    
    /**
     * Check if all photos are loaded and initialize if so
     */
    private fun checkAllPhotosLoaded() {
        val loadedCount = photoBitmaps.count { it != null }
        if (loadedCount == photoUris.size) {
            isPhotosLoaded = true
            calculatePhotoLayout()
            
            if (width > 0 && height > 0) {
                initializeTransformation()
            }
            
            invalidate()
        }
    }
    
    /**
     * Calculate layout for photos based on mode
     */
    private fun calculatePhotoLayout() {
        when (editorMode) {
            EditorMode.SINGLE -> calculateSinglePhotoLayout()
            EditorMode.COLLAGE -> calculateCollageLayout()
        }
    }
    
    /**
     * Calculate layout for single photo mode
     */
    private fun calculateSinglePhotoLayout() {
        if (photoBitmaps.isEmpty()) return
        
        photoBitmaps[0]?.let { bitmap ->
            totalCollageWidth = bitmap.width.toFloat()
            totalCollageHeight = bitmap.height.toFloat()
        }
        
        // Single photo fills entire canvas
        photoPositions.clear()
        photoPositions.add(RectF(0f, 0f, totalCollageWidth, totalCollageHeight))
    }
    
    /**
     * Calculate layout for collage mode - horizontal array
     * First photo determines the height for consistent scaling behavior
     */
    private fun calculateCollageLayout() {
        if (photoBitmaps.isEmpty()) return
        
        // Clear existing scaled bitmaps
        collageScaledBitmaps.forEach { if (!it.isRecycled) it.recycle() }
        collageScaledBitmaps.clear()
        
        // Capture first photo dimensions for proper scaling calculation
        photoBitmaps[0]?.let { firstPhoto ->
            firstPhotoWidth = firstPhoto.width.toFloat()
            firstPhotoHeight = firstPhoto.height.toFloat()
        }
        
        // Use first photo's height as uniform height for collage
        // This ensures first photo scaling matches single photo view behavior
        uniformPhotoHeight = firstPhotoHeight
        
        // Calculate total width and positions while pre-scaling bitmaps
        var currentX = 0f
        totalCollageWidth = 0f
        totalCollageHeight = uniformPhotoHeight
        
        photoPositions.clear()
        
        photoBitmaps.forEach { bitmap ->
            bitmap?.let {
                // Calculate width maintaining aspect ratio
                val aspectRatio = it.width.toFloat() / it.height.toFloat()
                val scaledWidth = uniformPhotoHeight * aspectRatio
                
                // Pre-scale bitmap for optimal performance
                val scaledBitmap = Bitmap.createScaledBitmap(
                    it,
                    scaledWidth.toInt(),
                    uniformPhotoHeight.toInt(),
                    true
                )
                collageScaledBitmaps.add(scaledBitmap)
                
                // Add position (for the pre-scaled bitmap)
                photoPositions.add(RectF(
                    currentX, 
                    0f, 
                    currentX + scaledWidth, 
                    uniformPhotoHeight
                ))
                
                currentX += scaledWidth + COLLAGE_PHOTO_GAP
            }
        }
        
        totalCollageWidth = currentX - COLLAGE_PHOTO_GAP // Remove last gap
    }
    
    /**
     * Initialize transformation manager
     */
    private fun initializeTransformation() {
        transformationManager = UnifiedTransformationManager().apply {
            initialize(
                totalWidth = totalCollageWidth,
                totalHeight = totalCollageHeight,
                viewportWidth = width.toFloat(),
                viewportHeight = height.toFloat(),
                mode = editorMode,
                firstPhotoWidth = firstPhotoWidth,
                firstPhotoHeight = firstPhotoHeight
            )
        }
        
        // Ensure proper initial bounds after initialization
        checkAndPerformSnapBack()
    }
    
    /**
     * Draw single photo
     */
    private fun drawSinglePhoto(canvas: Canvas) {
        if (photoBitmaps.isNotEmpty()) {
            photoBitmaps[0]?.let { bitmap ->
                canvas.drawBitmap(bitmap, 0f, 0f, imagePaint)
            }
        }
    }
    
    /**
     * Draw collage using pre-scaled bitmaps for optimal performance
     */
    private fun drawCollage(canvas: Canvas) {
        collageScaledBitmaps.forEachIndexed { index, scaledBitmap ->
            if (index < photoPositions.size) {
                val position = photoPositions[index]
                
                // Draw pre-scaled bitmap directly - no memory allocations!
                canvas.drawBitmap(scaledBitmap, position.left, position.top, imagePaint)
            }
        }
    }
    
    /**
     * Handle touch events
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isPhotosLoaded || !::gestureHandler.isInitialized) {
            return super.onTouchEvent(event)
        }
        
        // On touch down, stop any ongoing momentum scrolling
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            stopFling()
        }
        
        return gestureHandler.onTouchEvent(event) || super.onTouchEvent(event)
    }
    
    // EditorGestureListener implementations
    
    override fun onScaleBegin(focusX: Float, focusY: Float): Boolean {
        stopAllAnimations()
        return true
    }
    
    override fun onScale(scaleFactor: Float, focusX: Float, focusY: Float): Boolean {
        return transformationManager?.applyScale(scaleFactor, focusX, focusY)?.also {
            if (it) {
                invalidate()
                notifyViewportChanged()
            }
        } ?: false
    }
    
    override fun onScaleEnd() {
        checkAndPerformSnapBack()
    }
    
    override fun onPanBegin(startX: Float, startY: Float): Boolean {
        stopFling()
        return true
    }
    
    override fun onPan(deltaX: Float, deltaY: Float): Boolean {
        val contentBounds = transformationManager?.getTransformedContentBounds() ?: return false
        val constrainedDelta = boundsCalculator.constrainTranslationDelta(
            contentBounds, deltaX, deltaY, allowElastic = true
        )
        
        return transformationManager?.applyTranslation(constrainedDelta.x, constrainedDelta.y, constrainBounds = true)?.also {
            if (it) {
                invalidate()
                notifyViewportChanged()
            }
        } ?: false
    }
    
    override fun onPanEnd() {
        checkAndPerformSnapBack()
    }
    
    override fun onFling(velocityX: Float, velocityY: Float): Boolean {
        val velocity = sqrt(velocityX * velocityX + velocityY * velocityY)
        if (velocity < MIN_FLING_VELOCITY) {
            return false
        }
        
        // Get current translation for scroller start position
        val currentTranslation = transformationManager?.getCurrentTranslation() ?: return false
        val startX = currentTranslation.x.toInt()
        val startY = currentTranslation.y.toInt()
        
        // Calculate proper scroll bounds based on actual content
        val contentBounds = transformationManager?.getTransformedContentBounds() ?: return false
        val viewport = transformationManager?.getViewportDimensions() ?: return false
        
        // Calculate min/max scroll positions
        val minX = if (contentBounds.width() <= viewport.x) {
            // Content narrower than viewport - center it
            ((viewport.x - contentBounds.width()) / 2f).toInt()
        } else {
            // Content wider than viewport - allow scrolling to see right edge
            (viewport.x - contentBounds.width()).toInt()
        }
        val maxX = 0 // Left edge of content at left edge of viewport
        
        val minY = if (contentBounds.height() <= viewport.y) {
            // Content shorter than viewport - center it
            ((viewport.y - contentBounds.height()) / 2f).toInt()
        } else {
            // Content taller than viewport - allow scrolling to see bottom edge
            (viewport.y - contentBounds.height()).toInt()
        }
        val maxY = 0 // Top edge of content at top edge of viewport
        
        scroller.fling(
            startX, startY,              // start position
            velocityX.toInt(), velocityY.toInt(),  // velocity
            minX, maxX,                  // x bounds (proper content bounds)
            minY, maxY                   // y bounds (proper content bounds)
        )
        
        // Initialize last scroll position
        lastScrollX = startX
        lastScrollY = startY
        
        // Start the animation
        invalidate()
        return true
    }
    
    override fun onDoubleTap(x: Float, y: Float): Boolean {
        val currentZoom = transformationManager?.getZoomLevel() ?: return false
        val targetZoom = if (currentZoom > 1.5f) 1.0f else DOUBLE_TAP_ZOOM_FACTOR
        
        animateZoomToLevel(targetZoom, x, y)
        return true
    }
    
    /**
     * Override computeScroll for OverScroller momentum
     */
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val newScrollX = scroller.currX
            val newScrollY = scroller.currY
            
            val deltaX = newScrollX - lastScrollX
            val deltaY = newScrollY - lastScrollY
            
            if (abs(deltaX) > 0 || abs(deltaY) > 0) {
                transformationManager?.applyTranslation(deltaX.toFloat(), deltaY.toFloat(), constrainBounds = true)
                invalidate()
                notifyViewportChanged()
            }
            
            lastScrollX = newScrollX
            lastScrollY = newScrollY
            
            // Continue animation
            invalidate()
        } else {
            // Animation finished, reset gesture state
            if (::gestureHandler.isInitialized) {
                gestureHandler.reset()
            }
        }
    }
    
    /**
     * Stop momentum scrolling
     */
    private fun stopFling() {
        if (!scroller.isFinished) {
            scroller.abortAnimation()
        }
    }
    
    /**
     * Animate zoom to specific level
     */
    private fun animateZoomToLevel(targetZoom: Float, focusX: Float, focusY: Float) {
        val startZoom = transformationManager?.getZoomLevel() ?: return
        
        ValueAnimator.ofFloat(startZoom, targetZoom).apply {
            duration = 300
            interpolator = DecelerateInterpolator()
            
            addUpdateListener { animator ->
                val currentZoom = animator.animatedValue as Float
                transformationManager?.setZoomLevel(currentZoom, focusX, focusY)
                invalidate()
                notifyViewportChanged()
            }
            
            start()
        }
    }
    
    /**
     * Simple snap-back to bounds (OverScroller will handle this naturally)
     */
    private fun checkAndPerformSnapBack() {
        // For now, just rely on transformation manager's built-in constraints
        // OverScroller with proper bounds will handle most cases naturally
    }
    
    /**
     * Stop all animations
     */
    private fun stopAllAnimations() {
        stopFling()
    }
    
    /**
     * Draw debug information
     */
    private fun drawDebugInfo(canvas: Canvas) {
        val contentBounds = transformationManager?.getTransformedContentBounds() ?: return
        val viewport = boundsCalculator.getViewportBounds()
        
        // Draw content bounds
        debugPaint.color = Color.GREEN
        canvas.drawRect(contentBounds, debugPaint)
        
        // Draw viewport bounds
        debugPaint.color = Color.RED
        canvas.drawRect(viewport, debugPaint)
        
        // Draw transformation info
        debugPaint.color = Color.WHITE
        debugPaint.textSize = 24f
        val info = "Mode: $editorMode, Scale: %.2f, Photos: ${photoBitmaps.size}".format(
            transformationManager?.getCurrentScale() ?: 0f
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
            android.util.Log.w("UnifiedPhotoEditorView", "Slow frame: ${frameTime}ms")
        }
        
        // Log fps every 60 frames
        if (frameCount % 60 == 0) {
            val currentTime = System.currentTimeMillis()
            if (lastFrameTime > 0) {
                val fps = 60000f / (currentTime - lastFrameTime)
                android.util.Log.d("UnifiedPhotoEditorView", "FPS: $fps")
            }
            lastFrameTime = currentTime
        }
    }
    
    /**
     * Reset all transformations to initial state
     */
    fun resetTransformations() {
        stopAllAnimations()
        transformationManager?.resetToInitial()
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
     * Get current photo URIs
     */
    fun getCurrentPhotoUris(): List<Uri> = photoUris.toList()
    
    /**
     * Check if photos are loaded
     */
    fun arePhotosLoaded(): Boolean = isPhotosLoaded
    
    /**
     * Get current editor mode
     */
    fun getEditorMode(): EditorMode = editorMode
    
    /**
     * Get current viewport bounds relative to content for navigation overlay
     */
    fun getViewportBounds(): RectF? {
        if (!isPhotosLoaded || transformationManager == null) {
            return null
        }
        
        // Get the current transformation matrix
        val transformationMatrix = transformationManager?.getCurrentMatrix() ?: return null
        
        // Create inverse matrix to map screen coordinates to content coordinates
        val inverseMatrix = Matrix()
        if (!transformationMatrix.invert(inverseMatrix)) {
            android.util.Log.d("ViewportDebug", "Matrix is not invertible")
            return null // Matrix is not invertible
        }
        
        // Debug logging
        val currentScale = transformationManager?.getCurrentScale() ?: 1f
        val currentTranslation = transformationManager?.getCurrentTranslation() ?: PointF(0f, 0f)
        android.util.Log.d("ViewportDebug", "Editor view size: ${width}x${height}")
        android.util.Log.d("ViewportDebug", "Collage size: ${totalCollageWidth}x${totalCollageHeight}")
        android.util.Log.d("ViewportDebug", "Current scale: $currentScale")
        android.util.Log.d("ViewportDebug", "Current translation: $currentTranslation")
        
        // Map viewport corners to content coordinates using inverse matrix
        val viewportCorners = floatArrayOf(
            0f, 0f,                                    // Top-left corner
            width.toFloat(), height.toFloat()          // Bottom-right corner
        )
        
        // Transform screen coordinates to content coordinates
        inverseMatrix.mapPoints(viewportCorners)
        
        // Extract the transformed coordinates
        val contentLeft = viewportCorners[0]
        val contentTop = viewportCorners[1]
        val contentRight = viewportCorners[2]
        val contentBottom = viewportCorners[3]
        
        android.util.Log.d("ViewportDebug", "Matrix transformation: ($contentLeft, $contentTop, $contentRight, $contentBottom)")
        
        // Calculate intersection of viewport with actual content area
        val left = maxOf(0f, contentLeft)
        val top = maxOf(0f, contentTop)  
        val right = minOf(totalCollageWidth, contentRight)
        val bottom = minOf(totalCollageHeight, contentBottom)
        
        // If the intersection is empty (viewport doesn't overlap content), return null
        if (left >= right || top >= bottom) {
            android.util.Log.d("ViewportDebug", "No content visible in viewport")
            return null
        }
        
        android.util.Log.d("ViewportDebug", "Final viewport bounds: ($left, $top, $right, $bottom)")
        
        return RectF(left, top, right, bottom)
    }
    
    /**
     * Get total collage dimensions for navigation overlay
     */
    fun getCollageDimensions(): PointF {
        return PointF(totalCollageWidth, totalCollageHeight)
    }
    
    /**
     * Get rendered collage dimensions (how big collage appears on screen)
     * This accounts for current scale and is what the navigation overlay should use
     */
    fun getRenderedCollageDimensions(): PointF {
        val currentScale = transformationManager?.getCurrentScale() ?: 1f
        val renderedWidth = totalCollageWidth * currentScale
        val renderedHeight = totalCollageHeight * currentScale
        android.util.Log.d("RenderedDimensions", "Original: ${totalCollageWidth}x${totalCollageHeight}, Scale: $currentScale, Rendered: ${renderedWidth}x${renderedHeight}")
        return PointF(renderedWidth, renderedHeight)
    }
    
    /**
     * Navigate to specific point in collage (for navigation overlay)
     */
    fun navigateToPoint(collageX: Float, collageY: Float) {
        if (!isPhotosLoaded || transformationManager == null) return
        
        val currentScale = transformationManager?.getCurrentScale() ?: return
        
        // Calculate desired translation to center the point in viewport
        val targetTranslationX = (width / 2f) - (collageX * currentScale)
        val targetTranslationY = (height / 2f) - (collageY * currentScale)
        
        // Apply translation with bounds checking
        val currentTranslation = transformationManager?.getCurrentTranslation() ?: return
        val deltaX = targetTranslationX - currentTranslation.x
        val deltaY = targetTranslationY - currentTranslation.y
        
        transformationManager?.applyTranslation(deltaX, deltaY, constrainBounds = true)
        invalidate()
        
        // Perform snap back if needed
        checkAndPerformSnapBack()
        
        // Notify viewport change
        notifyViewportChanged()
    }
    
    /**
     * Set viewport change listener for navigation overlay
     */
    fun setViewportChangeListener(listener: ((RectF?, Boolean) -> Unit)?) {
        viewportChangeListener = listener
    }
    
    /**
     * Set photo navigation overlay for single photo mode
     */
    fun setPhotoNavigationOverlay(overlay: PhotoNavigationOverlay, photoUri: Uri) {
        android.util.Log.d("UnifiedPhotoEditorView", "setPhotoNavigationOverlay called with URI: $photoUri")
        photoNavigationOverlay = overlay
        
        // Set up navigation click listener
        overlay.setOnNavigationClickListener { imageX, imageY ->
            navigateToPosition(imageX, imageY)
        }
        
        // If we already have the photo loaded, initialize the overlay
        if (photoBitmaps.isNotEmpty() && photoBitmaps[0] != null) {
            val bitmap = photoBitmaps[0]!!
            android.util.Log.d("UnifiedPhotoEditorView", "Photo already loaded, setting overlay photo: ${bitmap.width}x${bitmap.height}")
            overlay.setPhoto(photoUri, bitmap.width.toFloat(), bitmap.height.toFloat())
            updatePhotoNavigationOverlayViewport()
        } else {
            android.util.Log.d("UnifiedPhotoEditorView", "Photo not loaded yet, will set overlay when photo loads")
        }
    }
    
    /**
     * Navigate to specific position in single photo mode
     */
    private fun navigateToPosition(imageX: Float, imageY: Float) {
        if (editorMode != EditorMode.SINGLE || transformationManager == null) return
        
        val currentZoom = transformationManager!!.getZoomLevel()
        val currentTranslation = transformationManager!!.getCurrentTranslation()
        
        // Calculate the position to center the clicked point in the viewport
        val targetTranslationX = (width / 2f) - (imageX * currentZoom)
        val targetTranslationY = (height / 2f) - (imageY * currentZoom)
        
        // Calculate delta from current position
        val deltaX = targetTranslationX - currentTranslation.x
        val deltaY = targetTranslationY - currentTranslation.y
        
        // Apply the translation delta
        transformationManager!!.applyTranslation(deltaX, deltaY)
        
        // Update overlay viewport
        updatePhotoNavigationOverlayViewport()
        
        invalidate()
    }
    
    /**
     * Update photo navigation overlay with current viewport bounds
     */
    private fun updatePhotoNavigationOverlayViewport() {
        if (editorMode != EditorMode.SINGLE || photoNavigationOverlay == null || photoBitmaps.isEmpty()) return
        
        val bitmap = photoBitmaps[0] ?: return
        val transformationManager = this.transformationManager ?: return
        
        // Check zoom level to determine overlay visibility
        val currentZoom = transformationManager.getZoomLevel()
        val shouldShowOverlay = currentZoom > 1.0f
        
        android.util.Log.d("UnifiedPhotoEditorView", "updatePhotoNavigationOverlayViewport: zoom=$currentZoom, shouldShow=$shouldShowOverlay")
        
        // Let the overlay handle its own visibility through fade animations
        // Don't set visibility directly - it conflicts with alpha animations
        
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
            photoNavigationOverlay?.updateViewport(tempRectF, shouldShowOverlay)
        }
    }
    
    /**
     * Notify viewport change to listener
     */
    private fun notifyViewportChanged() {
        if (editorMode == EditorMode.COLLAGE && viewportChangeListener != null) {
            val viewportBounds = getViewportBounds()
            val isVisible = viewportBounds != null
            viewportChangeListener?.invoke(viewportBounds, isVisible)
        } else if (editorMode == EditorMode.SINGLE) {
            // Update photo navigation overlay
            updatePhotoNavigationOverlayViewport()
        }
    }
    
    /**
     * Clear all photos and reset state
     */
    private fun clearPhotos() {
        stopAllAnimations()
        
        // CRITICAL: Clear Glide's memory cache first to prevent bitmap accumulation
        try {
            Glide.get(context.applicationContext).clearMemory()
        } catch (e: Exception) {
            // Context may be invalid, ignore
        }
        
        // Clear Glide targets
        currentTargets.forEach { target ->
            target?.let {
                try {
                    Glide.with(context.applicationContext).clear(it)
                } catch (e: Exception) {
                    // Context may be invalid, ignore
                }
            }
        }
        
        // Only recycle OUR pre-scaled bitmaps for collage mode
        // Do NOT recycle photoBitmaps - let Glide and Android handle those safely
        collageScaledBitmaps.forEach { bitmap ->
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
        collageScaledBitmaps.clear()
        
        // Clear all data (just clear references, let GC handle Glide bitmaps)
        photoBitmaps.clear()
        photoUris.clear()
        photoPositions.clear()
        currentTargets.clear()
        
        isPhotosLoaded = false
        isFirstLayout = true
        transformationManager = null
        
        invalidate()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearPhotos()
    }
}
