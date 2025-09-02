package com.photocollage.glide.ui.edit

import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.max
import kotlin.math.min

/**
 * Matrix state management for photo editor transformations.
 * Handles scale, translate, and rotation operations with performance optimization.
 * 
 * Performance targets:
 * - Matrix calculations: <1ms per operation
 * - Memory allocations: Zero during transformations
 * - Transformation accuracy: Sub-pixel precision
 */
class TransformationManager {
    
    companion object {
        private const val MAX_SCALE = 4.0f
        private const val DEFAULT_SCALE = 1.0f
        
        // Animation constants
        private const val ZOOM_ANIMATION_DURATION = 300L
        private const val SNAP_BACK_THRESHOLD = 50f
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
    
    // Image and viewport dimensions
    private var imageWidth = 0f
    private var imageHeight = 0f
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    
    // Current transformation values
    private var currentScale = DEFAULT_SCALE
    private var currentTranslationX = 0f
    private var currentTranslationY = 0f
    private var currentRotation = 0f
    
    // Initial fit-to-screen values
    private var initialScale = DEFAULT_SCALE
    private var initialTranslationX = 0f
    private var initialTranslationY = 0f
    
    /**
     * Initialize with image and viewport dimensions
     */
    fun initialize(
        imageWidth: Float,
        imageHeight: Float,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
        
        calculateInitialTransformation()
        resetToInitial()
    }
    
    /**
     * Calculate initial fit-to-screen transformation
     */
    private fun calculateInitialTransformation() {
        if (imageWidth <= 0 || imageHeight <= 0 || viewportWidth <= 0 || viewportHeight <= 0) {
            return
        }
        
        // Calculate scale to fit viewport while maintaining aspect ratio (entire image visible)
        val scaleX = viewportWidth / imageWidth
        val scaleY = viewportHeight / imageHeight
        initialScale = min(scaleX, scaleY)
        
        // Calculate translation to center the image
        val scaledImageWidth = imageWidth * initialScale
        val scaledImageHeight = imageHeight * initialScale
        
        initialTranslationX = (viewportWidth - scaledImageWidth) / 2f
        initialTranslationY = (viewportHeight - scaledImageHeight) / 2f
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
        
        // Constrain scale within limits (never smaller than initial fill scale)
        val constrainedScale = newScale.coerceIn(initialScale, MAX_SCALE)
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
     * Constrain translation to keep image edges within viewport bounds
     * For fill mode: ensure at least 2 borders always touch screen edges
     */
    private fun constrainTranslation(translationX: Float, translationY: Float): PointF {
        val scaledImageWidth = imageWidth * currentScale
        val scaledImageHeight = imageHeight * currentScale
        
        val constrainedX = when {
            scaledImageWidth <= viewportWidth -> {
                // Image narrower than viewport - center horizontally
                (viewportWidth - scaledImageWidth) / 2f
            }
            translationX > 0 -> {
                // Prevent left edge from going beyond left viewport edge
                0f
            }
            translationX + scaledImageWidth < viewportWidth -> {
                // Prevent right edge from going beyond right viewport edge
                viewportWidth - scaledImageWidth
            }
            else -> translationX
        }
        
        val constrainedY = when {
            scaledImageHeight <= viewportHeight -> {
                // Image shorter than viewport - center vertically
                (viewportHeight - scaledImageHeight) / 2f
            }
            translationY > 0 -> {
                // Prevent top edge from going beyond top viewport edge
                0f
            }
            translationY + scaledImageHeight < viewportHeight -> {
                // Prevent bottom edge from going beyond bottom viewport edge
                viewportHeight - scaledImageHeight
            }
            else -> translationY
        }
        
        tempPointF.set(constrainedX, constrainedY)
        return tempPointF
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
        
        // Apply rotation if needed (around image center)
        if (currentRotation != 0f) {
            val centerX = currentTranslationX + (imageWidth * currentScale) / 2f
            val centerY = currentTranslationY + (imageHeight * currentScale) / 2f
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
     * Get transformed image bounds
     */
    fun getTransformedImageBounds(): RectF {
        tempRectF.set(0f, 0f, imageWidth, imageHeight)
        currentMatrix.mapRect(tempRectF)
        return tempRectF
    }
    
    /**
     * Map point from screen coordinates to image coordinates
     */
    fun mapPointToImage(screenX: Float, screenY: Float): PointF {
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
     * Map point from image coordinates to screen coordinates
     */
    fun mapPointToScreen(imageX: Float, imageY: Float): PointF {
        val points = floatArrayOf(imageX, imageY)
        currentMatrix.mapPoints(points)
        tempPointF.set(points[0], points[1])
        return tempPointF
    }
    
    /**
     * Check if image can be zoomed in more
     */
    fun canZoomIn(): Boolean = currentScale < MAX_SCALE
    
    /**
     * Check if image can be zoomed out more
     */
    fun canZoomOut(): Boolean = currentScale > initialScale
    
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
     * Check if image is at initial (fit-to-screen) state
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
        val targetScale = initialScale * zoomLevel.coerceIn(1.0f, MAX_SCALE / initialScale)
        val scaleFactor = targetScale / currentScale
        
        val centerX = focusX ?: (viewportWidth / 2f)
        val centerY = focusY ?: (viewportHeight / 2f)
        
        return applyScale(scaleFactor, centerX, centerY)
    }
    
    /**
     * Set absolute translation values
     */
    fun setTranslation(translationX: Float, translationY: Float, constrainBounds: Boolean = true): Boolean {
        var newTranslationX = translationX
        var newTranslationY = translationY
        
        if (constrainBounds) {
            val constrainedTranslation = constrainTranslation(newTranslationX, newTranslationY)
            newTranslationX = constrainedTranslation.x
            newTranslationY = constrainedTranslation.y
        }
        
        if (newTranslationX == currentTranslationX && newTranslationY == currentTranslationY) {
            return false
        }
        
        currentTranslationX = newTranslationX
        currentTranslationY = newTranslationY
        
        updateMatrix()
        return true
    }
}