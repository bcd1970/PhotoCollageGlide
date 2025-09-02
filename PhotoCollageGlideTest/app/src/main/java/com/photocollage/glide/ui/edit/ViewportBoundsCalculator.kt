package com.photocollage.glide.ui.edit

import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Viewport bounds calculation utility for photo editor boundary constraints.
 * Prevents image edges from going beyond screen boundaries while maintaining smooth UX.
 * 
 * Performance targets:
 * - Bounds calculation: <0.5ms per operation
 * - Memory allocations: Zero during calculations
 * - Constraint enforcement: Real-time at 60fps
 */
class ViewportBoundsCalculator {
    
    companion object {
        // Elastic bounds constants - enhanced for momentum scrolling
        private const val ELASTIC_RESISTANCE_FACTOR = 0.3f
        private const val SNAP_BACK_THRESHOLD = 20f
        private const val MAX_ELASTIC_DISTANCE = 120f // Increased for better momentum feel
        
        // Animation constants
        private const val SPRING_BACK_DURATION = 250L
        private const val SPRING_DAMPING = 0.8f
        
        // Overscroll percentage of viewport (10% overscroll allowed)
        private const val OVERSCROLL_FACTOR = 0.1f
    }
    
    // Reusable objects to avoid allocations
    private val tempRectF = RectF()
    private val tempPointF = PointF()
    
    // Viewport dimensions
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private var viewportLeft = 0f
    private var viewportTop = 0f
    
    // Current bounds state
    private var isElasticBoundsEnabled = true
    private var isSnapBackEnabled = true
    
    /**
     * Initialize viewport dimensions
     */
    fun initialize(
        viewportLeft: Float = 0f,
        viewportTop: Float = 0f,
        viewportWidth: Float,
        viewportHeight: Float
    ) {
        this.viewportLeft = viewportLeft
        this.viewportTop = viewportTop
        this.viewportWidth = viewportWidth
        this.viewportHeight = viewportHeight
    }
    
    /**
     * Get viewport bounds as RectF
     */
    fun getViewportBounds(): RectF {
        tempRectF.set(
            viewportLeft,
            viewportTop,
            viewportLeft + viewportWidth,
            viewportTop + viewportHeight
        )
        return tempRectF
    }
    
    /**
     * Get maximum overscroll distance based on viewport size
     */
    fun getMaxOverscrollDistance(): Float {
        val maxViewportDimension = max(viewportWidth, viewportHeight)
        return min(MAX_ELASTIC_DISTANCE, maxViewportDimension * OVERSCROLL_FACTOR)
    }
    
    /**
     * Constrain translation delta to prevent image from going out of bounds
     */
    fun constrainTranslationDelta(
        currentImageBounds: RectF,
        proposedDeltaX: Float,
        proposedDeltaY: Float,
        allowElastic: Boolean = true
    ): PointF {
        
        val viewport = getViewportBounds()
        
        // Calculate proposed new image bounds
        val newImageBounds = RectF(currentImageBounds)
        newImageBounds.offset(proposedDeltaX, proposedDeltaY)
        
        var constrainedDeltaX = proposedDeltaX
        var constrainedDeltaY = proposedDeltaY
        
        // Constrain X translation
        constrainedDeltaX = constrainXTranslation(
            currentImageBounds,
            newImageBounds,
            viewport,
            proposedDeltaX,
            allowElastic
        )
        
        // Constrain Y translation
        constrainedDeltaY = constrainYTranslation(
            currentImageBounds,
            newImageBounds,
            viewport,
            proposedDeltaY,
            allowElastic
        )
        
        tempPointF.set(constrainedDeltaX, constrainedDeltaY)
        return tempPointF
    }
    
