# Implementation Record

## Chapter 1 — Freehand Smoothing (DrawToolsSandbox)

- Module: `:DrawToolsSandbox`
- Entry: `com.photocollage.glide.drawsandbox.MainActivity`
- View: `com.photocollage.glide.drawsandbox.DrawingSurfaceView`
- UI controls:
  - Slider `smoothingSlider` (0–100%)
  - Toggle `debugOverlaySwitch` (Overlay raw)

### Current Behavior
- Draw freehand strokes in white on a dark background.
- Smoothing strength is controlled by the slider (live, mid‑stroke).
- Optional debug overlay draws the raw stroke in red on top of the smoothed stroke.
- Raw overlay persists after lifting the finger to enable direct comparison.
- Eraser clears both smoothed strokes and raw overlays.

### Smoothing Pipeline
1. Sample touch points every MOVE (raw polyline).
2. Decimate points into “knots” by a spacing in pixels to reduce jitter.
3. Construct a smoothed path using the midpoint quadratic technique:
   - Move to midpoint(p0, p1)
   - For each interior knot i: `quadTo(pi, midpoint(pi, pi+1))`
   - Line to the last knot.

This approach yields rounded corners and visible simplification at higher spacing values, while remaining fast on device.

### Slider Mapping
- Slider value `v` in [0, 100] maps to `smoothingStrength = v / 100.0`.
- Spacing formula (effective range):
  - `spacingPx = 4f + 96f * smoothingStrength` → 4 px at 0%, 100 px at 100%.
- Effect:
  - 0%: raw polyline (no smoothing)
  - ~40–60%: noticeable rounding and jitter reduction
  - 100%: strong simplification, flowing curves

### Debugging & Telemetry
- Logcat tag: `DrawSmooth`
  - `setSmoothing amount=…, spacingPx=…`
  - `DOWN/MOVE/UP` with point counts
  - `rebuild raw=…, knots=…, smooth=…, spacing=…`
- On‑screen HUD (top‑left): `smooth=…, spacing=…, rawPts=…`.
- ADB examples:
  - Filter logs: `adb logcat -s DrawSmooth`

### Files
- Layout: `DrawToolsSandbox/src/main/res/layout/activity_main.xml`
  - Slider `@id/smoothingSlider`
  - Text `@id/smoothingValue`
  - Toggle `@id/debugOverlaySwitch`
- Activity: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/MainActivity.kt`
  - Wires slider and overlay toggle to the view
  - Applies initial slider value on start
- View: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`
  - Smoothing implementation and rendering
  - Raw overlay (current + finished) persistence
  - Logging + HUD

### Alternatives Explored (Rejected/Archived)
- EMA + quadratic segments: too subtle under dense input; limited visual payoff.
- Chaikin corner cutting: improved, but still subtle mid‑stroke without decimation.
- Catmull–Rom → cubic Bezier: correct but visually similar for dense polylines; required decimation to be noticeable.

### Rationale
- Decimation + midpoint quadratic balancing provides a clear, performant smoothing with an intuitive single parameter (spacing).
- Persisting the raw overlay enables visual verification without tooling.

### Future Work
- Presets (Low/Med/High/Max) alongside the slider.
- Separate toggles to persist or hide finished raw overlays.
- Stroke width/color controls; per‑stroke smoothing baked after lift.
- Undo/redo for strokes.
- Optional post‑processing (Savitzky–Golay / spline fits) for exported paths.

## Chapter 2 — Closing Loop + Snap Indicator

### Overview
- Adds loop‑closing to the freehand brush: when the stroke endpoint is near the start, the path snaps closed and is smoothed as a loop.
- Visual snap indicator: a circle around the start point shows the active snap radius; it turns green when inside range.
- Raw overlay persists (if enabled) so red raw loop remains visible for comparison after lift.

### Behavior
- While drawing, a circle is rendered around the stroke’s start point:
  - Cyan circle: outside snap range.
  - Green circle: inside snap range; lifting will snap/close.
- On lift (ACTION_UP), if the last point is within radius of the start, the stroke snaps to the exact start point and closes; the smoothed path uses wrap‑around midpoint smoothing and is `close()`d.
- Overlay: If “Overlay raw” is enabled, the raw red closed path persists after lift; smoothed white closed loop is also kept.

