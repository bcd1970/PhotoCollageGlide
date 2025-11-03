
# PhotoCollageGlide — One‑Shot **Pro Annotations + Everyday UX** Implementation Guide (for OpenAI Codex)

> **Intent**: Add **sophisticated yet implementable** features that make day‑to‑day work faster—without bloat. All changes are incremental to your Pro Annotations baseline (layers, snapping, calibration, callouts, redaction, templates, OCR interface, constraints, report/export, vault, camera pipeline).  
> **How to use**: Paste the *One‑Shot Builder Prompt* below into your code‑gen agent. It will modify/add files in one PR.

**Why these features?** They mirror widely adopted patterns in focused annotation apps:  
- **Area & perimeter measuring** for plans/drawings (akin to Acrobat’s measure tools). citeturn0search2  
- **Magnifier/loupe** to avoid finger occlusion during precise placement (Android Magnifier API). citeturn0search18  
- **Pixelate/blur redaction** and simple **callouts/stamps** seen in Skitch/Evernote. citeturn0search6turn0search13  
- **Auto‑crop & perspective fix on import**, **pen/text annotations**, and **quick sharing** workflows popularized by Microsoft Lens. citeturn0search3  
- **Color eyedropper + smart palettes** based on the Android **Palette API** for better contrast/legibility. citeturn0search4  
- **Share sheet shortcuts & custom actions** for one‑tap exports (Android Sharesheet & ChooserAction). citeturn1search2turn1search14  
- **Haptics on snaps/commits** to confirm precision without visual clutter. citeturn1search1turn1search4

---

## 0) New, curated features (daily‑use value, low bloat)

1) **Polygon Area/Perimeter Tool** — Close a polyline to display **area and perimeter** using the current calibration (mm², cm², in²). Optionally display centroid. citeturn0search2  

2) **Precision Loupe (Magnifier)** — While dragging points/handles, show a small loupe near the finger with crosshair and a 3× zoom using `android.widget.Magnifier` (API 28+), with a simple overlay fallback on older devices. citeturn0search18  

3) **Eyedropper & Smart Palettes** — Tap to sample color from the image; generate a **contrast‑aware** swatch set via Jetpack **Palette** (Vibrant/Muted/Light/Dark) and auto‑pick a text color that meets WCAG contrast goals. citeturn0search4  

4) **Pixelate Brush (Mosaic) + Blur Brush** — Non‑destructive masks rendered as pixelate or blur (flatten on “Finalize”). Mirrors common “Pixelate” tools in mainstream annotators. citeturn0search6turn0search13  

5) **Auto‑Crop & Perspective Suggest** — On import, suggest a crop/quad when edges are salient; user accepts/adjusts, then proceed to rectification. (Workflow pattern popular in Lens.) citeturn0search3  

6) **Favorites & Recents (Tools/Styles/Presets)** — Star up to 6 actions (e.g., Callout→“Defect”, Dimension→“mm 1‑dec”), surfaced in a compact **Quick Actions** palette (bottom or side, hand‑aware). Keeps the app fast without new complexity.

7) **One‑Tap Export Profiles + Share Sheet actions** — “Export → PDF (Flattened, Watermark)”, “Export → Image (Redacted)”, “Export → .annotpkg”, each exposed as **ChooserAction** buttons in Android 14+ share sheet; fall back to standard Sharesheet on older OS versions. citeturn1search14turn1search2  

8) **Direct OCR → Clipboard** — Long‑press a region → “Copy Text” runs the pluggable OCR provider and copies to clipboard; keeps UI minimal while covering the frequent “grab a serial number” case. (OCR as a general pattern exists in OneNote/Lens.) citeturn0search9turn0search3  

9) **Haptics for Precision** — Light tick when a **snap** occurs; medium click when an **annotation commits**; gentle nudge on **constraint violation**. Respect system haptics settings. citeturn1search1turn1search4  

10) **Left‑hand Mode** — Repositions quick actions and layer panel to the left; remembers per‑device.

11) **Search & Filter** — Search across labels/tags/notes; quick filters (by date, layer kind, has redaction, has measurements).