    /**
     * Constrain X-axis translation
     */
    private fun constrainXTranslation(
        currentBounds: RectF,
        newBounds: RectF,
        viewport: RectF,
        deltaX: Float,
        allowElastic: Boolean
    ): Float {
        
        return when {
            // Image is smaller than viewport - keep centered
            currentBounds.width() <= viewport.width() -> {
                val centerX = viewport.centerX()
                val targetCenterX = currentBounds.centerX() + deltaX
                val maxOffset = if (allowElastic) MAX_ELASTIC_DISTANCE else 0f
                
                val constrainedCenterX = targetCenterX.coerceIn(
                    centerX - maxOffset,
                    centerX + maxOffset
                )
                
                constrainedCenterX - currentBounds.centerX()
            }
            
            // Image is larger than viewport - constrain edges
            else -> {
                var constrainedDelta = deltaX
                
                // Check left edge constraint
                if (newBounds.left > viewport.left) {
                    val overshoot = newBounds.left - viewport.left
                    constrainedDelta = if (allowElastic && overshoot <= MAX_ELASTIC_DISTANCE) {
                        // Apply elastic resistance
                        deltaX - (overshoot * (1f - ELASTIC_RESISTANCE_FACTOR))
                    } else {
                        // Hard constraint
                        viewport.left - currentBounds.left
                    }
                }
                
                // Check right edge constraint
                if (newBounds.right < viewport.right) {
                    val overshoot = viewport.right - newBounds.right
                    constrainedDelta = if (allowElastic && overshoot <= MAX_ELASTIC_DISTANCE) {
                        // Apply elastic resistance
                        deltaX + (overshoot * (1f - ELASTIC_RESISTANCE_FACTOR))
                    } else {
                        // Hard constraint
                        viewport.right - currentBounds.right
                    }
                }
                
                constrainedDelta
            }
        }
    }
    
    /**
     * Constrain Y-axis translation
     */
    private fun constrainYTranslation(
        currentBounds: RectF,
        newBounds: RectF,
        viewport: RectF,
        deltaY: Float,
        allowElastic: Boolean
    ): Float {
        
        return when {
            // Image is smaller than viewport - keep centered
            currentBounds.height() <= viewport.height() -> {
                val centerY = viewport.centerY()
                val targetCenterY = currentBounds.centerY() + deltaY
                val maxOffset = if (allowElastic) MAX_ELASTIC_DISTANCE else 0f
                
                val constrainedCenterY = targetCenterY.coerceIn(
                    centerY - maxOffset,
                    centerY + maxOffset
                )
                
                constrainedCenterY - currentBounds.centerY()
            }
            
            // Image is larger than viewport - constrain edges
            else -> {
                var constrainedDelta = deltaY
                
                // Check top edge constraint
                if (newBounds.top > viewport.top) {
                    val overshoot = newBounds.top - viewport.top
                    constrainedDelta = if (allowElastic && overshoot <= MAX_ELASTIC_DISTANCE) {
                        // Apply elastic resistance
                        deltaY - (overshoot * (1f - ELASTIC_RESISTANCE_FACTOR))
                    } else {
                        // Hard constraint
                        viewport.top - currentBounds.top
                    }
                }
                
                // Check bottom edge constraint
                if (newBounds.bottom < viewport.bottom) {
                    val overshoot = viewport.bottom - newBounds.bottom
                    constrainedDelta = if (allowElastic && overshoot <= MAX_ELASTIC_DISTANCE) {
                        // Apply elastic resistance
                        deltaY + (overshoot * (1f - ELASTIC_RESISTANCE_FACTOR))
                    } else {
                        // Hard constraint
                        viewport.bottom - currentBounds.bottom
                    }
                }
                
                constrainedDelta
            }
        }
    }
    