### Parameters
- Snap radius (dynamic): `closeThresholdPx = max(40 px, spacingPx × 1.0)`
  - Tied to smoothing slider via `spacingPx` (Chapter 1): 4 px at 0% → 100 px at 100%.
  - At 100% smoothing, snap radius ≈ 100 px (very forgiving).

### Smoothing (Closed)
- Uses the same decimation spacing mapped from the slider.
- For closed curves, smoothing wraps midpoints:
  - Move to midpoint(last, first)
  - For each knot i: `quadTo(knot[i], midpoint(knot[i], knot[i+1]))` (with wrap)
  - `close()` the path for a continuous loop.

### Debugging & Telemetry
- Logcat tag: `DrawSmooth`
  - On lift: `UP rawSize=… closed=true|false`
  - Rebuild includes `closed=…` flag.
- On‑screen HUD remains: `smooth=…, spacing=…, rawPts=…`.
- Overlay toggle remains: “Overlay raw” switch.

### Files
- View: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`
  - Added loop closing, dynamic snap radius, snap indicator (circle only), and closed‑curve smoothing.
  - Persists finished raw paths when overlay is enabled.

### Notes
- The snap line preview was removed per request; the circle indicator remains.
- The snap radius scales with smoothing to match expected simplification behavior.

## Chapter 3 — Straight Lines (Lock + Handles + Startup)

### Overview
- Adds a straight line tool with:
  - H/V constrain lock (toggled by reselecting the Line item in the bottom bar).
  - Draggable endpoint handles for post‑placement adjustments.
  - Line tool is active and functional at app startup.

### Behavior
- Drawing:
  - Select Line in the bottom bar (preselected on launch).
  - Press and drag to preview a line from DOWN to current pointer; release to commit.
- Lock toggle + icon:
  - Tap Line again (reselect) to toggle constrain lock ON/OFF.
  - Icon updates: unlocked uses `ic_menu_sort_by_size`, locked uses `ic_lock_lock`.
- Constrain logic (when lock is ON):
  - During preview or when dragging a handle, the line snaps to horizontal or vertical based on the first movement and retains that axis for the current edit.
- Editing:
  - Tap near an endpoint (cyan handle) to pick it up; drag to reposition.
  - With lock ON, movement is constrained to the detected axis relative to the opposite endpoint.
- Eraser clears lines along with brush strokes/overlays.

### Startup Initialization
- On `MainActivity` creation:
  - Programmatically selects the Line item.
  - Sets tool mode to Line and applies the current lock state.
  - Updates the Line menu icon to match lock state.

### UI
- Bottom bar: Line item acts as both selector and lock toggle (via reselect). Separate lock button near the slider was removed to avoid mis‑taps.
- Handles: Cyan circular handles visualize draggable endpoints.

### Files
- View: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`
  - `ToolMode.LINE`, preview + commit, lines store, handle hit‑testing/dragging, constrain logic.
  - `clearAll()` now clears lines and resets selection.
- Activity: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/MainActivity.kt`
  - Line/Brush mode switching; Line reselection toggles lock + icon update.
  - Startup selects Line and applies lock; helper `updateLineMenuIcon()`.
- Menu: `DrawToolsSandbox/src/main/res/menu/menu_bottom_nav.xml`
  - Adds the Line item and icon.
- Layout: `DrawToolsSandbox/src/main/res/layout/activity_main.xml`
  - Removed old lock button; slider spans to smoothing value again.

### Future Work
- Line selection highlight and delete action for a selected line.
- Numeric angle display and free‑angle snapping (15° increments).
- Separate style controls for lines (width, color, dashed).
- Multi‑segment polylines and curved paths (next step).

## Chapter 4 — Curves (Quad + Cubic)

### Overview
- Adds a Curve tool with two recorded modes: Quad and Cubic.
- Location: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`
  - `enum class CurveMode { QUAD, CUBIC, … }`
  - Internal model: `Curve(mode, start, end, c1, c2, …)`

### Default Control Placement
- Quad (single control point):
  - Control point is the midpoint of the chord, offset 40 px along the perpendicular to the segment.
  - Code path: `defaultCurve(QUAD)` and `updateDefaultControls(QUAD)`.
  - Formula: `cp = midpoint(s, e) + perpOffset(s, e, 40f)`.
