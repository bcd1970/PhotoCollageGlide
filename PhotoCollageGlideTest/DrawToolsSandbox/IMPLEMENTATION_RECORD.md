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

## Chapter 5 — Rectangle Drawing Restore (Simplified UI)

### Overview
- Removed the shape lock UI and its logic, then temporarily removed shapes from the app.
- Restored basic rectangle drawing in the DrawToolsSandbox with a simplified UI: drawing is always active, and only an Eraser action is present in the bottom bar.

### Behavior
- Drawing: press, drag to preview a rectangle, release to commit. Multiple rectangles can be added.
- Editing: existing logic for corner handles and moving rectangles remains available; aspect lock is disabled by default and no UI is exposed to toggle it.
- Eraser: clears all rectangles.

### UI Changes
- `activity_main.xml`: removed the lock toggle group; drawing surface now constrains directly above the bottom bar.

## Chapter 6 — TextBox Tool: IME, Selection, Transform (Scale + Rotate)

### Overview
- Module: `:DrawToolsSandbox`
- View: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`
- Activity: `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/MainActivity.kt`
- Layout: `DrawToolsSandbox/src/main/res/layout/activity_main.xml`

This chapter documents the TextBox tool: editing text with the soft keyboard, caret/selection drawing, double‑tap word selection, and a free‑transform box that supports corner scaling plus rotation with pivot anchoring, visual axis guidance, and haptics.

### Text Model
```kotlin
private data class TextBox(
    var x: Float,
    var y: Float,
    var fontSize: Float,
    var text: StringBuilder,
    var caretIndex: Int = 0,
    var selStart: Int = 0,
    var selEnd: Int = 0,
    var rotationDeg: Float = 0f,
    var transforming: Boolean = false,
    var editing: Boolean = true,
    var dragMode: TBDrag = TBDrag.NONE
)
```

### IME Integration (Proxy EditText)
- Hidden `AppCompatEditText` (`@id/imeProxy`) is added to the layout (1dp, alpha 0).
- In `MainActivity`, we pass the proxy to the view: `drawingSurface.setImeDelegate(binding.imeProxy)`.
- In the view:
  - `onCreateInputConnection` delegates to the proxy’s `onCreateInputConnection`. Before delegating we seed its text and selection from the active `TextBox` so IME corrections and suggestions “just work”.
  - A `TextWatcher` on the proxy mirrors the proxy’s text and selection back into the active `TextBox` and invalidates the view.
  - `showKeyboard()/hideKeyboard()` focus and show/hide the proxy instead of the custom view.

Key snippet:
```kotlin
fun setImeDelegate(editText: EditText) {
  imeDelegate = editText
  editText.addTextChangedListener(object: TextWatcher {
    override fun afterTextChanged(s: Editable?) {
      val tb = activeTb() ?: return
      val newText = s?.toString() ?: ""
      if (tb.text.toString() != newText) {
        tb.text.clear(); tb.text.append(newText)
        tb.caretIndex = editText.selectionEnd.coerceAtLeast(0)
        tb.selStart = editText.selectionStart.coerceAtLeast(0)
        tb.selEnd = editText.selectionEnd.coerceAtLeast(0)
        invalidate()
      }
    }
  })
}

override fun onCreateInputConnection(out: EditorInfo): InputConnection? {
  if (activeTb()?.editing != true) return null
  val proxy = imeDelegate ?: return null
  val tb = activeTb() ?: return null
  if (proxy.text?.toString() != tb.text.toString()) proxy.setText(tb.text.toString())
  proxy.setSelection(min(tb.selStart, tb.selEnd), max(tb.selStart, tb.selEnd))
  return proxy.onCreateInputConnection(out)
}
```

### Caret and Selection Drawing
- For each line we compute ascent/descent/line height from `textPaint.fontMetrics`.
- Selection background is drawn per line using measured substrings to find the x‑span; paint is semi‑transparent blue (`#6633B5E5`).
- The caret is drawn as a 1‑px line at the measured x of the caret within the active line.

### Double‑Tap Word Selection
- Double‑tap detection uses `ViewConfiguration.getDoubleTapTimeout()` and `scaledDoubleTapSlop` with a time+distance gate.
- On double‑tap inside a text box, we compute the caret index from the tap position (binary searching the measured width across the line), expand to word bounds, and set `selStart/selEnd`. We also mirror this selection to the IME proxy with `setSelection(ws, we)`.

