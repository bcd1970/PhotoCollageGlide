# IME Replacement Bug — Fix Plan & Kotlin Patch (Artifact)

**Problem**  
Tapping a keyboard suggestion (e.g., Gboard autocorrect) **appends** the suggestion instead of **replacing** the last typed word in a custom text editor that implements a custom `InputConnection`.

**Root cause (typical)**  
The IME attempts to *delete* the current word via `deleteSurroundingText(...)` (char-count variant) and/or relies on correct composing-span lifecycle. If the delete path isn’t implemented against **your** text model (only the `BaseInputConnection`’s internal `Editable`) or composing state/selection isn’t reported correctly, the delete step fails and the IME falls back to simple `commitText` → **append**.

---

## Minimal Patch Checklist (do these)

1. **Implement both delete variants** against your model:
   - `deleteSurroundingText(before, after)`
   - `deleteSurroundingTextInCodePoints(before, after)`
2. **Implement `finishComposingText()`** to clear your composing span.
3. **After every text/selection/composition change**, call `InputMethodManager.updateSelection(view, selStart, selEnd, compStart, compEnd)`.
4. **Return correct surrounding text** via `getTextBeforeCursor(...)`, `getTextAfterCursor(...)`, `getSelectedText(...)`.
5. In `onCreateInputConnection(...)`, set coherent `EditorInfo` and call `EditorInfoCompat.setInitialSurroundingSubText(...)` with the current caret; set `initialSelStart/End`.
6. Ensure `commitText(...)` and `setComposingText(...)` update caret correctly and end with `updateSelection`.

---

## Kotlin Patch Snippets

> Drop these into your `SimpleInputConnection` (or subclass) that edits your `TextBox` model (`tb`). Adjust field names as needed.

### Helpers

```kotlin
private fun notifyImeSelectionChanged() {
    val imm = context.getSystemService(InputMethodManager::class.java)
    val compStart = tb.composingStart ?: -1
    val compEnd   = tb.composingEnd   ?: -1
    imm.updateSelection(
        thisView, // reference to your DrawingSurfaceView
        tb.selectionStart,
        tb.selectionEnd,
        compStart,
        compEnd
    )
}

private fun clearEmptyComposingIfNeeded() {
    if (tb.hasComposing() && tb.composingStart == tb.composingEnd) {
        tb.clearComposing()
    }
}

private fun invalidateCaret() {
    // trigger your view to redraw caret/selection
    thisView.invalidate()
}
```

### A) Delete (char-count) — **MUST** implement

```kotlin
override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
    val selStart = tb.selectionStart
    val selEnd   = tb.selectionEnd
    if (selStart != selEnd) {
        tb.text.delete(selStart, selEnd)
        tb.caretIndex = selStart
    } else {
        val start = (tb.caretIndex - beforeLength).coerceAtLeast(0)
        val end   = (tb.caretIndex + afterLength).coerceAtMost(tb.text.length)
        if (end > start) {
            tb.text.delete(start, end)
            tb.caretIndex = start
        }
    }
    clearEmptyComposingIfNeeded()
    notifyImeSelectionChanged()
    invalidateCaret()
    return true
}
```

### B) Delete (code-point) — emoji/surrogates safe

```kotlin
override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
    fun moveLeftByCodePoints(index: Int, cps: Int): Int {
        var i = index
        repeat(cps.coerceAtLeast(0)) { i = Character.offsetByCodePoints(tb.text, 0, i, -1) }
        return i
    }
    fun moveRightByCodePoints(index: Int, cps: Int): Int {
        var i = index
        repeat(cps.coerceAtLeast(0)) { i = Character.offsetByCodePoints(tb.text, 0, i, +1) }
        return i
    }

    val selStart = tb.selectionStart
    val selEnd   = tb.selectionEnd
    if (selStart != selEnd) {
        tb.text.delete(selStart, selEnd)
        tb.caretIndex = selStart
    } else {
        val start = moveLeftByCodePoints(tb.caretIndex, beforeLength)
        val end   = moveRightByCodePoints(tb.caretIndex, afterLength)
        if (end > start) {
            tb.text.delete(start, end)
            tb.caretIndex = start
        }
    }
    clearEmptyComposingIfNeeded()
    notifyImeSelectionChanged()
    invalidateCaret()
    return true
}
```

### C) Finish composing lifecycle

```kotlin
override fun finishComposingText(): Boolean {
    if (tb.hasComposing()) {
        tb.clearComposing()
        notifyImeSelectionChanged()
        invalidateCaret()
    }
    return true
}
```

### D) Commit text