    /**
     * Calculate snap-back translation to bring image within bounds
     */
    fun calculateSnapBackTranslation(currentImageBounds: RectF): PointF? {
        if (!isSnapBackEnabled) {
            return null
        }
        
        val viewport = getViewportBounds()
        var snapBackX = 0f
        var snapBackY = 0f
        var needsSnapBack = false
        
        // Calculate X snap-back
        when {
            currentImageBounds.width() <= viewport.width() -> {
                // Center small image
                val targetCenterX = viewport.centerX()
                val currentCenterX = currentImageBounds.centerX()
                if (abs(currentCenterX - targetCenterX) > SNAP_BACK_THRESHOLD) {
                    snapBackX = targetCenterX - currentCenterX
                    needsSnapBack = true
                }
            }
            
            currentImageBounds.left > viewport.left -> {
                // Snap left edge to viewport
                snapBackX = viewport.left - currentImageBounds.left
                needsSnapBack = true
            }
            
            currentImageBounds.right < viewport.right -> {
                // Snap right edge to viewport
                snapBackX = viewport.right - currentImageBounds.right
                needsSnapBack = true
            }
        }
        
        // Calculate Y snap-back
        when {
            currentImageBounds.height() <= viewport.height() -> {
                // Center small image
                val targetCenterY = viewport.centerY()
                val currentCenterY = currentImageBounds.centerY()
                if (abs(currentCenterY - targetCenterY) > SNAP_BACK_THRESHOLD) {
                    snapBackY = targetCenterY - currentCenterY
                    needsSnapBack = true
                }
            }
            
            currentImageBounds.top > viewport.top -> {
                // Snap top edge to viewport
                snapBackY = viewport.top - currentImageBounds.top
                needsSnapBack = true
            }
            
            currentImageBounds.bottom < viewport.bottom -> {
                // Snap bottom edge to viewport
                snapBackY = viewport.bottom - currentImageBounds.bottom
                needsSnapBack = true
            }
        }
        
        return if (needsSnapBack) {
            tempPointF.set(snapBackX, snapBackY)
            tempPointF
        } else {
            null
        }
    }
    
    /**
     * Check if image bounds are within viewport constraints
     */
    fun isWithinBounds(imageBounds: RectF, tolerance: Float = 1f): Boolean {
        val viewport = getViewportBounds()
        
        return when {
            imageBounds.width() <= viewport.width() -> {
                // Small image should be roughly centered
                val centerXDiff = abs(imageBounds.centerX() - viewport.centerX())
                centerXDiff <= tolerance
            }
            
            else -> {
                // Large image edges should be within viewport
                val leftValid = imageBounds.left <= viewport.left + tolerance
                val rightValid = imageBounds.right >= viewport.right - tolerance
                leftValid && rightValid
            }
        } && when {
            imageBounds.height() <= viewport.height() -> {
                // Small image should be roughly centered
                val centerYDiff = abs(imageBounds.centerY() - viewport.centerY())
                centerYDiff <= tolerance
            }
            
            else -> {
                // Large image edges should be within viewport
                val topValid = imageBounds.top <= viewport.top + tolerance
                val bottomValid = imageBounds.bottom >= viewport.bottom - tolerance
                topValid && bottomValid
            }
        }
    }
    
    /**
     * Get minimum scale to fit image in viewport
     */
    fun getMinimumScaleToFit(imageWidth: Float, imageHeight: Float): Float {
        if (imageWidth <= 0f || imageHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
            return 1f
        }
        
        val scaleX = viewportWidth / imageWidth
        val scaleY = viewportHeight / imageHeight
        
        return min(scaleX, scaleY)
    }
    
    /**
     * Get maximum scale that still allows panning
     */
    fun getMaximumUsableScale(imageWidth: Float, imageHeight: Float): Float {
        if (imageWidth <= 0f || imageHeight <= 0f || viewportWidth <= 0f || viewportHeight <= 0f) {
            return 1f
        }
        
        // Maximum scale where image is still larger than viewport in both dimensions
        val maxScaleX = (viewportWidth * 5f) / imageWidth  // 5x viewport width
        val maxScaleY = (viewportHeight * 5f) / imageHeight  // 5x viewport height
        
        return min(maxScaleX, maxScaleY)
    }
    
    /**
     * Enable/disable elastic bounds behavior
     */
    fun setElasticBoundsEnabled(enabled: Boolean) {
        isElasticBoundsEnabled = enabled
    }
    
    /**
     * Enable/disable snap-back behavior
     */
    fun setSnapBackEnabled(enabled: Boolean) {
        isSnapBackEnabled = enabled
    }
    
    /**
     * Get current viewport dimensions
     */
    fun getViewportDimensions(): PointF {
        tempPointF.set(viewportWidth, viewportHeight)
        return tempPointF
    }
}