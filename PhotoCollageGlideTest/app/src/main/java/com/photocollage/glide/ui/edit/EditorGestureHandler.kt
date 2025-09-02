package com.photocollage.glide.ui.edit

import android.content.Context
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import kotlin.math.abs

/**
 * Unified gesture handler for photo editor combining ScaleGestureDetector and GestureDetector
 * following 2025 Android best practices for simultaneous pan and zoom operations.
 * 
 * Performance targets:
 * - Touch response time: <16ms for 60fps
 * - Gesture detection latency: <1ms
 * - Memory allocations: Zero during gesture operations
 */
class EditorGestureHandler(
    context: Context,
    private val gestureListener: EditorGestureListener
) {
    
    interface EditorGestureListener {
        fun onScaleBegin(focusX: Float, focusY: Float): Boolean
        fun onScale(scaleFactor: Float, focusX: Float, focusY: Float): Boolean
        fun onScaleEnd()
        
        fun onPanBegin(startX: Float, startY: Float): Boolean
        fun onPan(deltaX: Float, deltaY: Float): Boolean
        fun onPanEnd()
        
        fun onFling(velocityX: Float, velocityY: Float): Boolean
        fun onDoubleTap(x: Float, y: Float): Boolean
    }
    
    enum class GestureState {
        IDLE,
        PANNING,
        SCALING,
        FLINGING
    }
    
    private var currentState = GestureState.IDLE
    
    // Reusable objects to avoid allocations during gestures
    private val tempPointF = PointF()
    
    // Scale gesture detector for pinch-to-zoom
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            currentState = GestureState.SCALING
            return gestureListener.onScaleBegin(focusX, focusY)
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val focusX = detector.focusX
            val focusY = detector.focusY
            
            // Constrain scale factor to prevent extreme values
            val constrainedScaleFactor = scaleFactor.coerceIn(0.5f, 2.0f)
            
            return gestureListener.onScale(constrainedScaleFactor, focusX, focusY)
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            currentState = GestureState.IDLE
            gestureListener.onScaleEnd()
        }
    })
    
    // Gesture detector for pan, fling, and double-tap
    private val panDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        
        override fun onScroll(
            e1: MotionEvent?,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            // Only handle pan if not currently scaling
            if (currentState == GestureState.SCALING) {
                return false
            }
            
            // Start panning state on first scroll event
            if (currentState == GestureState.IDLE) {
                currentState = GestureState.PANNING
                gestureListener.onPanBegin(e2.x, e2.y)
            }
            
            // Convert distance to delta (invert for natural scrolling)
            val deltaX = -distanceX
            val deltaY = -distanceY
            
            return gestureListener.onPan(deltaX, deltaY)
        }
        
        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            // Only fling after panning, not during scaling
            if (currentState != GestureState.PANNING) {
                return false
            }
            
            currentState = GestureState.FLINGING
            
            // Pass full velocity for more natural fling behavior
            return gestureListener.onFling(velocityX, velocityY)
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            // Only handle double-tap when idle
            if (currentState != GestureState.IDLE) {
                return false
            }
            
            return gestureListener.onDoubleTap(e.x, e.y)
        }
    })
    
    /**
     * Handle touch events - pass to both detectors following 2025 best practices
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = false
        
        // Always pass to scale detector first
        if (scaleDetector.onTouchEvent(event)) {
            handled = true
        }
        
        // Pass to pan detector if not scaling or if scale detector didn't consume
        if (currentState != GestureState.SCALING || !handled) {
            if (panDetector.onTouchEvent(event)) {
                handled = true
            }
        }
        
        // Handle touch events for state management
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // If user touches during fling, interrupt fling and allow new gesture
                if (currentState == GestureState.FLINGING) {
                    currentState = GestureState.IDLE
                }
            }
            
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                if (currentState == GestureState.PANNING) {
                    currentState = GestureState.IDLE
                    gestureListener.onPanEnd()
                } else if (currentState == GestureState.FLINGING) {
                    // Fling state will be reset by animation completion
                }
            }
            
            MotionEvent.ACTION_POINTER_UP -> {
                // Multi-touch gesture ended, ensure we're in correct state
                if (event.pointerCount <= 2 && currentState == GestureState.SCALING) {
                    // Transition from scaling to potential panning
                    currentState = GestureState.IDLE
                }
            }
        }
        
        return handled
    }
    
    /**
     * Get current gesture state
     */
    fun getCurrentState(): GestureState = currentState
    
    /**
     * Reset gesture state (useful when view loses focus)
     */
    fun reset() {
        currentState = GestureState.IDLE
    }
    
    /**
     * Check if currently handling a gesture
     */
    fun isGestureInProgress(): Boolean = currentState != GestureState.IDLE
    
    /**
     * Get scale detector span for advanced gesture analysis
     */
    fun getCurrentSpan(): Float = scaleDetector.currentSpan
    
    /**
     * Get scale detector focus point for advanced transformations
     */
    fun getCurrentFocus(): PointF {
        tempPointF.set(scaleDetector.focusX, scaleDetector.focusY)
        return tempPointF
    }
}