```kotlin
override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
    beginBatchEdit()

    // Replace selection or composing range if any; else insert at caret
    val hasSel = tb.selectionStart != tb.selectionEnd
    val hasComp = tb.hasComposing()

    val replaceStart: Int
    val replaceEnd: Int

    if (hasSel) {
        replaceStart = tb.selectionStart
        replaceEnd = tb.selectionEnd
    } else if (hasComp) {
        replaceStart = tb.composingStart!!
        replaceEnd = tb.composingEnd!!
    } else {
        replaceStart = tb.caretIndex
        replaceEnd = tb.caretIndex
    }

    tb.text.replace(replaceStart, replaceEnd, text)

    // Position caret per EditText semantics
    val advance = when {
        newCursorPosition > 0 -> text.length + (newCursorPosition - 1)
        newCursorPosition < 0 -> newCursorPosition // negative moves left from start of inserted
        else -> text.length // common case: place after inserted
    }
    tb.caretIndex = (replaceStart + advance).coerceIn(0, tb.text.length)

    // Clear composing on commit
    tb.clearComposing()
    // Collapse selection at caret
    tb.selectionStart = tb.caretIndex
    tb.selectionEnd = tb.caretIndex

    endBatchEdit()
    notifyImeSelectionChanged()
    invalidateCaret()
    return true
}
```

### E) Set composing text

```kotlin
override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
    beginBatchEdit()

    if (!tb.hasComposing()) {
        tb.expandComposingToCurrentWord() // your implementation: set composingStart/End over current word
    }

    val cs = tb.composingStart ?: tb.caretIndex
    val ce = tb.composingEnd ?: tb.caretIndex
    tb.text.replace(cs, ce, text)

    // Update composing range to the replaced text
    tb.composingStart = cs
    tb.composingEnd = cs + text.length
    tb.caretIndex = tb.composingEnd!!

    endBatchEdit()
    notifyImeSelectionChanged()
    invalidateCaret()
    return true
}
```

### F) Selection & composing region

```kotlin
override fun setSelection(start: Int, end: Int): Boolean {
    tb.selectionStart = start.coerceIn(0, tb.text.length)
    tb.selectionEnd   = end.coerceIn(0, tb.text.length)
    tb.caretIndex = tb.selectionEnd
    notifyImeSelectionChanged()
    invalidateCaret()
    return true
}

override fun setComposingRegion(start: Int, end: Int): Boolean {
    tb.composingStart = start.coerceIn(0, tb.text.length)
    tb.composingEnd   = end.coerceIn(0, tb.text.length)
    notifyImeSelectionChanged()
    invalidateCaret()
    return true
}
```

### G) Surrounding text providers (IMEs use these for word boundaries)

```kotlin
override fun getTextBeforeCursor(n: Int, flags: Int): CharSequence {
    val end = tb.caretIndex
    val start = (end - n).coerceAtLeast(0)
    return tb.text.subSequence(start, end)
}

override fun getTextAfterCursor(n: Int, flags: Int): CharSequence {
    val start = tb.caretIndex
    val end = (start + n).coerceAtMost(tb.text.length)
    return tb.text.subSequence(start, end)
}

override fun getSelectedText(flags: Int): CharSequence? {
    val s = tb.selectionStart
    val e = tb.selectionEnd
    return if (s != e) tb.text.subSequence(s, e) else null
}
```

---

## `onCreateInputConnection(...)` essentials

```kotlin
override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
    outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
                         InputType.TYPE_TEXT_FLAG_AUTO_CORRECT or
                         InputType.TYPE_TEXT_FLAG_MULTI_LINE

    outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_DONE

    // Coherent initial selection
    outAttrs.initialSelStart = tb.selectionStart
    outAttrs.initialSelEnd   = tb.selectionEnd

    // Surrounding context for IME heuristics
    EditorInfoCompat.setInitialSurroundingSubText(outAttrs, tb.text, tb.caretIndex)

    // Return your custom connection (use BaseInputConnection(false))
    return object : BaseInputConnection(thisView, /* fullEditor = */ false) {
        // ... include overrides from sections A–G above ...
    }
}
```

---

## Diagnostics (prove it’s fixed)

1. Add logging for each override (`deleteSurroundingText*`, `commitText`, `setComposingText`, `finishComposingText`).
2. In `logcat`, type `teh` then tap `the` suggestion.
   - **Before fix:** no handled `deleteSurroundingText(...)`, then `commitText("the")` → append.
   - **After fix:** `deleteSurroundingText(...)` removing prior word, then `commitText("the")` → replacement.
3. Verify `updateSelection(...)` is called after every mutation with accurate composing range (or `-1, -1` when none).

---

## Optional “Can’t Miss” fallback

Embed a tiny, invisible `EditText` and delegate `onCreateInputConnection()` to it; mirror text to your model. This piggybacks on the platform’s robust IME handling while you retain custom drawing. Use only if you prefer not to maintain the custom `InputConnection` logic.

---

### Summary
With the char-based `deleteSurroundingText(...)` implemented against your *own* buffer, correct composing lifecycle (`finishComposingText`), accurate selection/composition reporting (`updateSelection`), and proper surrounding text, IME corrections replace the previous word instead of appending. Save, rebuild, test with Gboard — you should see correct replacement on the **first tap**.

