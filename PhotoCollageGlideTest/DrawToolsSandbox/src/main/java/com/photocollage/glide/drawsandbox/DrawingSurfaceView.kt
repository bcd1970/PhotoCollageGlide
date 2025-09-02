package com.photocollage.glide.drawsandbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DrawingSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    companion object { private const val TAG = "DrawSmooth" }

    enum class ToolMode { NONE, SHAPE }

    private val bgPaint = Paint().apply {
        color = Color.parseColor("#121212")
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        style = Paint.Style.FILL
    }
    private val activeHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }
    private val debugVecPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val debugTracePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val debugTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 28f
    }

    // Shapes (rectangles)
    private data class RectShape(
        var left: Float,
        var top: Float,
        var right: Float,
        var bottom: Float
    )
    private val shapes: MutableList<RectShape> = mutableListOf()
    private var previewRect: RectShape? = null
    private var editingShapeIndex: Int = -1
    private enum class RectHandle { NONE, MOVE, TL, TR, BL, BR }
    private var draggingRectHandle: RectHandle = RectHandle.NONE
    private var handleRadiusPx: Float = 18f

    // Drag/lock state
    private var dragStartX: Float = 0f
    private var dragStartY: Float = 0f
    private var shapeLockEnabled: Boolean = false
    private var lockRatio: Float? = null // null when unlocked; 1f for square; >0f for aspect
    private var rectAnchorX: Float = 0f
    private var rectAnchorY: Float = 0f
    private var rectSx: Float = 0f
    private var rectSy: Float = 0f
    // Debug/analysis state
    private var dbgTouchX: Float = 0f
    private var dbgTouchY: Float = 0f
    private var dbgCorner: String = ""
    private var dbgW: Float = 0f
    private var dbgH: Float = 0f
    private var dbgWidthDriven: Boolean = false
    private var dbgAnchorX: Float = 0f
    private var dbgAnchorY: Float = 0f
    private var dbgVecPersist: Boolean = false
    private var dbgVecStartX: Float = 0f
    private var dbgVecStartY: Float = 0f
    private var dbgVecEndX: Float = 0f
    private var dbgVecEndY: Float = 0f
    private val dbgTracePts: MutableList<PointF> = mutableListOf()

    private var toolMode: ToolMode = ToolMode.SHAPE

    fun setToolMode(mode: ToolMode) {
        toolMode = mode
        invalidate()
    }

    fun setShapeLockEnabled(enabled: Boolean) {
        shapeLockEnabled = enabled
        if (shapeLockEnabled) {
            // If a shape is in progress, lock to its current aspect; otherwise lock to square
            lockRatio = currentAspectOrNull() ?: 1f
            Log.d(TAG, "SHAPE lock enabled ratio=${"%.3f".format(lockRatio ?: -1f)}")
        } else {
            lockRatio = null
            Log.d(TAG, "SHAPE lock disabled")
        }
        invalidate()
    }

    fun clearAll() {
        shapes.clear()
        previewRect = null
        editingShapeIndex = -1
        draggingRectHandle = RectHandle.NONE
        // Clear debug traces
        dbgTracePts.clear()
        dbgVecPersist = false
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Draw shapes
        val drawRect: (RectShape) -> Unit = { r ->
            val l = min(r.left, r.right)
            val t = min(r.top, r.bottom)
            val rr = max(r.left, r.right)
            val b = max(r.top, r.bottom)
            canvas.drawRect(l, t, rr, b, strokePaint)
        }
        shapes.forEach(drawRect)
        previewRect?.let(drawRect)

        // Handles for selected or preview rectangle
        if (toolMode == ToolMode.SHAPE) {
            val idx = if (editingShapeIndex in shapes.indices) editingShapeIndex else -1
            val drawHandles: (RectShape) -> Unit = { r ->
                val l = min(r.left, r.right)
                val t = min(r.top, r.bottom)
                val rr = max(r.left, r.right)
                val b = max(r.top, r.bottom)
                // base handles
                canvas.drawCircle(l, t, handleRadiusPx, handlePaint)
                canvas.drawCircle(rr, t, handleRadiusPx, handlePaint)
                canvas.drawCircle(l, b, handleRadiusPx, handlePaint)
                canvas.drawCircle(rr, b, handleRadiusPx, handlePaint)
                // highlight active corner during drag
                val paintRing = activeHandlePaint
                when (draggingRectHandle) {
                    RectHandle.TL -> canvas.drawCircle(l, t, handleRadiusPx + 4f, paintRing)
                    RectHandle.TR -> canvas.drawCircle(rr, t, handleRadiusPx + 4f, paintRing)
                    RectHandle.BL -> canvas.drawCircle(l, b, handleRadiusPx + 4f, paintRing)
                    RectHandle.BR -> canvas.drawCircle(rr, b, handleRadiusPx + 4f, paintRing)
                    else -> {}
                }
            }
            if (idx >= 0) drawHandles(shapes[idx])
            previewRect?.let(drawHandles)
        }

        // Draw debug vector/markers and trajectory (persist last after UP)
        if (draggingRectHandle != RectHandle.NONE || previewRect != null || dbgVecPersist) {
            // Touch marker
            canvas.drawCircle(dbgTouchX, dbgTouchY, 10f, debugVecPaint)
            // Anchor marker and vector
            val ax = if (draggingRectHandle != RectHandle.NONE) rectAnchorX else if (dbgVecPersist) dbgVecStartX else dragStartX
            val ay = if (draggingRectHandle != RectHandle.NONE) rectAnchorY else if (dbgVecPersist) dbgVecStartY else dragStartY
            dbgAnchorX = ax; dbgAnchorY = ay
            canvas.drawCircle(ax, ay, 10f, activeHandlePaint)
            val ex = if (dbgVecPersist) dbgVecEndX else dbgTouchX
            val ey = if (dbgVecPersist) dbgVecEndY else dbgTouchY
            canvas.drawLine(ax, ay, ex, ey, debugVecPaint)
            // Trajectory polyline
            if (dbgTracePts.size >= 2) {
                var px = dbgTracePts[0].x
                var py = dbgTracePts[0].y
                for (i in 1 until dbgTracePts.size) {
                    val q = dbgTracePts[i]
                    canvas.drawLine(px, py, q.x, q.y, debugTracePaint)
                    px = q.x; py = q.y
                }
            }
            // Text info
            val ratio = lockRatio ?: -1f
            val info = "corner=$dbgCorner  touch=(%.0f,%.0f) anchor=(%.0f,%.0f) w=%.1f h=%.1f wd=%s ratio=%.2f".format(
                dbgTouchX, dbgTouchY, dbgAnchorX, dbgAnchorY, dbgW, dbgH, dbgWidthDriven.toString(), ratio
            )
            val margin = 16f
            val textY = height.toFloat() - margin
            canvas.drawText(info, margin, textY, debugTextPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (toolMode != ToolMode.SHAPE) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                // start new debug trace
                dbgTracePts.clear()
                dbgTouchX = event.x; dbgTouchY = event.y
                dbgTracePts.add(PointF(event.x, event.y))
                dbgVecPersist = false
                val hit = hitTestRectHandle(event.x, event.y)
                if (hit.first >= 0) {
                    editingShapeIndex = hit.first
                    draggingRectHandle = hit.second
                    dragStartX = event.x; dragStartY = event.y
                    // Prepare anchor and quadrant for locked drags
                    if (editingShapeIndex in shapes.indices && shapeLockEnabled) {
                        val r = shapes[editingShapeIndex]
                        val l = min(r.left, r.right)
                        val t = min(r.top, r.bottom)
                        val rr = max(r.left, r.right)
                        val b = max(r.top, r.bottom)
                        when (draggingRectHandle) {
                            RectHandle.TL -> { rectAnchorX = rr; rectAnchorY = b }
                            RectHandle.TR -> { rectAnchorX = l; rectAnchorY = b }
                            RectHandle.BL -> { rectAnchorX = rr; rectAnchorY = t }
                            RectHandle.BR -> { rectAnchorX = l; rectAnchorY = t }
                            else -> {}
                        }
                            val dx0 = event.x - rectAnchorX
                            val dy0 = event.y - rectAnchorY
                            rectSx = if (dx0 >= 0f) 1f else -1f
                            rectSy = if (dy0 >= 0f) 1f else -1f
                        }
                } else {
                    // Start new rectangle preview
                    previewRect = RectShape(event.x, event.y, event.x, event.y)
                    editingShapeIndex = -1
                    draggingRectHandle = RectHandle.NONE
                    dragStartX = event.x; dragStartY = event.y
                    rectSx = 0f; rectSy = 0f
                    // If lock is ON before start, ensure ratio preset (square by default)
                    if (shapeLockEnabled && lockRatio == null) lockRatio = 1f
                }
                invalidate(); return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (editingShapeIndex in shapes.indices && draggingRectHandle != RectHandle.NONE) {
                    val r = shapes[editingShapeIndex]
                    dbgTouchX = event.x; dbgTouchY = event.y
                    dbgTracePts.add(PointF(event.x, event.y))
                    when (draggingRectHandle) {
                        RectHandle.MOVE -> {
                            // Move is only allowed when no lock is active
                            if (!shapeLockEnabled) {
                                val dx = event.x - dragStartX
                                val dy = event.y - dragStartY
                                r.left += dx; r.right += dx; r.top += dy; r.bottom += dy
                                dragStartX = event.x; dragStartY = event.y
                            }
                        }
                        RectHandle.TL -> {
                            val ax = r.right; val ay = r.bottom
                            if (shapeLockEnabled) {
                                val ratio = (lockRatio ?: 1f).coerceAtLeast(1e-6f)
                                if (kotlin.math.abs(ratio - 1f) < 1e-3f) {
                                    val dxA = max(0f, ax - event.x)
                                    val dyA = max(0f, ay - event.y)
                                    val s = max(dxA, dyA)
                                    dbgCorner = "TL"; dbgW = s; dbgH = s; dbgWidthDriven = dxA >= dyA
                                    r.left = ax - s; r.top = ay - s
                                } else {
                                    val dxA = ax - event.x
                                    val dyA = ay - event.y
                                    val (w, h) = aspectProject(dxA, dyA, ratio)
                                    dbgCorner = "TL"; dbgW = w; dbgH = h; dbgWidthDriven = (abs(dxA) >= abs(dyA) * ratio)
                                    r.left = ax - w; r.top = ay - h
                                }
                            } else { r.left = event.x; r.top = event.y }
                        }
                        RectHandle.TR -> {
                            val ax = r.left; val ay = r.bottom
                            if (shapeLockEnabled) {
                                val ratio = (lockRatio ?: 1f).coerceAtLeast(1e-6f)
                                if (kotlin.math.abs(ratio - 1f) < 1e-3f) {
                                    val dxA = max(0f, event.x - ax)
                                    val dyA = max(0f, ay - event.y)
                                    val s = max(dxA, dyA)
                                    dbgCorner = "TR"; dbgW = s; dbgH = s; dbgWidthDriven = dxA >= dyA
                                    r.right = ax + s; r.top = ay - s
                                } else {
                                    val dxA = event.x - ax
                                    val dyA = ay - event.y
                                    val (w, h) = aspectProject(dxA, dyA, ratio)
                                    dbgCorner = "TR"; dbgW = w; dbgH = h; dbgWidthDriven = (abs(dxA) >= abs(dyA) * ratio)
                                    r.right = ax + w; r.top = ay - h
                                }
                            } else { r.right = event.x; r.top = event.y }
                        }
                        RectHandle.BL -> {
                            val ax = r.right; val ay = r.top
                            if (shapeLockEnabled) {
                                val ratio = (lockRatio ?: 1f).coerceAtLeast(1e-6f)
                                if (kotlin.math.abs(ratio - 1f) < 1e-3f) {
                                    val dxA = max(0f, ax - event.x)
                                    val dyA = max(0f, event.y - ay)
                                    val s = max(dxA, dyA)
                                    dbgCorner = "BL"; dbgW = s; dbgH = s; dbgWidthDriven = dxA >= dyA
                                    r.left = ax - s; r.bottom = ay + s
                                } else {
                                    val dxA = ax - event.x
                                    val dyA = event.y - ay
                                    val (w, h) = aspectProject(dxA, dyA, ratio)
                                    dbgCorner = "BL"; dbgW = w; dbgH = h; dbgWidthDriven = (abs(dxA) >= abs(dyA) * ratio)
                                    r.left = ax - w; r.bottom = ay + h
                                }
                            } else { r.left = event.x; r.bottom = event.y }
                        }
                        RectHandle.BR -> {
                            val ax = r.left; val ay = r.top
                            if (shapeLockEnabled) {
                                val ratio = (lockRatio ?: 1f).coerceAtLeast(1e-6f)
                                if (kotlin.math.abs(ratio - 1f) < 1e-3f) {
                                    val dxA = max(0f, event.x - ax)
                                    val dyA = max(0f, event.y - ay)
                                    val s = max(dxA, dyA)
                                    dbgCorner = "BR"; dbgW = s; dbgH = s; dbgWidthDriven = dxA >= dyA
                                    r.right = ax + s; r.bottom = ay + s
                                } else {
                                    val dxA = event.x - ax
                                    val dyA = event.y - ay
                                    val (w, h) = aspectProject(dxA, dyA, ratio)
                                    dbgCorner = "BR"; dbgW = w; dbgH = h; dbgWidthDriven = (abs(dxA) >= abs(dyA) * ratio)
                                    r.right = ax + w; r.bottom = ay + h
                                }
                            } else { r.right = event.x; r.bottom = event.y }
                        }
                        else -> {}
                    }
                    invalidate(); return true
                }
                previewRect?.let { r ->
                    dbgTouchX = event.x; dbgTouchY = event.y
                    dbgTracePts.add(PointF(event.x, event.y))
                    if (shapeLockEnabled) {
                        val ratio = (lockRatio ?: 1f).coerceAtLeast(1e-6f)
                        val dxRaw = event.x - dragStartX
                        val dyRaw = event.y - dragStartY
                        if (rectSx == 0f) rectSx = if (dxRaw >= 0f) 1f else -1f
                        if (rectSy == 0f) rectSy = if (dyRaw >= 0f) 1f else -1f
                        val sx = if (rectSx >= 0f) 1f else -1f
                        val sy = if (rectSy >= 0f) 1f else -1f
                        dbgCorner = when {
                            sx < 0f && sy < 0f -> "TL"
                            sx > 0f && sy < 0f -> "TR"
                            sx < 0f && sy > 0f -> "BL"
                            else -> "BR"
                        }
                        if (kotlin.math.abs(ratio - 1f) < 1e-3f) {
                            // Square: constrain corner to diagonal from start
                            val s = max(abs(dxRaw), abs(dyRaw))
                            dbgW = s; dbgH = s; dbgWidthDriven = abs(dxRaw) >= abs(dyRaw)
                            if (sx >= 0) { r.left = dragStartX; r.right = dragStartX + s } else { r.right = dragStartX; r.left = dragStartX - s }
                            if (sy >= 0) { r.top = dragStartY; r.bottom = dragStartY + s } else { r.bottom = dragStartY; r.top = dragStartY - s }
                        } else {
                            // Aspect: fit-in with pinned dragged corner at touch
                            val dx = abs(dxRaw)
                            val dy = abs(dyRaw)
                            val wFit = min(dx, ratio * dy)
                            val hFit = wFit / ratio
                            dbgWidthDriven = dx <= ratio * dy
                            dbgW = wFit; dbgH = hFit
                            when (dbgCorner) {
                                "BR" -> { r.right = event.x; r.bottom = event.y; r.left = r.right - wFit; r.top = r.bottom - hFit }
                                "TR" -> { r.right = event.x; r.top = event.y; r.left = r.right - wFit; r.bottom = r.top + hFit }
                                "TL" -> { r.left = event.x; r.top = event.y; r.right = r.left + wFit; r.bottom = r.top + hFit }
                                "BL" -> { r.left = event.x; r.bottom = event.y; r.right = r.left + wFit; r.top = r.bottom - hFit }
                            }
                        }
                    } else {
                        // Unlocked: free rectangle with pinned corner at touch
                        r.right = event.x; r.bottom = event.y
                    }
                    invalidate(); return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // persist last vector for analysis
                val ax = if (draggingRectHandle != RectHandle.NONE) rectAnchorX else dragStartX
                val ay = if (draggingRectHandle != RectHandle.NONE) rectAnchorY else dragStartY
                dbgVecStartX = ax; dbgVecStartY = ay
                dbgVecEndX = dbgTouchX; dbgVecEndY = dbgTouchY
                dbgVecPersist = true
                if (editingShapeIndex in shapes.indices && draggingRectHandle != RectHandle.NONE) {
                    draggingRectHandle = RectHandle.NONE
                    invalidate(); parent?.requestDisallowInterceptTouchEvent(false); return true
                }
                previewRect?.let { r ->
                    shapes.add(r)
                    editingShapeIndex = shapes.lastIndex
                    previewRect = null
                    // Maintain lockRatio if enabled (stays set); clear quadrant for next op
                    rectSx = 0f; rectSy = 0f
                    Log.d(TAG, "SHAPE commit: ratio=${lockRatio ?: -1f} lock=$shapeLockEnabled")
                }
                invalidate(); parent?.requestDisallowInterceptTouchEvent(false); return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun hitTestRectHandle(x: Float, y: Float): Pair<Int, RectHandle> {
        val r2 = handleRadiusPx * handleRadiusPx
        for (i in shapes.indices.reversed()) {
            val s = shapes[i]
            val l = min(s.left, s.right)
            val t = min(s.top, s.bottom)
            val r = max(s.left, s.right)
            val b = max(s.top, s.bottom)
            val corners = listOf(
                Triple(PointF(l, t), RectHandle.TL, r2),
                Triple(PointF(r, t), RectHandle.TR, r2),
                Triple(PointF(l, b), RectHandle.BL, r2),
                Triple(PointF(r, b), RectHandle.BR, r2)
            )
            for ((pt, kind, r2h) in corners) {
                val dx = x - pt.x
                val dy = y - pt.y
                if (dx * dx + dy * dy <= r2h) return Pair(i, kind)
            }
            // If inside rect (no corner hit), allow move only when no lock is active
            if (!shapeLockEnabled) {
                if (x >= l && x <= r && y >= t && y <= b) return Pair(i, RectHandle.MOVE)
            }
        }
        return Pair(-1, RectHandle.NONE)
    }

    private fun currentAspectOrNull(): Float? {
        fun aspect(w: Float, h: Float): Float? {
            val ww = abs(w); val hh = abs(h)
            return if (ww > 0.5f && hh > 0.5f) ww / hh else null
        }
        previewRect?.let { r ->
            return aspect(r.right - r.left, r.bottom - r.top)
        }
        if (editingShapeIndex in shapes.indices) {
            val s = shapes[editingShapeIndex]
            return aspect(s.right - s.left, s.bottom - s.top)
        }
        return null
    }

    private fun aspectProject(dx: Float, dy: Float, ratio: Float): Pair<Float, Float> {
        val r = ratio.coerceAtLeast(1e-6f)
        val dxC = max(0f, dx)
        val dyC = max(0f, dy)
        // Project (dxC, dyC) onto ray along vector (1, 1/r)
        val ux = 1f
        val uy = 1f / r
        val denom = ux * ux + uy * uy // 1 + 1/r^2
        val s = ((dxC * ux) + (dyC * uy)) / denom
        val w = max(0f, s) // width component along x
        val h = w / r      // height from aspect
        return Pair(w, h)
    }
}