Helper:
```kotlin
private fun wordBoundsAt(tb: TextBox, index: Int): Pair<Int, Int> {
  if (tb.text.isEmpty()) return 0 to 0
  val n = tb.text.length
  var s = index.coerceIn(0, n)
  var e = s
  while (s > 0 && (tb.text[s-1].isLetterOrDigit() || tb.text[s-1] == '_')) s--
  while (e < n && (tb.text[e].isLetterOrDigit() || tb.text[e] == '_')) e++
  return s to e
}
```

### Transform UI and Hit‑Testing
- Long‑press toggles `transforming = true`.
- Transform rectangle is padded by `transformPaddingPx = 16dp` around measured text bounds to reduce mis‑taps.
- Rotate handle: a circle centered above the top edge by `rotateHandleOffsetPx = 36dp`. Visual radius = 18dp; hit radius = 32dp.
- Corner hit radius = 28dp.
- Hit tests are done in the box’s local (unrotated) space by inverse‑rotating the finger around the text’s visual center.

Constants:
```kotlin
transformPaddingPx = 16dp
rotateHandleOffsetPx = 36dp
rotateHandleDrawRadiusPx = 18dp
rotateHandleHitRadiusPx = 32dp
cornerHitRadiusPx = 28dp
```

### Rotation (Handle)
- While dragging the rotate handle, we compute the angle delta around the visual center and add it to `rotationDeg`. The axis‑stick filter (below) is applied every frame.

### Corner Scale + Free Rotation (Opposite‑Corner Pivot)
We support uniform scale and rotation by dragging any corner. The pivot is kept fixed at the diagonally opposite corner in world space.

On pointer down (scale start):
1. Compute current metrics at size `fontSize`:
   - Lines count, line height, ascent, descent, `maxLineWidth`.
   - Local rectangle corners (TL, TR, BR, BL) and local center.
2. Derive the world position of the opposite (pivot) corner by rotating `(pivotLocal − centerLocal)` by `rotationDeg` and adding center world.
3. Record scale reference:
   - `pivotWX, pivotWY` (world pivot coordinates)
   - `r0` = distance from pivot → current touch (world) [prevents initial jump]
   - `fontSize0`
   - `angle0` = angle(pivot → touch)
   - `rotStart` = `rotationDeg`
   - `pivotCornerIndex` (0..3)

On pointer move (scale/rotate):
1. Compute `d = |touch − pivot|`, `f = d / r0`, `newSize = clamp(fontSize0 * f)`.
2. Compute `angle1 = angle(pivot → touch)`, `newRot = rotStart + (angle1 − angle0)` then pass through axis‑stick filter (may freeze rotation briefly).
3. Recompute metrics at `newSize` and `newRot`:
   - `centerLocalX = maxW/2`, `centerLocalY = −ascent/2 + (lines−1)*lineH/2`.
   - `pivotLocal` for saved corner index.
   - Rotate `(pivotLocal − centerLocal)` by `newRot` to `(offX, offY)`, compute `centerWorld = pivotWorld − (offX, offY)`.
   - Derive `tb.x` and `tb.y` from `centerWorld` and new metrics so the pivot stays fixed.

Key snippet:
```kotlin
val v = Pt(pivotLocal.x - centerLocalX, pivotLocal.y - centerLocalY)
val rad = Math.toRadians(newRot.toDouble())
val cosN = cos(rad).toFloat(); val sinN = sin(rad).toFloat()
val offX = v.x * cosN - v.y * sinN
val offY = v.x * sinN + v.y * cosN
val cxN = pivotWX - offX
val cyN = pivotWY - offY
tb.x = cxN - maxW / 2f
tb.y = cyN + ascent / 2f - (lines - 1) * lineH / 2f
tb.fontSize = newSize
tb.rotationDeg = newRot
```

### Axis Stick + Haptics + Guide
- Purpose: make it easy to align to horizontal/vertical while dragging; provide a brief, natural “stay” and tactile cue.
- Thresholds (configurable):
  - `stickEnterThreshold = 4.0°`
  - `stickHoldWindow = 4.0°`
  - `stickDurationMs = 300 ms` (freeze)
  - `rearmCooldownMs = 450 ms` (haptic cooldown)