- Cubic (two control points):
  - Control points lie on the straight segment at 1/3 and 2/3 from the start.
  - Code path: `defaultCurve(CUBIC)` and `updateDefaultControls(CUBIC)`.
  - Formula: `c1 = s + (e - s)/3`, `c2 = s + 2*(e - s)/3`.

### Live Update During Placement
- On pointer move while placing a new curve, the end point `e` is updated and the corresponding defaults above are recomputed for the active mode.
- Axis constrain (if enabled) snaps the preview to horizontal/vertical before computing defaults.

### Rendering
- Quad: `Path.quadTo(cp.x, cp.y, end.x, end.y)` using `c1` if set, otherwise the midpoint fallback.
- Cubic: `Path.cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)`; if a control is null, a safe midpoint fallback is used.
- Entry point: `drawCurve(canvas, curve)` selects quad vs cubic based on `curve.mode`.

### Handle Interaction
- Visible handles in Curve mode:
  - Anchors: start/end (cyan).
  - Controls: `c1`/`c2` (magenta). Quad shows only `c1`; Cubic shows both.
- Drag behavior:
  - Dragging `c1`/`c2` directly sets those control points, overriding defaults.
  - Dragging start/end moves the anchors; the existing control points are preserved (shape retention) unless the curve is in a mode that auto-recomputes (not applicable to Quad/Cubic in records).
  - New placement uses defaults each frame until committed on lift.

### Notes
- Only Quad and Cubic are relevant here; other curve modes (Arc/Tangent/Bend) are excluded from this record by request.
- Defaults provide predictable, smooth shapes: Quad arcs with a fixed perpendicular offset; Cubic starts as a straight-line cubic amenable to later control edits.

### Key Snippets
Default placement (on preview start):

```kotlin
private fun defaultCurve(mode: CurveMode, s: PointF, e: PointF): Curve = when (mode) {
    CurveMode.QUAD -> {
        val m = midpoint(s, e)
        val (px, py) = perpOffset(s, e, 40f)
        Curve(mode, s, e, c1 = PointF(m.x + px, m.y + py))
    }
    CurveMode.CUBIC -> {
        val v = PointF(e.x - s.x, e.y - s.y)
        val c1 = PointF(s.x + v.x / 3f, s.y + v.y / 3f)
        val c2 = PointF(s.x + 2f * v.x / 3f, s.y + 2f * v.y / 3f)
        Curve(mode, s, e, c1 = c1, c2 = c2)
    }
    else -> TODO()
}
```

Live recompute while dragging anchors (keeps defaults unless user edited controls):

```kotlin
private fun updateDefaultControls(c: Curve) {
    when (c.mode) {
        CurveMode.QUAD -> {
            val m = midpoint(c.start, c.end)
            val (px, py) = perpOffset(c.start, c.end, 40f)
            c.c1 = PointF(m.x + px, m.y + py)
        }
        CurveMode.CUBIC -> {
            val v = PointF(c.end.x - c.start.x, c.end.y - c.start.y)
            c.c1 = PointF(c.start.x + v.x / 3f, c.start.y + v.y / 3f)
            c.c2 = PointF(c.start.x + 2f * v.x / 3f, c.start.y + 2f * v.y / 3f)
        }
        else -> Unit
    }
}
```

Drawing the curve:

```kotlin
private fun drawCurve(canvas: Canvas, c: Curve) {
    val path = Path().apply { moveTo(c.start.x, c.start.y) }
    when (c.mode) {
        CurveMode.QUAD -> {
            val cp = c.c1 ?: midpoint(c.start, c.end)
            path.quadTo(cp.x, cp.y, c.end.x, c.end.y)
        }
        CurveMode.CUBIC -> {
            val p1 = c.c1 ?: midpoint(c.start, c.end)
            val p2 = c.c2 ?: midpoint(c.start, c.end)
            path.cubicTo(p1.x, p1.y, p2.x, p2.y, c.end.x, c.end.y)
        }
        else -> Unit
    }
    canvas.drawPath(path, strokePaint)
}
```