12) **Multi‑Page Scan Session** — For document‑centric tasks: scan several pages (auto‑crop each), re‑order, annotate individually, export as one PDF.

> These are additive to your existing tools and exports; they’re focused on routine use and avoid “feature sprawl.”

---

## 1) Updated architecture touchpoints

- **editor/tools**: add `AreaTool`, `EyedropperTool`, `PixelateBrushTool`.  
- **editor/ui**: `MagnifierController`, `QuickActionsView`, `HandednessController`.  
- **editor/export**: add `ExportProfile` + sharesheet integration.  
- **editor/ocr**: wire “Copy Text” action to existing `OcrProvider`.  
- **camera/**: extend to multi‑page capture with auto‑crop suggest.  
- **core/palette/**: small helper around Jetpack Palette + contrast check.

---

## 2) One‑Shot **Builder Prompt** (paste to Codex)

```
SYSTEM GOAL
Upgrade the Pro Annotations baseline with everyday-use features that are sophisticated but simple to use, without UI bloat. Implement all items below in one PR, modifying existing files where possible.

NEW FEATURES
A) Polygon Area/Perimeter Tool (calibrated units). 
B) Precision Loupe (Magnifier API with legacy fallback). 
C) Eyedropper + Smart Palettes (Jetpack Palette + contrast-aware text color). 
D) Pixelate Brush + Blur Brush (non-destructive masks; flatten on Finalize). 
E) Auto-crop & Perspective Suggest on import (user-confirmed). 
F) Favorites & Recents (tools/styles/presets) + Quick Actions palette with left/right-handed layouts. 
G) One-Tap Export Profiles integrated with Android Sharesheet; add ChooserAction on Android 14+. 
H) Direct OCR→Clipboard from region long-press. 
I) Haptic feedback: snaps, commits, constraint violations; respect system settings. 
J) Search & Filter panel. 
K) Multi-page scan sessions (capture, reorder, annotate, export as one PDF).

TECH
- Kotlin, AndroidX, Material 3. Use existing architecture (Unified editor, layers, snapping, OCR provider, exports, vault). Avoid new heavy dependencies; keep providers pluggable.

FILES (add/edit)
- editor/tools/{AreaTool.kt, EyedropperTool.kt, PixelateBrushTool.kt}
- editor/ui/{MagnifierController.kt, QuickActionsView.kt, HandednessController.kt, SearchFilterSheet.kt}
- core/palette/{PaletteExt.kt, Contrast.kt}
- editor/export/{ExportProfiles.kt, SharesheetActions.kt}
- camera/{MultiPageSession.kt, AutoCropSuggester.kt}
- editor/ocr/{CopyRegionAction.kt}
- res/layout/{view_quick_actions.xml, sheet_search_filter.xml}
- res/xml/{shortcuts.xml (if needed)}
- manifest and navigation updates

IMPLEMENTATION DETAILS
1) Area/Perimeter Tool
- Create Annotation.AreaPolygon(points: List<Vec2>, isClosed: Boolean, label:String?). Display live area+perimeter based on current Page.calibration (mm²/in²). Use polygon area formula; show centroid marker.
- Snaps to endpoints/midpoints and angle ticks.

2) Magnifier (Loupe)
- MagnifierController: On drag/handle move, show Magnifier(view).update(x,y); hide on up/cancel. Fallback: draw a circular zoom overlay using Canvas with an offscreen bitmap snapshot for API<28.

3) Eyedropper & Smart Palettes
- EyedropperTool: sample pixel under crosshair in rectified image space; set current stroke/fill/text color.
- Generate complementary swatches using Palette.from(bitmap).generate() and compute contrast; pick label text color automatically.

4) Pixelate & Blur
- PixelateBrushTool writes a mask path + effect=PIXELATE(tile=8..24px) or BLUR(radius=4..16). Render non-destructively; exporter flattens these on Finalize.

5) Auto-crop Suggest
- AutoCropSuggester: detect strong quadrilateral using simple edge/contour heuristics; present handles; if accepted, apply RectifyTool with this quad as default.

6) Favorites & Recents; Quick Actions
- Persist favorite actions in DataStore. QuickActionsView shows up to 6 starred actions; “Recents” row shows last 5 tools/styles.
- HandednessController flips panel anchor; remember per-device.

7) Export Profiles & Sharesheet
- ExportProfiles: define presets (PDF+watermark flattened, Image+redacted, AnnotPkg). 
- SharesheetActions: For Android 14+, add ChooserAction buttons for the three presets; otherwise, show an in-app bottom sheet and forward to Intent.ACTION_SEND.

8) OCR→Clipboard
- CopyRegionAction: long-press region → bitmap crop → OcrProvider.recognize → copy first block to clipboard with a toast.

9) Haptics
- On snap: performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE or KEYBOARD_TAP). On commit: virtual key heavy click. On constraint violation: VibrationEffect with low amplitude pattern, gated by settings.

10) Search & Filter
- SearchFilterSheet: text query matches annotation meta (label/tags) and document title; toggles for layer kinds, date range, "has measurements/redaction".

11) Multi-page Session
- MultiPageSession: collect N captures with AutoCropSuggester; reorder via drag; each page becomes a Page in a Document; batch-annotate via templates; export as one PDF with per-page annotations.
```

---

## 3) Key code skeletons

### 3.1 Area/Perimeter (units-aware)

```kotlin
// editor/tools/AreaTool.kt
class AreaTool(private val doc: Document) : Tool {
    private val points = mutableListOf<Vec2>()
    override fun onDown(p: Vec2) { points += p }
    override fun onMove(p: Vec2) { if (points.isNotEmpty()) points[points.lastIndex] = p }
    override fun onDoubleTap() { closeAndCommit() }

    private fun closeAndCommit() {
        if (points.size < 3) return
        val page = doc.currentPage()
        val areaPx2 = polygonArea(points)
        val perimPx = perimeter(points)
        val scale = page.calibration?.scalePerPixel ?: 1.0
        val unit = page.calibration?.unit ?: "px"
        val area = areaPx2 * scale * scale
        val perim = perimPx * scale
        val label = "A=%.2f %s²  P=%.2f %s".format(Locale.US, area, unit, perim, unit)
        commitAnnotation(Annotation.AreaPolygon(points.toList(), true, label))
    }

    private fun polygonArea(ps: List<Vec2>): Double {
        var s = 0.0
        for (i in ps.indices) {
            val j = (i + 1) % ps.size
            s += ps[i].x * ps[j].y - ps[j].x * ps[i].y
        }
        return kotlin.math.abs(s) * 0.5
    }
    private fun perimeter(ps: List<Vec2>): Double =
        (ps.indices).sumOf { i -> ps[i].distanceTo(ps[(i+1)%ps.size]) }
}
```

*Pattern matches Acrobat’s distance/perimeter/area tools, adapted to calibrated photos.* citeturn0search2

---

### 3.2 Precision Loupe (Magnifier)

```kotlin
// editor/ui/MagnifierController.kt
class MagnifierController(private val host: View) {
    private val magnifier: Magnifier? = if (Build.VERSION.SDK_INT >= 28) Magnifier(host) else null
    private var legacy: LegacyLoupeOverlay? = null

    fun showAt(p: PointF) {
        if (magnifier != null) magnifier.show(p.x, p.y) else legacyShow(p)
    }
    fun update(p: PointF) {
        if (magnifier != null) magnifier.show(p.x, p.y) else legacyShow(p)
    }
    fun hide() { magnifier?.dismiss(); legacy?.dismiss() }

    private fun legacyShow(p: PointF) {
        if (legacy == null) legacy = LegacyLoupeOverlay(host.context, host)
        legacy?.showAt(p)
    }
}
```
*Uses the platform Magnifier where available; provides a minimal overlay fallback.* citeturn0search18

---

### 3.3 Eyedropper + Palette helpers

```kotlin
// core/palette/PaletteExt.kt
object PaletteExt {
    fun from(bitmap: Bitmap): Palette = Palette.from(bitmap).clearFilters().generate()
    fun bestTextColor(bg: Int): Int {
        val white = Color.WHITE; val black = Color.BLACK
        return if (contrastRatio(bg, white) >= 4.5) white else black
    }
    fun contrastRatio(bg: Int, fg: Int): Double {
        fun l(c: Int): Double {
            fun ch(x: Int): Double {
                val v = (x / 255.0); return if (v <= 0.03928) v/12.92 else ((v+0.055)/1.055).pow(2.4)
            }
            val r = ch(Color.red(c)); val g = ch(Color.green(c)); val b = ch(Color.blue(c))
            return 0.2126*r + 0.7152*g + 0.0722*b
        }
        val L1 = max(l(bg), l(fg)); val L2 = min(l(bg), l(fg))
        return (L1 + 0.05) / (L2 + 0.05)
    }
}
```

```kotlin
// editor/tools/EyedropperTool.kt (excerpt)
override fun onTap(p: Vec2) {
    val bmp = currentBitmapInImageSpace()
    val color = bmp.getPixel(p.x.toInt().coerceIn(0, bmp.width-1), p.y.toInt().coerceIn(0, bmp.height-1))
    style.setStrokeColor(color)
    style.setTextColor(PaletteExt.bestTextColor(color))
    updatePaletteSwatches(PaletteExt.from(bmp))
}
```
*Leverages Jetpack Palette to derive useful swatches from the current image.* citeturn0search4

---

### 3.4 Pixelate brush (non‑destructive; flattened on export)

```kotlin
// editor/tools/PixelateBrushTool.kt
class PixelateBrushTool : Tool {
    private val path = Path()
    var tile: Int = 16
    override fun onDown(p: Vec2) { path.moveTo(p.x, p.y) }
    override fun onMove(p: Vec2) { path.lineTo(p.x, p.y) }
    override fun onUp(p: Vec2) { commitMask(path, Effect.Pixelate(tile)) }
}
```

```kotlin
// editor/export/BitmapEffects.kt (flattening)
fun applyPixelate(src: Bitmap, mask: Path, tile: Int): Bitmap {
    val out = src.copy(src.config, true)
    val c = Canvas(out)
    val bounds = RectF(); mask.computeBounds(bounds, true)
    val x0 = bounds.left.toInt(); val y0 = bounds.top.toInt()
    val x1 = bounds.right.toInt(); val y1 = bounds.bottom.toInt()
    val paint = Paint()
    for (y in y0 until y1 step tile) for (x in x0 until x1 step tile) {
        val cx = (x + min(tile, out.width - x) / 2)
        val cy = (y + min(tile, out.height - y) / 2)
        if (mask.contains(cx.toFloat(), cy.toFloat())) {
            val color = out.getPixel(cx, cy)
            paint.color = color
            c.drawRect(x.toFloat(), y.toFloat(), (x + tile).toFloat(), (y + tile).toFloat(), paint)
        }
    }
    return out
}
```
*Provides the familiar “pixelate” redaction seen in lightweight annotators.* citeturn0search6turn0search13

---

### 3.5 Sharesheet with **ChooserAction** shortcuts (Android 14+)

```kotlin
// editor/export/SharesheetActions.kt
fun shareWithProfiles(context: Context, outUri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, outUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("export", outUri)
    }
    val chooser = Intent.createChooser(send, "Share annotated output")
    if (Build.VERSION.SDK_INT >= 34) {
        val viewPdf = Icon.createWithResource(context, R.drawable.ic_pdf)
        val actionPdf = ChooserAction.Builder(viewPdf, "Export PDF", PendingIntent.getBroadcast(
            context, 0, Intent(context, ExportReceiver::class.java).putExtra("profile", "PDF"), FLAG_IMMUTABLE
        )).build()
        chooser.putExtra(Intent.EXTRA_CHOOSER_CUSTOM_ACTIONS, arrayOf(actionPdf))
    }
    context.startActivity(chooser)
}
```
*Keeps sharing simple while offering one‑tap export profiles on newer Android versions.* citeturn1search14turn1search2

---

### 3.6 Haptics on snaps/commits

```kotlin
fun View.tickSnap() = performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
fun View.clickCommit() = performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
```
*Aligns with Android’s haptic guidance; gate via settings.* citeturn1search1turn1search4

---

### 3.7 OCR → Clipboard

```kotlin
suspend fun copyTextFromRegion(bmp: Bitmap, roi: Rect): Boolean {
    val crop = Bitmap.createBitmap(bmp, roi.left, roi.top, roi.width(), roi.height())
    val blocks = ocrProvider.recognize(crop, null)
    val text = blocks.firstOrNull()?.text ?: return false
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("OCR", text))
    Toast.makeText(context, "Copied: ${'$'}{text.take(60)}…", Toast.LENGTH_SHORT).show()
    return true
}
```
*Mirrors the “copy text from picture” flow popular in OneNote/Lens.* citeturn0search9turn0search3

---

## 4) UI notes (no clutter)

- **Quick Actions** is a compact row (max 6 starred, 5 recent); long‑press any toolbar item to star/unstar.  
- **Loupe** appears only while dragging; hidden on release.  
- **Export profiles** live in the export menu but are also available as share‑sheet buttons on Android 14+.  
- **Left‑hand mode** toggle in Settings; flips Quick Actions + layer panel anchor.  
- **Search & Filter** is a bottom sheet, not a full page.

---

## 5) Acceptance tests (added/updated)

1) **Area tool** — Close polygon on a calibrated page; area/perimeter match expected within rounding. citeturn0search2  
2) **Loupe** — While dragging a handle, the magnifier shows a 3× zoom with a crosshair; hides on release. citeturn0search18  
3) **Eyedropper** — Sample a color; label text auto‑switches to black/white to meet contrast; swatches populate from Palette. citeturn0search4  
4) **Pixelate brush** — Draw over a face; export “Flattened PDF” → region is pixelated in output. citeturn0search6turn0search13  
5) **Auto‑crop suggest** — Import a document photo; suggested quad appears; accept → rectified page opens. citeturn0search3  
6) **Favorites** — Long‑press “Dimension (mm)” to star; it appears in Quick Actions; left‑hand mode flips its placement.  
7) **Sharesheet** — On Android 14+, “Export PDF” appears as a chooser action; older versions show normal share sheet. citeturn1search14turn1search2  
8) **OCR→Clipboard** — Long‑press region with printed text; “Copy Text” copies OCR result to clipboard. citeturn0search9  
9) **Haptics** — A small tick occurs on snaps; heavier click on commit; disable haptics globally → app respects setting. citeturn1search1  
10) **Search & Filter** — Filter for “has measurements”; only pages containing Dimension/Area annotations are shown.  
11) **Multi‑page** — Capture 3 pages; reorder; export single PDF; each page has its own annotations.

---

## 6) References (for patterns & APIs)

- Acrobat measurement tools (distance, perimeter, area). citeturn0search2  
- Microsoft Lens (crop, annotate with pen/text, reorder, sharing). citeturn0search3  
- Skitch/Evernote annotation (pixelate/blur, arrows, shapes). citeturn0search6turn0search13  
- Android Palette API (extract prominent colors). citeturn0search4  
- Android Magnifier (loupe). citeturn0search18  
- Android Sharesheet + ChooserAction (custom actions, sharing). citeturn1search2turn1search14  
- Android Haptics guidance. citeturn1search1turn1search4

---

**Use the One‑Shot Builder Prompt** to generate the PR. These additions stay out of the way until needed and make the daily flow (point‑and‑annotate, measure, redact, share) substantially faster, without turning the app into a “bells & whistles” bundle.

---

# Appendices — User-Focused Specs

## Appendix A — Expanded Annotations (User-Focused)
This is your **complete guide to annotations**, the core of the app. It explains what each tool does, how to style it fast, and the quickest gestures to get things done with precision.

---

### 1) The Annotation Palette (What you can place)
- **Arrows**
  - Straight, Curved, and Double‑headed
  - Leader arrow (text attached)
- **Lines**
  - Straight line, Polyline, Dashed line
  - Angle marker (two legs with angle readout)
- **Shapes**
  - Rectangle / Rounded rectangle
  - Ellipse / Circle
  - Polygon (any number of points)
  - Cloud (freeform “review bubble”)
  - Star, Triangle
  - Brace **{ }** and Bracket **[ ]**
- **Callouts**
  - Box callout, Rounded callout, Cloud callout
  - Pointer styles: arrow / line / curved leader
- **Text**
  - Plain text, Rich text (bold/italic/underline)
  - Inline label (auto-sized chip)
  - Numbered marker (1, 2, 3…)
- **Emphasis & Markup**
  - Highlighter (see‑through marker)
  - Freehand ink (pen/pencil)
  - Cross‑out / Tick ✓ ✗
- **Stamps & Symbols**
  - Status chips: **OK / NG / HOLD**
  - Hazard, Attention, Info
  - Custom stamp sets (user presets)
- **Measurement Annotations**
  - Linear dimension (with arrowheads)
  - Distance line (no tails)
  - Perimeter / Polyline length
  - Area (polygon area + centroid)
  - Angle, Radius / Diameter, Scale bar
- **Redaction (for privacy)**
  - Blur brush, Pixelate (mosaic) brush

> Tip: Star your most‑used tools to pin them into **Quick Actions** (max 6).

---

### 2) Fast Placement (How to draw quickly)
- **Tap** to insert default‑sized items (text, stamp, chip). Start typing to edit text.
- **Drag** to draw lines/shapes with a live **loupe** (magnifier) near your finger.
- **Two‑finger drag** to pan the canvas; **pinch** to zoom; **twist** with two fingers to rotate selected items (when rotation is enabled for that tool).
- **Long‑press** on a handle to see numeric values (length/angle/area) where applicable.
- **Double‑tap** a selected object to open its **Style Sheet** (see §4).

---

### 3) Precision & Snapping (Keep things tidy)
- **Snapping**: endpoints, midpoints, intersections, 0°/45°/90° angle ticks.
- **Guides**: temporary smart guides appear to align with nearby objects.
- **Constraints**: hold during drag to **lock angle** or **keep aspect ratio** (toggle in settings if you prefer one‑hand use).
- **Loupe**: appears only while dragging; shows a zoomed view and the exact point.
- **Nudge**: with arrow controls (±1 px) or long‑press for coarse step (±10 px).

---

### 4) Styling System (Readable by default, customizable when needed)
- **Stroke**: width, dash, cap, join, arrowheads (start/end), opacity.
- **Fill**: solid, none, or semi‑transparent (for highlightable shapes).
- **Text**: font size, weight, alignment, background chip on/off, padding.
- **Auto‑contrast**: text color flips for legibility; optional outline for thin fonts.
- **Color pickers**: swatches, **image eyedropper**, and **smart palette** (dominant colors extracted from the image).
- **Style Presets**: save any style as a preset (e.g., “Defect‑A / Red 2pt / Arrow”).
- **Theme Packs**: group of presets you can switch in one tap (e.g., “QA set”).

> Presets remember **tool + style** together, so your next arrow or callout is consistent without re‑tuning options.

---

### 5) Tool Details (What each can do)

#### 5.1 Arrows
- Types: straight, curved (bezier), double‑headed, leader arrow.
- Options: head type (triangle, open, dot), head size, tail cap; curvature control for curved arrows.
- Quick: tap = default arrow; drag = custom length; double‑tap = edit head/tail.

#### 5.2 Lines
- Straight / Polyline with vertex add/remove via tap on path.
- Dashes, end caps; **Angle marker** mode shows angle readout between two legs.

#### 5.3 Shapes
- **Rect / Round‑rect / Ellipse / Polygon / Triangle / Star / Cloud / Brace / Bracket.**
- Resize with corner/edge handles; hold to preserve aspect ratio (circle/square).
- Polygon: tap to add vertex; long‑press vertex to delete; close path to fill.

#### 5.4 Callouts
- Box, rounded or cloud body; pointer as **straight** or **curved leader**.
- Auto‑layout keeps text readable; callout body grows with content.
- Pointer can re‑anchor by dragging the tip; body stays put.

#### 5.5 Text
- Plain or Rich (bold/italic/underline, bullet list, numbered list).
- **Inline label (chip)**: auto‑size, optional border; great for micro‑tags.
- **Numbered marker**: auto‑increment; re‑order updates numbers.

#### 5.6 Emphasis
- **Highlighter**: translucent color; doesn’t obscure underlying image.
- **Freehand ink**: smooth stroke; thickness options; can be converted to shape.

#### 5.7 Stamps & Symbols
- One‑tap placement; drag to change size; text inside chips is editable.
- Custom stamp sets: build your own library (e.g., “Factory QA”, “Field Report”).

#### 5.8 Measurement Annotations
- **Linear dimension**: two anchors + text box; arrowheads configurable.
- **Distance line**: minimal readout on the path (no tails).
- **Perimeter**: length of a polyline; **Area**: polygon area + centroid cross.
- **Angle**: two rays; shows angle; can snap to right/45°.
- **Radius/Diameter**: from circle; **Scale bar** for context.
- Units follow your **calibration**; change in Settings anytime.

---

### 6) Editing & Layout (Work like a pro)
- **Multi‑select** (lasso or long‑press → Select multiple) → **Align** (L/C/R/T/M/B) & **Distribute** (H/V spacing).
- **Group / Ungroup**, **Lock**, **Duplicate**, **Flatten** (turn into pixels).
- **Layers panel**: reorder, hide/show, lock/unlock; search by type (“callout”, “dimension”).

---

### 7) Presets, Favorites & Recents (Speed boosters)
- **Presets**: save named combinations (tool + style). Long‑press a preset to update it.
- **Favorites**: pin up to 6 tools/presets to Quick Actions bar.
- **Recents**: last 5 styles; swipe the bar to reveal more without opening the full style sheet.

---

### 8) Accessibility & Comfort
- Large touch targets (adjustable handle size).
- **Left‑hand mode** flips the bars; **Haptics** on snaps and commits.
- Color‑blind friendly palette options.

---

### 9) Export Behavior (What happens to annotations)
- **Flattened Image/PDF**: everything baked in (good for sharing and printing).
- **Annotation Package (.annotpkg)**: keeps layers editable for later changes.
- **Redactions** (blur/pixelate) are **always baked in** when exporting flattened outputs.

---

### 10) Ready‑to‑Use Templates (Optional)
- **QA Defect Pack**: red arrow + callout + dimension line presets.
- **Inspection Pack**: numbered markers + brackets + area readout.
- **Field Report Pack**: yellow highlighter + info stamp + rounded callout.

---

### 11) Quick Recipes
- **Point & explain**: Arrow → Callout (type text) → Done.
- **Measure & mark**: Calibrate → Linear dimension → Save preset → Re‑use.
- **Review bubble**: Cloud shape around region → Callout pointer → Export PDF.
- **Step tags**: Place numbered markers → Auto‑reorder as needed → Export image.

---

#### Pro tips
- Use **loupe + snapping** for pin‑point accuracy without over‑zooming.
- Save presets for recurring tasks; pin the top 3 into **Favorites**.
- Keep **annotation noise low**: prefer callouts and labels over multiple arrows when possible.

## Appendix B — Hatching & Pattern Fills (User-Focused Add-On)
Add **hatches** (a.k.a. “hashes”) and **pattern fills** to your annotations and shapes. Great for highlighting regions without heavy color fills, improving print legibility, and creating compact visual codes.

---

### 1) Where you can use hatches
- **Shapes**: Rectangle, Round‑rect, Ellipse/Circle, Polygon, Cloud, Brace/Bracket areas.
- **Callout bodies**: optional patterned background for emphasis.
- **Freehand regions**: draw a closed freehand shape and apply a hatch.
- **Measurement areas**: area polygons can show subtle hatch instead of solid fill.

> Tip: Hatches are **non-destructive** and can be toggled on/off per object.

---

### 2) Hatch & Pattern Styles (mix and match)
- **Lines**
  - Parallel (0–180°)
  - Diagonal (e.g., 45°, 135°)
  - Crosshatch (two angles)
  - Triple hatch (three angles)
  - Zigzag (line + periodic offsets)
  - Dashed line hatch
- **Points/Dots**
  - Uniform dot grid
  - Stipple (random jittered dots)
  - Hex/triangular dot lattices
- **Grids**
  - Square grid
  - Isometric grid (60°/120°)
- **Textures (vector)**
  - Brick, Stripes, Wave, Contour (follows shape outline)
  - Noise line (slight angle jitter for hand‑drawn look)

Each style can be saved as a **Preset** with its parameters.

---

### 3) Controls (per object)
- **Angle**: 0–179° (crosshatch uses Angle A/B; triple uses A/B/C).
- **Spacing**: 0.5–50 px (or in real units when calibrated).
- **Thickness**: 0.5–10 px (line weight or dot radius).
- **Phase (offset)**: shift the pattern start relative to object bounds.
- **Jitter**: 0–100% (randomness for “hand‑drawn” or stipple looks).
- **Opacity**: 0–100% (pattern alpha; fill color remains none).
- **Blend**: normal / multiply / screen (for photo‑based contrast control).
- **Clip to shape**: on by default (no spillover outside the geometry).
- **Scale behavior**: keep absolute screen size **or** scale with zoom/object.

> Quick action: **Long‑press Fill** → choose **Pattern** → pick a style, adjust sliders.

---

### 4) Color & Contrast
- **Auto‑contrast**: pattern color adapts for legibility vs. image background.
- **Dual‑tone**: optional second color for crosshatch or dot accents.
- **Eyedropper** supported; **Smart palette** suggests legible tones.

---

### 5) Performance & Print
- Patterns are **vector** while editing for smooth pan/zoom.
- On export:
  - **Flattened image/PDF**: pattern is baked with anti‑aliasing.
  - **.annotpkg**: pattern params remain editable.
- **Print‑friendly** presets** keep thin lines ≥ 0.25 pt to avoid dropout.

---

### 6) Accessibility
- **Low‑vision mode**: boost thickness + opacity automatically.
- **Color‑blind safe**: monochrome pattern presets emphasize geometry, not hue.
- **Legend chips**: auto‑generate a small legend (pattern sample + label).

---

### 7) Presets (ready to use)
- **QA‑Light Hatch**: 45° lines, 2 px, 10 px spacing, 60% opacity.
- **QA‑Cross Dense**: 45°/135°, 2 px, 6 px spacing, 40% opacity.
- **Stipple‑Soft**: dot radius 1.5 px, spacing 10 px, jitter 20%.
- **Inspection‑Grid**: square grid, 1 px, 12 px spacing, 35% opacity.
- **Contour‑Wave**: wave texture, 1.5 px, period 14 px, 45% opacity.
- **Isometric‑Light**: 60°/120° crosshatch, 1.5 px, 10 px spacing.

Long‑press any preset to update it with current settings.

---

### 8) Editing & Layering
- **Reorder** pattern below/above the object’s **text** and **stroke**:
  - Modes: **Under stroke**, **Between fill & stroke**, **Over stroke**.
- Combine with **labels/callouts**; text remains solid for readability.
- **Mask regions**: add “holes” by subtracting smaller shapes (non‑destructive).

---

### 9) Quick Recipes
- **Subtle emphasis without color**: Rectangle → Pattern: 45° thin lines → Export PDF.
- **Different zones, same photo**: Polygon A (stipple) + Polygon B (crosshatch). Add legend.
- **Measurement with clarity**: Area polygon → Pattern: light grid → Keep dimension text bold.
- **Privacy‑aware highlight**: Cloud shape over sensitive region → Pattern: dense crosshatch + light blur under it → Export image.

---

### 10) Defaults (sane starting points)
- Angle 45°, spacing 12 px, thickness 1.5 px, opacity 40%, clip on, scale = absolute.
- Crosshatch uses 45°/135°. Stipple jitter 15%.
- Print‑safe minimum line weight 0.25 pt.

---

### 11) Touch gestures
- **Two‑finger twist** on a selected patterned shape → change **angle** live.
- **Pinch** while holding the pattern handle → adjust **spacing**.
- **Double‑tap** the pattern chip in the style bar → cycle through presets.

---

### 12) Export behavior
- **Flattened** outputs bake patterns at target DPI (no moiré).
- **Annotation package** keeps pattern parameters for full editability.
- Redactions still flatten permanently (patterns don’t affect privacy guarantees).

---

#### Notes
- Hatches are available wherever **Fill = Pattern** is supported.
- For photos with strong texture, consider **multiply** blend for clear contrast.
- Use **legends** in multi‑pattern documents to keep meaning obvious.