- Behavior:
  - When rotation crosses from outside → inside the 4° window for an axis, we:
    - Trigger LONG_PRESS haptic (enter‑only, cooldowned).
    - Freeze rotation (and scaling when scaling) at the current angle for the duration.
    - Immediately turn the transform outline and handle green (guide) and redraw.
  - While frozen, the rectangle does not move; after freeze, it resumes and snaps to finger position.
  - On release, if rotation is still within the window, snap to the exact axis (0/90/180/270). For scale mode we re‑anchor using the saved pivot so the opposite corner remains fixed.

Guide + Haptic Sync:
```kotlin
// On stick enter
performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
lastGuideWithin = true
postInvalidateOnAnimation()
```

### Visuals
- Normal outline: white rectangle + rotate handle.
- Near axis: green rectangle + green handle (same thresholds as stick hold window).
- Selection background: semi‑transparent blue; caret as a line.

### Logs
- Tag: `DrawSmooth`.
  - `STICK enter axis=… angle=… window=…ms`
  - `STICK exit axis=… at angle=…`
  - `GUIDE enter/exit angle=… t=…` (uptime ms)
  - `DTAP check:` double‑tap diagnostics

### Haptics
- `performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` on stick enter (enter‑only, cooldowned); silent on exit.

### Manifest / Window Insets
- To avoid the caret “sliding” when the IME shows, we set `android:windowSoftInputMode="adjustNothing"` on the activity.

### Tunables
- Transform hit areas and padding (dp constants).
- Axis stick thresholds, duration, cooldown.
- Selection/background colors.

### Future Work
- Corner handle graphics (dots/squares) that rotate with the rectangle.
- Keyboard toolbar for font size / style.
- Multi‑select and group transforms.

- `menu_bottom_nav.xml`: removed the Shapes menu item; only `action_eraser` remains.
- `MainActivity`: removed lock state and toggle listeners; default selection is Eraser.

### Rendering & Input
- `DrawingSurfaceView` defaults to `ToolMode.SHAPE` so rectangle drawing is always on.
- The view renders existing rectangles and any in‑progress preview rectangle.
- Touch handling supports placement and editing of rectangles; lock remains off without exposed controls.

### Files
- `DrawToolsSandbox/src/main/res/layout/activity_main.xml`
- `DrawToolsSandbox/src/main/res/menu/menu_bottom_nav.xml`
- `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/MainActivity.kt`
- `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`

### Build/Deploy
- Built and installed the module with `:DrawToolsSandbox:installDebug`, then started `com.photocollage.glide.drawsandbox.MainActivity` on device.

## Chapter 6 — Ellipse Drawing (Drag‑to‑Stretch)

### Overview
- Replaced rectangle rendering with ellipse rendering. Each shape is drawn as an oval that fits the drag-defined bounding box.
- Keeps the simplified UI: drawing is always active; the bottom bar only exposes Eraser.

### Behavior
- Press and drag: previews an ellipse stretched to the current pointer relative to the press point.
- Release: commits the ellipse. Multiple ellipses can be created in sequence.
- Editing: existing handle/move logic operates on the bounding box; no aspect lock UI.

### Rendering & Input
- DrawingSurfaceView now calls `canvas.drawOval(left, top, right, bottom, strokePaint)` for both committed shapes and in‑progress previews.
- Touch handling for placement is unchanged from rectangles; only the render primitive switched to oval.

### Files
- `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt`
  - Switched rectangle draw calls to `drawOval(...)` for stored and preview shapes.

### Build/Deploy
- Built and installed `:DrawToolsSandbox:installDebug` and launched `com.photocollage.glide.drawsandbox.MainActivity` to verify on device.


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
 
## Chapter 7 — Angle Tool (Two Rays + Interior Arc)

### Overview
- Adds an Angle tool that builds an angle from a vertex (point 1) and two rays 1→2 and 1→3. Lines are simple strokes (no arrowheads).
- Interaction mirrors the sketch: place vertex, drag first ray, tap near its endpoint to seed the second ray, then drag to rotate it around the vertex while a small interior arc is shown.

### Interaction Flow (State Machine)
- `DRAW_FIRST`:
  - ACTION_DOWN at vertex P1 = (cx, cy).
  - ACTION_MOVE previews first ray P1→P2.
  - ACTION_UP commits P2 and radius R = |P1P2|, transitions to `WAIT_SECOND_TAP`.
