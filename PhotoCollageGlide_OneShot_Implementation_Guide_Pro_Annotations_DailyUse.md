
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
