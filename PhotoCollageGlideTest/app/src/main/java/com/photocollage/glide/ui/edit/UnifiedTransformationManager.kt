package com.photocollage.glide.ui.edit

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Unified transformation manager for both single photo and collage modes.
 * Handles scale, translate, and rotation operations with mode-specific constraints.
 * 
 * Performance targets:
 * - Matrix calculations: <1ms per operation
 * - Memory allocations: Zero during transformations
 * - Transformation accuracy: Sub-pixel precision
 */
class UnifiedTransformationManager {
    
    companion object {
        private const val DEFAULT_SCALE = 1.0f
        
        // Mode-specific maximum scales
        private const val SINGLE_MAX_SCALE = 4.0f
        private const val COLLAGE_MAX_SCALE_FACTOR = 6.0f // 600% maximum zoom for collage
        
        // Animation constants
        private const val ZOOM_ANIMATION_DURATION = 300L
        private const val SNAP_BACK_THRESHOLD = 50f
        
        // Overscroll constants
        private const val OVERSCROLL_FACTOR = 0.1f // 10% of viewport can be overscrolled
        private const val ELASTIC_RESISTANCE = 0.3f // Resistance when overscrolling
    }
    
    // Current transformation matrix
    private val currentMatrix = Matrix()
    
    // Temporary matrices to avoid allocations
    private val tempMatrix = Matrix()
    private val tempMatrix2 = Matrix()
    
    // Reusable objects for calculations
    private val tempRectF = RectF()
    private val tempPointF = PointF()
    private val tempFloatArray = FloatArray(9)
    
    // Content and viewport dimensions
    private var contentWidth = 0f
    private var contentHeight = 0f
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    
    // Current mode
    private var editorMode = UnifiedPhotoEditorView.EditorMode.SINGLE
    
    // First photo dimensions for collage scaling
    private var firstPhotoWidth = 0f
    private var firstPhotoHeight = 0f
    
    // Current transformation values
    private var currentScale = DEFAULT_SCALE
    private var currentTranslationX = 0f
    private var currentTranslationY = 0f
    private var currentRotation = 0f
    
    // Initial fit-to-screen values
    private var initialScale = DEFAULT_SCALE
    private var initialTranslationX = 0f
    private var initialTranslationY = 0f
    private var maxScale = SINGLE_MAX_SCALE
    
    /**
     * Initialize with content and viewport dimensions
     */
    fun initialize(
        totalWidth: Float,
        totalHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        mode: UnifiedPhotoEditorView.EditorMode,
        firstPhotoWidth: Float = 0f,
        firstPhotoHeight: Float = 0f
    ) {
        this.contentWidth = totalWidth
        this.contentHeight = totalHeight
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        this.editorMode = mode
        this.firstPhotoWidth = firstPhotoWidth
        this.firstPhotoHeight = firstPhotoHeight
        
        calculateInitialTransformation()
        resetToInitial()
    }
    