- `WAIT_SECOND_TAP`:
  - ACTION_DOWN within 48 px of P2 starts `DRAG_SECOND`; otherwise begins a fresh angle at new P1.
- `DRAG_SECOND`:
  - ACTION_MOVE rotates the second ray P1→P3 at fixed radius R; ACTION_UP commits the angle.

### Data Model
- `AngleShape(cx, cy, radius, theta1Deg, theta2Deg)` where endpoints derive from polar coordinates.
- Stored angles list plus one `previewAngle` rendered during placement.

### Math
- Polar conversion for an endpoint on the circle of radius R centered at P1:
  - θ = atan2(y − cy, x − cx) [radians], or `radToDeg(atan2(...))` for degrees.
  - P(θ) = (cx + R cos θ, cy + R sin θ).
- Minor-angle sweep used for the interior arc (normalize into [−180°, 180°]):
  - d = θ2 − θ1; while d > 180°: d −= 360°; while d < −180°: d += 360°.
- Arc drawing rectangle: `RectF(cx − rA, cy − rA, cx + rA, cy + rA)` with `rA = clamp(R × 0.25, 20, 80)`.

### Rendering
- Rays: two `canvas.drawLine(cx, cy, px, py, strokePaint)` calls — one for 1→2, one for 1→3.
- Interior arc: `canvas.drawArc(smallRect, theta1Deg, sweepDeg, false, strokePaint)` where `sweepDeg = normalizeSweep(theta1, theta2)`.
- No arrowheads (per requirement). Uses the same white stroke style as other tools.

### Key Snippets
Model and helpers:

```kotlin
private data class AngleShape(
    var cx: Float, var cy: Float,
    var radius: Float,
    var theta1Deg: Float, var theta2Deg: Float
)
private enum class AngleStage { NONE, DRAW_FIRST, WAIT_SECOND_TAP, DRAG_SECOND }

private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float =
    kotlin.math.hypot(x2 - x1, y2 - y1)

private fun radToDeg(r: Float): Float = r * 180f / Math.PI.toFloat()
private fun normalizeSweep(startDeg: Float, endDeg: Float): Float {
    var d = endDeg - startDeg
    while (d > 180f) d -= 360f
    while (d < -180f) d += 360f
    return d
}
```

Drawing the angle:

```kotlin
val drawAngle: (AngleShape) -> Unit = { a ->
    val r = a.radius
    val p2x = a.cx + r * kotlin.math.cos(Math.toRadians(a.theta1Deg.toDouble())).toFloat()
    val p2y = a.cy + r * kotlin.math.sin(Math.toRadians(a.theta1Deg.toDouble())).toFloat()
    val p3x = a.cx + r * kotlin.math.cos(Math.toRadians(a.theta2Deg.toDouble())).toFloat()
    val p3y = a.cy + r * kotlin.math.sin(Math.toRadians(a.theta2Deg.toDouble())).toFloat()
    canvas.drawLine(a.cx, a.cy, p2x, p2y, strokePaint)
    canvas.drawLine(a.cx, a.cy, p3x, p3y, strokePaint)
    val arcR = kotlin.math.min(r * 0.25f, 80f).coerceAtLeast(20f)
    val rect = android.graphics.RectF(a.cx - arcR, a.cy - arcR, a.cx + arcR, a.cy + arcR)
    canvas.drawArc(rect, a.theta1Deg, normalizeSweep(a.theta1Deg, a.theta2Deg), false, strokePaint)
}
```

Gesture handling (high level):

```kotlin
when (angleStage) {
  AngleStage.DRAW_FIRST -> {
    aRadius = dist(aCx, aCy, x, y)
    aTheta1Deg = radToDeg(kotlin.math.atan2(y - aCy, x - aCx).toFloat())
    previewAngle = AngleShape(aCx, aCy, kotlin.math.max(aRadius, 1f), aTheta1Deg, aTheta1Deg)
  }
  AngleStage.DRAG_SECOND -> {
    aTheta2Deg = radToDeg(kotlin.math.atan2(y - aCy, x - aCx).toFloat())
    previewAngle = AngleShape(aCx, aCy, aRadius, aTheta1Deg, aTheta2Deg)
  }
}
```

