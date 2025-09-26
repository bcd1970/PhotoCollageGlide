# IME Fix — Report True Selection (Patch)

This patch ensures the IME (e.g., Gboard) sees the **real selection range** instead of a collapsed caret, so suggestion taps **replace** the last word instead of **appending**.

> Replace the helper and selection reporting in your `DrawingSurfaceView`/`SimpleInputConnection` with the snippets below. Keep your existing composing/commit/delete logic.

---

## 1) Always report the **true selection** to the IME

```kotlin
private fun imeUpdateSelection(tb: TextBox) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

    val selStart = min(tb.selStart, tb.selEnd).coerceIn(0, tb.text.length)
    val selEnd   = max(tb.selStart, tb.selEnd).coerceIn(0, tb.text.length)

    val compStart = if (tb.composingStart >= 0) tb.composingStart else -1
    val compEnd   = if (tb.composingStart >= 0) tb.composingEnd   else -1

    imm.updateSelection(this /* your view */, selStart, selEnd, compStart, compEnd)
}
```

Call `imeUpdateSelection(tb)` **after every** text/selection/composition mutation (you likely already do; this just needs to pass the real selection, not the caret twice).

---

## 2) Seed the **real selection** in `onCreateInputConnection(...)`

```kotlin
override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
    val tb = currentTextBox() ?: return null

    // ... your existing inputType / imeOptions / caps ...

    // Report actual selection (not just caret)
    outAttrs.initialSelStart = min(tb.selStart, tb.selEnd)
    outAttrs.initialSelEnd   = max(tb.selStart, tb.selEnd)

    // Keep your surrounding text call
    EditorInfoCompat.setInitialSurroundingSubText(outAttrs, tb.text, tb.caretIndex)

    // return your BaseInputConnection(false) instance
    return simpleInputConnection
}
```

---

## 3) Return the real selection in `getExtractedText(...)`

```kotlin
override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
    val t = tb() // however you obtain the active TextBox
    val et = ExtractedText()
    if (t == null) {
        et.text = ""
        et.selectionStart = 0
        et.selectionEnd = 0
        et.flags = 0
        return et
    }
    et.text = t.text.toString()
    val s = min(t.selStart, t.selEnd).coerceIn(0, t.text.length)
    val e = max(t.selStart, t.selEnd).coerceIn(0, t.text.length)
    et.selectionStart = s
    et.selectionEnd = e
    et.flags = 0
    return et
}
```

> Ensure your other methods (`commitText`, `setComposingText`, deletes) maintain `tb.selStart/tb.selEnd` consistently (collapse selection to caret when appropriate). With true selection reporting, IME suggestion taps will delete the previous word and insert the suggestion on the **first** tap.

---

## Quick test
1. Type `teh`.
2. Tap the suggestion `the`.
3. Observe: `teh` is **replaced** by `the` (no duplicate append).

If you still see appends, log calls to `deleteSurroundingText(...)` and confirm they now arrive **before** `commitText(...)`. If not, verify `updateSelection(...)` is being called with the **non-collapsed** selection when you actually have one.