    /**
     * Calculate initial fit-to-screen transformation based on mode
     */
    private fun calculateInitialTransformation() {
        if (contentWidth <= 0 || contentHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return
        }
        
        when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                // Single photo: fit entire image to screen
                val scaleX = viewportWidth / contentWidth
                val scaleY = viewportHeight / contentHeight
                initialScale = min(scaleX, scaleY)
                maxScale = SINGLE_MAX_SCALE
            }
            
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // Collage: scale so first photo fits entirely within viewport (like single photo view)
                // Use first photo dimensions to calculate scale (not entire collage dimensions)
                if (firstPhotoWidth > 0 && firstPhotoHeight > 0) {
                    val scaleX = viewportWidth / firstPhotoWidth
                    val scaleY = viewportHeight / firstPhotoHeight
                    initialScale = min(scaleX, scaleY)
                } else {
                    // Fallback to old behavior if first photo dimensions not available
                    initialScale = viewportHeight / contentHeight
                }
                // Maximum zoom is 600% of this initial scale
                maxScale = initialScale * COLLAGE_MAX_SCALE_FACTOR
            }
        }
        
        // Calculate translation based on mode
        val scaledContentWidth = contentWidth * initialScale
        val scaledContentHeight = contentHeight * initialScale
        
        when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                // Single photo: center the content
                initialTranslationX = (viewportWidth - scaledContentWidth) / 2f
                initialTranslationY = (viewportHeight - scaledContentHeight) / 2f
            }
            
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // Collage: align first photo with viewport (left edge at left border)
                // Since we scaled to match height, just align to left edge
                initialTranslationX = 0f
                initialTranslationY = (viewportHeight - scaledContentHeight) / 2f
            }
        }
    }
    
    /**
     * Reset transformation to initial fit-to-screen state
     */
    fun resetToInitial() {
        currentScale = initialScale
        currentTranslationX = initialTranslationX
        currentTranslationY = initialTranslationY
        currentRotation = 0f
        
        updateMatrix()
    }
    
    /**
     * Apply scale transformation around focus point
     */
    fun applyScale(scaleFactor: Float, focusX: Float, focusY: Float): Boolean {
        val newScale = currentScale * scaleFactor
        
        // Constrain scale within mode-specific limits
        val minScale = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> initialScale
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // For collage, minimum is when farthest borders touch screen edges
                // This ensures entire collage is always visible at minimum zoom
                val scaleXToFit = viewportWidth / contentWidth
                val scaleYToFit = viewportHeight / contentHeight
                min(scaleXToFit, scaleYToFit)
            }
        }
        
        val constrainedScale = newScale.coerceIn(minScale, maxScale)
        val actualScaleFactor = constrainedScale / currentScale
        
        if (actualScaleFactor == 1.0f) {
            return false // No change needed
        }
        
        // Calculate translation adjustment for focus point
        val deltaX = (focusX - currentTranslationX) * (actualScaleFactor - 1f)
        val deltaY = (focusY - currentTranslationY) * (actualScaleFactor - 1f)
        
        currentScale = constrainedScale
        currentTranslationX -= deltaX
        currentTranslationY -= deltaY
        
        // Apply boundary constraints after scaling
        val constrainedTranslation = constrainTranslation(currentTranslationX, currentTranslationY)
        currentTranslationX = constrainedTranslation.x
        currentTranslationY = constrainedTranslation.y
        
        updateMatrix()
        return true
    }
    
    /**
     * Apply translation with boundary constraints
     */
    fun applyTranslation(deltaX: Float, deltaY: Float, constrainBounds: Boolean = true): Boolean {
        if (deltaX == 0f && deltaY == 0f) {
            return false
        }
        
        var newTranslationX = currentTranslationX + deltaX
        var newTranslationY = currentTranslationY + deltaY
        
        if (constrainBounds) {
            val constrainedTranslation = constrainTranslation(newTranslationX, newTranslationY)
            newTranslationX = constrainedTranslation.x
            newTranslationY = constrainedTranslation.y
        }
        
        currentTranslationX = newTranslationX
        currentTranslationY = newTranslationY
        
        updateMatrix()
        return true
    }
    
    /**
     * Apply translation with elastic overscroll support for fling animations
     */
    fun applyTranslationWithOverscroll(deltaX: Float, deltaY: Float): Boolean {
        if (deltaX == 0f && deltaY == 0f) {
            return false
        }
        
        val newTranslationX = currentTranslationX + deltaX
        val newTranslationY = currentTranslationY + deltaY
        
        // Apply elastic overscroll - movement gets reduced when beyond bounds
        val elasticTranslation = applyElasticConstraints(newTranslationX, newTranslationY, deltaX, deltaY)
        
        currentTranslationX = elasticTranslation.x
        currentTranslationY = elasticTranslation.y
        
        updateMatrix()
        return true
    }
    
    /**
     * Apply elastic constraints that allow overscroll with resistance
     */
    private fun applyElasticConstraints(translationX: Float, translationY: Float, deltaX: Float, deltaY: Float): PointF {
        val scaledContentWidth = contentWidth * currentScale
        val scaledContentHeight = contentHeight * currentScale
        
        val overscrollLimitX = viewportWidth * OVERSCROLL_FACTOR
        val overscrollLimitY = viewportHeight * OVERSCROLL_FACTOR
        
        // Get normal constraints
        val normalConstraints = constrainTranslation(translationX, translationY)
        
        // Check if we're trying to go beyond normal bounds
        var elasticX = translationX
        var elasticY = translationY
        
        // Horizontal elastic constraints
        val minX = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                if (scaledContentWidth <= viewportWidth) {
                    (viewportWidth - scaledContentWidth) / 2f
                } else {
                    -overscrollLimitX
                }
            }
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                if (scaledContentWidth <= viewportWidth) {
                    (viewportWidth - scaledContentWidth) / 2f
                } else {
                    -overscrollLimitX
                }
            }
        }
        
        val maxX = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                if (scaledContentWidth <= viewportWidth) {
                    (viewportWidth - scaledContentWidth) / 2f
                } else {
                    viewportWidth - scaledContentWidth + overscrollLimitX
                }
            }
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                if (scaledContentWidth <= viewportWidth) {
                    (viewportWidth - scaledContentWidth) / 2f
                } else {
                    viewportWidth - scaledContentWidth + overscrollLimitX
                }
            }
        }
        
        // Apply elastic resistance when beyond normal bounds
        if (translationX < normalConstraints.x && deltaX < 0) {
            // Moving further left beyond bounds
            val overscrollAmount = normalConstraints.x - translationX
            val resistanceX = (overscrollAmount / overscrollLimitX).coerceIn(0f, 1f)
            elasticX = currentTranslationX + deltaX * (1f - resistanceX * ELASTIC_RESISTANCE)
            elasticX = elasticX.coerceAtLeast(minX)
        } else if (translationX > normalConstraints.x && deltaX > 0) {
            // Moving further right beyond bounds
            val overscrollAmount = translationX - normalConstraints.x
            val resistanceX = (overscrollAmount / overscrollLimitX).coerceIn(0f, 1f)
            elasticX = currentTranslationX + deltaX * (1f - resistanceX * ELASTIC_RESISTANCE)
            elasticX = elasticX.coerceAtMost(maxX)
        }
        
        // Vertical elastic constraints
        val minY = if (scaledContentHeight <= viewportHeight) {
            (viewportHeight - scaledContentHeight) / 2f
        } else {
            -overscrollLimitY
        }
        
        val maxY = if (scaledContentHeight <= viewportHeight) {
            (viewportHeight - scaledContentHeight) / 2f
        } else {
            viewportHeight - scaledContentHeight + overscrollLimitY
        }
        
        if (translationY < normalConstraints.y && deltaY < 0) {
            // Moving further up beyond bounds
            val overscrollAmount = normalConstraints.y - translationY
            val resistanceY = (overscrollAmount / overscrollLimitY).coerceIn(0f, 1f)
            elasticY = currentTranslationY + deltaY * (1f - resistanceY * ELASTIC_RESISTANCE)
            elasticY = elasticY.coerceAtLeast(minY)
        } else if (translationY > normalConstraints.y && deltaY > 0) {
            // Moving further down beyond bounds
            val overscrollAmount = translationY - normalConstraints.y
            val resistanceY = (overscrollAmount / overscrollLimitY).coerceIn(0f, 1f)
            elasticY = currentTranslationY + deltaY * (1f - resistanceY * ELASTIC_RESISTANCE)
            elasticY = elasticY.coerceAtMost(maxY)
        }
        
        tempPointF.set(elasticX, elasticY)
        return tempPointF
    }
    
    /**
     * Constrain translation based on mode and zoom level
     */
    private fun constrainTranslation(translationX: Float, translationY: Float): PointF {
        val scaledContentWidth = contentWidth * currentScale
        val scaledContentHeight = contentHeight * currentScale
        
        val constrainedX = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> {
                // Single photo constraints (existing logic)
                constrainSinglePhotoTranslationX(translationX, scaledContentWidth)
            }
            
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // Collage constraints - allow horizontal scrolling when zoomed
                constrainCollageTranslationX(translationX, scaledContentWidth)
            }
        }
        
        val constrainedY = when {
            scaledContentHeight <= viewportHeight -> {
                // Content shorter than viewport - center vertically
                (viewportHeight - scaledContentHeight) / 2f
            }
            translationY > 0 -> {
                // Prevent top edge from going beyond top viewport edge
                0f
            }
            translationY + scaledContentHeight < viewportHeight -> {
                // Prevent bottom edge from going beyond bottom viewport edge
                viewportHeight - scaledContentHeight
            }
            else -> translationY
        }
        
        tempPointF.set(constrainedX, constrainedY)
        return tempPointF
    }
    
    /**
     * Constrain X translation for single photo mode
     */
    private fun constrainSinglePhotoTranslationX(translationX: Float, scaledContentWidth: Float): Float {
        return when {
            scaledContentWidth <= viewportWidth -> {
                // Content narrower than viewport - center horizontally
                (viewportWidth - scaledContentWidth) / 2f
            }
            translationX > 0 -> {
                // Prevent left edge from going beyond left viewport edge
                0f
            }
            translationX + scaledContentWidth < viewportWidth -> {
                // Prevent right edge from going beyond right viewport edge
                viewportWidth - scaledContentWidth
            }
            else -> translationX
        }
    }
    
    /**
     * Constrain X translation for collage mode
     */
    private fun constrainCollageTranslationX(translationX: Float, scaledContentWidth: Float): Float {
        return when {
            scaledContentWidth <= viewportWidth -> {
                // Collage narrower than viewport - center horizontally
                (viewportWidth - scaledContentWidth) / 2f
            }
            translationX > 0 -> {
                // Prevent left edge of collage from going beyond left viewport edge
                0f
            }
            translationX + scaledContentWidth < viewportWidth -> {
                // Prevent right edge of collage from going beyond right viewport edge
                viewportWidth - scaledContentWidth
            }
            else -> translationX
        }
    }
    
    /**
     * Apply rotation around center point
     */
    fun applyRotation(rotationDegrees: Float): Boolean {
        if (rotationDegrees == 0f) {
            return false
        }
        
        currentRotation += rotationDegrees
        // Keep rotation in 0-360 range
        currentRotation = ((currentRotation % 360f) + 360f) % 360f
        
        updateMatrix()
        return true
    }
    
    /**
     * Update the transformation matrix
     */
    private fun updateMatrix() {
        currentMatrix.reset()
        
        // Scale first around origin to prevent displacement
        currentMatrix.postScale(currentScale, currentScale)
        
        // Then translate to final position
        currentMatrix.postTranslate(currentTranslationX, currentTranslationY)
        
        // Apply rotation if needed (around content center)
        if (currentRotation != 0f) {
            val centerX = currentTranslationX + (contentWidth * currentScale) / 2f
            val centerY = currentTranslationY + (contentHeight * currentScale) / 2f
            currentMatrix.postRotate(currentRotation, centerX, centerY)
        }
    }
    
    /**
     * Get current transformation matrix (read-only)
     */
    fun getCurrentMatrix(): Matrix {
        tempMatrix.set(currentMatrix)
        return tempMatrix
    }
    
    /**
     * Get transformed content bounds
     */
    fun getTransformedContentBounds(): RectF {
        tempRectF.set(0f, 0f, contentWidth, contentHeight)
        currentMatrix.mapRect(tempRectF)
        return tempRectF
    }
    
    /**
     * Map point from screen coordinates to content coordinates
     */
    fun mapPointToContent(screenX: Float, screenY: Float): PointF {
        val inverseMatrix = Matrix()
        if (currentMatrix.invert(inverseMatrix)) {
            val points = floatArrayOf(screenX, screenY)
            inverseMatrix.mapPoints(points)
            tempPointF.set(points[0], points[1])
        } else {
            tempPointF.set(screenX, screenY)
        }
        return tempPointF
    }
    
    /**
     * Map point from content coordinates to screen coordinates
     */
    fun mapPointToScreen(contentX: Float, contentY: Float): PointF {
        val points = floatArrayOf(contentX, contentY)
        currentMatrix.mapPoints(points)
        tempPointF.set(points[0], points[1])
        return tempPointF
    }
    
    /**
     * Check if content can be zoomed in more
     */
    fun canZoomIn(): Boolean = currentScale < maxScale
    
    /**
     * Check if content can be zoomed out more
     */
    fun canZoomOut(): Boolean {
        val minScale = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> initialScale
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // For collage, minimum shows entire collage (farthest borders touch screen)
                val scaleXToFit = viewportWidth / contentWidth
                val scaleYToFit = viewportHeight / contentHeight
                min(scaleXToFit, scaleYToFit)
            }
        }
        return currentScale > minScale
    }
    
    /**
     * Get current scale factor
     */
    fun getCurrentScale(): Float = currentScale
    
    /**
     * Get current translation
     */
    fun getCurrentTranslation(): PointF {
        tempPointF.set(currentTranslationX, currentTranslationY)
        return tempPointF
    }
    
    /**
     * Get current rotation in degrees
     */
    fun getCurrentRotation(): Float = currentRotation
    
    /**
     * Check if content is at initial (fit-to-screen) state
     */
    fun isAtInitialState(): Boolean {
        val scaleThreshold = 0.01f
        val translationThreshold = 1.0f
        val rotationThreshold = 0.1f
        
        return kotlin.math.abs(currentScale - initialScale) < scaleThreshold &&
               kotlin.math.abs(currentTranslationX - initialTranslationX) < translationThreshold &&
               kotlin.math.abs(currentTranslationY - initialTranslationY) < translationThreshold &&
               kotlin.math.abs(currentRotation) < rotationThreshold
    }
    
    /**
     * Get zoom level relative to initial scale (1.0 = fit-to-screen)
     */
    fun getZoomLevel(): Float = currentScale / initialScale
    
    /**
     * Set zoom level relative to initial scale
     */
    fun setZoomLevel(zoomLevel: Float, focusX: Float? = null, focusY: Float? = null): Boolean {
        val minZoomLevel = when (editorMode) {
            UnifiedPhotoEditorView.EditorMode.SINGLE -> 1.0f
            UnifiedPhotoEditorView.EditorMode.COLLAGE -> {
                // For collage, minimum shows entire collage (farthest borders touch screen)
                val scaleXToFit = viewportWidth / contentWidth
                val scaleYToFit = viewportHeight / contentHeight
                min(scaleXToFit, scaleYToFit) / initialScale
            }
        }
        
        val maxZoomLevel = maxScale / initialScale
        val targetScale = initialScale * zoomLevel.coerceIn(minZoomLevel, maxZoomLevel)
        val scaleFactor = targetScale / currentScale
        
        val centerX = focusX ?: (viewportWidth / 2f)
        val centerY = focusY ?: (viewportHeight / 2f)
        
        return applyScale(scaleFactor, centerX, centerY)
    }
    
    /**
     * Get content dimensions
     */
    fun getContentDimensions(): PointF {
        tempPointF.set(contentWidth, contentHeight)
        return tempPointF
    }
    
    /**
     * Get viewport dimensions
     */
    fun getViewportDimensions(): PointF {
        tempPointF.set(viewportWidth, viewportHeight)
        return tempPointF
    }
    
    /**
     * Get current editor mode
     */
    fun getEditorMode(): UnifiedPhotoEditorView.EditorMode = editorMode
    
    /**
     * Check if currently in overscroll state
     */
    fun isInOverscroll(): Boolean {
        val normalConstraints = constrainTranslation(currentTranslationX, currentTranslationY)
        val threshold = 1.0f // Small threshold for floating point comparison
        
        return kotlin.math.abs(currentTranslationX - normalConstraints.x) > threshold ||
               kotlin.math.abs(currentTranslationY - normalConstraints.y) > threshold
    }
    
    /**
     * Get the normal (constrained) position for snap-back animation
     */
    fun getNormalConstraints(): PointF {
        return constrainTranslation(currentTranslationX, currentTranslationY)
    }
    
    /**
     * Snap back to normal bounds with animation
     */
    fun snapToNormalBounds(): Boolean {
        val normalConstraints = constrainTranslation(currentTranslationX, currentTranslationY)
        val threshold = 1.0f
        
        val needsSnapX = kotlin.math.abs(currentTranslationX - normalConstraints.x) > threshold
        val needsSnapY = kotlin.math.abs(currentTranslationY - normalConstraints.y) > threshold
        
        if (needsSnapX || needsSnapY) {
            currentTranslationX = normalConstraints.x
            currentTranslationY = normalConstraints.y
            updateMatrix()
            return true
        }
        
        return false
    }
}