### Defaults & Tuning
- Second-tap hit radius: 48 px around P2.
- Arc radius: `clamp(R × 0.25, 20, 80)` ensures readability near the vertex.
- Commit creates a persistent `AngleShape`; Eraser clears all angles and shapes.

### Files
- `DrawToolsSandbox/src/main/java/com/photocollage/glide/drawsandbox/DrawingSurfaceView.kt` — Angle model, draw routine, and state machine integrated with the view.

### Future Enhancements
- Optional degree label (rounded), tick marks on the arc, and post‑placement editing for P1/P2.

## Chapter 8 — Parallel Lines + Dimension (Live, Edit, Slide)

### Overview
- Adds a dimensioning tool that creates two equal‑length, parallel line segments with a double‑headed arrow between them.
- Workflow:
  1) Place base line 1→2 (press, drag, release).
  2) Tap near the base line and drag away to preview and set the parallel line offset (live preview).
  3) After commit, edit by dragging endpoints (length), dragging the arrow (position along lines), dragging near either line (offset), or dragging on the base line (slide both lines freely).

### Data Model
- `ParallelShape(sx, sy, ex, ey, offset, hasParallel, arrowT)`
  - `sx,sy` and `ex,ey`: endpoints of the base line.
  - `offset`: signed distance along the line’s normal to the parallel.
  - `hasParallel`: whether the second line is committed.
  - `arrowT ∈ [0,1]`: parametric position of the arrow along the lines.

### States
- `PLACE_FIRST` → draw base line 1→2.
- `WAIT_SECOND_DRAG` → await tap near base line to start offset.
- `DRAG_OFFSET` → preview and set offset; shows second line + arrow live.
- `EDIT_ENDPOINT_START/END` → drag either endpoint (both lines stay equal length, parallel, co‑aligned).
- `EDIT_ARROW` → slide the double‑headed arrow along lines; clamped to endpoints.
- `SLIDE_GROUP` → drag on base line to freely translate both lines together (dx,dy), keeping offset.
- `IDLE` → noop until another gesture.

### Geometry
- Unit direction and normal:
```kotlin
private fun unitDir(sx: Float, sy: Float, ex: Float, ey: Float): Triple<Float, Float, Float> {
  val dx = ex - sx; val dy = ey - sy
  val len = hypot(dx, dy)
  return if (len < 1e-6f) Triple(0f, 0f, 0f) else Triple(dx/len, dy/len, len)
}
```
- Parallel endpoints: `S2 = S + d·n`, `E2 = E + d·n` with `n = (-uy, ux)`.
- Arrow endpoints at parameter `t`: `P = S + t·L·u`, arrow is `P ↔ P + d·n`.

### Drawing
- Lines: two `canvas.drawLine(...)` calls.
- Double‑headed arrow with mirrored tips that visually touch the parallel lines:
```kotlin
private fun drawDoubleArrow(canvas: Canvas, ax: Float, ay: Float, bx: Float, by: Float) {
  canvas.drawLine(ax, ay, bx, by, strokePaint)
  val dx = bx - ax; val dy = by - ay
  val len = hypot(dx, dy); if (len < 1e-3f) return
  val vx = dx/len; val vy = dy/len
  val head = min(24f, len * 0.35f)
  val theta = Math.toRadians(28.0)
  fun drawHead(x: Float, y: Float, dirX: Float, dirY: Float) {
    val c = cos(theta).toFloat(); val s = sin(theta).toFloat()
    val r1x = dirX*c - dirY*s; val r1y = dirX*s + dirY*c
    val r2x = dirX*c + dirY*s; val r2y = -dirX*s + dirY*c
    canvas.drawLine(x, y, x + r1x*head, y + r1y*head, strokePaint)
    canvas.drawLine(x, y, x + r2x*head, y + r2y*head, strokePaint)
  }
  // Nudge tips inward so they touch (not overlap) the parallel lines
  val inset = strokePaint.strokeWidth * 0.6f
  val aX = ax + vx*inset; val aY = ay + vy*inset
  val bX = bx - vx*inset; val bY = by - vy*inset
  drawHead(aX, aY, +vx, +vy)
  drawHead(bX, bY, -vx, -vy)
}
```

### Hit‑Testing Helpers
```kotlin
private fun projectionT(sx: Float, sy: Float, ex: Float, ey: Float, px: Float, py: Float): Float {
  val (ux, uy, len) = unitDir(sx, sy, ex, ey); if (len < 1e-6f) return 0f
  return ((px - sx) * ux + (py - sy) * uy) / len
}
private fun distPointToSegment(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
  val (ux, uy, len) = unitDir(ax, ay, bx, by); if (len < 1e-6f) return hypot(px-ax, py-ay)
  val t = ((px-ax)*ux + (py-ay)*uy).coerceIn(0f, len)
  return hypot(px - (ax + t*ux), py - (ay + t*uy))
}
```

### Gesture Logic (high‑level)
```kotlin
// Starting offset
if (!sh.hasParallel && nearBase && state==WAIT_SECOND_DRAG) { state=DRAG_OFFSET; updateOffsetFromPoint(sh,x,y) }

// After commit
if (nearArrow) state=EDIT_ARROW
else if (nearStart || nearStart2) state=EDIT_ENDPOINT_START
else if (nearEnd || nearEnd2) state=EDIT_ENDPOINT_END
else if (nearBase) { // Slide both lines freely
  state=SLIDE_GROUP; storeBasePositions()
} else if (nearParallel) { // Adjust distance
  state=DRAG_OFFSET; updateOffsetFromPoint(sh,x,y)
}

// MOVE handlers
if (state==DRAG_OFFSET) updateOffsetFromPoint(sh,x,y)
if (state==SLIDE_GROUP) translateBoth(dx,dy)
if (state==EDIT_ARROW) sh.arrowT = projectionT(...).coerceIn(0f,1f)
if (state==EDIT_ENDPOINT_*) moveEndpointAlongDir(...)
```

### Polishing
- Live Preview: second line and arrow render during `DRAG_OFFSET` before lift.
- Arrowheads: mirrored, tips inset by `strokeWidth×0.6` to meet lines exactly.
- Post‑commit editing:
  - Drag near either line → offset adjust.
  - Drag on base line → free translation (new).
  - Drag endpoints → change length; lines remain parallel and equal.
  - Drag arrow → slide along the lines, clamped to endpoints.

### Files
- `DrawToolsSandbox/src/main/java/.../DrawingSurfaceView.kt`
  - `ToolMode.PARALLEL`, `ParallelShape`, state machine, drawing, hit‑tests, and helpers.

### Build/Deploy
- Verified via `:DrawToolsSandbox:installDebug` and on‑device screenshots captured to `build/`.

## Chapter 9 — L‑Shape (Free 1–2 + Horizontal 2–3)

### Overview
- Adds an L‑shape drawing tool with a free segment 1–2 and a horizontal segment 2–3 that is always parallel to the bottom of the screen.
- Segment 2–3 shows live while placing 1–2, initially to the right with a fixed default length; after commit, it stays horizontal while its length can be adjusted by dragging point 3.

### Data Model
- `LShape(p1x, p1y, p2x, p2y, hLen)` where point 3 is derived: `p3 = (p2x + hLen, p2y)`.
  - `hLen` can be positive (to the right) or negative (to the left).

### States
- `PLACE`: press/drag to position P2 from P1; 2–3 previews horizontally.
- `DRAG_P1`: drag P1 to edit segment 1–2 (P2 fixed).
- `DRAG_P2`: drag P2 to move the joint; 2–3 stays horizontal and keeps current `hLen`.
- `DRAG_P3`: drag near P3 to change `hLen` (2–3 remains horizontal at `y = p2y`).
- `SLIDE`: drag on the 1–2 segment to translate the entire L‑shape (both points P1,P2 and the derived P3) by (dx,dy).
- `IDLE`: waiting.

### Drawing
- Rendering uses derived P3 on every frame:
```kotlin
private fun drawLShape(canvas: Canvas, p1x: Float, p1y: Float, p2x: Float, p2y: Float, p3x: Float, p3y: Float) {
  canvas.drawLine(p1x, p1y, p2x, p2y, strokePaint)
  canvas.drawLine(p2x, p2y, p3x, p3y, strokePaint)
  drawHandle(canvas, p1x, p1y)
  drawHandle(canvas, p2x, p2y)
  drawHandle(canvas, p3x, p3y)
}
```

### Defaults
- Default horizontal length during placement:
```kotlin
private fun defaultHL(): Float = 80f * resources.displayMetrics.density
```
- `ensureLShapeDefaults()` allocates a starter shape when needed.

### Interaction (high‑level)
```kotlin
// DOWN
if (shape==null || state==PLACE) { shape = LShape(x,y,x,y, defaultHL()); state=PLACE }
else if (near(P1)) state = DRAG_P1
else if (near(P2)) state = DRAG_P2
else if (near(P3)) state = DRAG_P3
else if (nearSeg12) { state = SLIDE; storeBase(P1,P2); down=(x,y) }

// MOVE
when (state) {
  PLACE -> { shape.p2x=x; shape.p2y=y }
  DRAG_P1 -> { shape.p1x=x; shape.p1y=y }
  DRAG_P2 -> { shape.p2x=x; shape.p2y=y }          // P3 derived from p2 + hLen
  DRAG_P3 -> { shape.hLen = x - shape.p2x }        // horizontal only
  SLIDE   -> { dx=x-downX; dy=y-downY; translate(P1,P2,dx,dy) }
}

// UP → state = IDLE
```

### Hit‑Testing
- Endpoints: radial threshold `r ≈ 36 px` around P1, P2, P3.
- Group slide: distance to segment(1,2) < 36 px.

### Behavior Guarantees
- 2–3 is always horizontal (y equals P2.y) in all states.
- P3 flips left/right naturally as `hLen` becomes negative when dragging past P2.
- Group slide translates the whole L‑shape; offset/lengths preserved.

### Files
- `DrawToolsSandbox/src/main/java/.../DrawingSurfaceView.kt` — `ToolMode.LSHAPE`, `LShape` model and `LStage` states; touch handling and drawing.
- `MainActivity.kt` — sets the view’s tool mode to `LSHAPE` on start for convenience.

### Future Enhancements
- Optional arrowheads at 1–2 or 2–3 ends; numeric length label on 2–3; snap P2 to multiples of 15° relative to P1; double‑tap to reset length.

## Chapter 10 — Text Box (Single/Multi‑Line, Caret, Transform, IME)

### Overview
- Adds a text box tool with a blinking caret, draggable position, long‑press transform (resize + rotate), and system keyboard integration.
- Single‑line by default but supports Enter/newlines for multi‑line rendering; multiple text boxes may coexist; Eraser clears them and hides the keyboard.

### Model
- `TextBox(x, y, fontSize, text, caretIndex, selStart, selEnd, rotationDeg, transforming, editing, composingStart, composingEnd)`
- Multiple instances kept in `textBoxes`; `activeTbIdx` marks the selected one.

### Rendering
- Draws each line split by `\n` at `y = base + lineIndex * lineHeight` with rotation around the text box center.
- Active box shows caret (blinking) and, when transforming, a rectangle and a larger rotation handle above the box; corners have enlarged hit areas.

### Gestures
- Tap inside: selects the topmost box and places care t at tapped position (binary search on measured text), shows keyboard.
- Drag inside: moves the box; long‑press toggles transform; drag corners to scale, rotation handle to rotate.
- Tap empty space: creates a new empty text box (previous boxes persist). Eraser is the only way to clear all.

### IME Integration (Gboard‑friendly)
- Input type: `TYPE_CLASS_TEXT | TYPE_TEXT_FLAG_AUTO_CORRECT | TYPE_TEXT_FLAG_CAP_SENTENCES | TYPE_TEXT_FLAG_MULTI_LINE`.
- Implemented InputConnection methods:
  - `getTextBefore/AfterCursor`, `getSelectedText`, `setSelection`, `getExtractedText`, `getCursorCapsMode`.
  - Composing lifecycle: `setComposingText`, `setComposingRegion`, `finishComposingText`.
  - Edits: `commitText`, `deleteSurroundingText`, `deleteSurroundingTextInCodePoints`.
- commit precedence: replace selection → replace composing region → insert at caret.
- After every edit: updates IME selection/composition via `InputMethodManager.updateSelection(...)`.

### Known Limitation
- Word suggestion replacement (Gboard) still inconsistent on first tap in some flows: sometimes the suggestion appends instead of replacing. We added composing/selection handling, restartInput, and updateSelection, but a corner case persists and will be addressed next session (likely via `commitCorrection`/fallback token replacement heuristic).
