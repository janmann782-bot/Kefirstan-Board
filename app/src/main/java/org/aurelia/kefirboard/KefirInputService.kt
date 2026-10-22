package org.aurelia.kefirboard

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs

/**
 * Android IME. Features not implemented in this prototype are documented in README.
 * There is intentionally no INTERNET permission.
 */
class KefirInputService : InputMethodService() {
    private val prefs by lazy { Prefs(this) }
    private val wiki by lazy { WikiStore(this) }
    private val clips by lazy { SecureClipboard(this) }
    private lateinit var root: LinearLayout
    private var pane = "keys"
    private var shifted = false
    private var privateField = false
    private var wikiQuery = ""
    private var mathText = ""
    private var mathResult = MathEngine.Result("", emptyList())
    private var suggestionStrip: LinearLayout? = null
    private val clipboard by lazy { getSystemService(CLIPBOARD_SERVICE) as ClipboardManager }
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!privateField && isInputViewShown) recordClipboard()
    }
    private fun dp(v: Int) = ThemeUtil.dp(this, v)
    private fun bg() = ThemeUtil.background(prefs.dark)
    private fun fg() = ThemeUtil.foreground(prefs.dark)
    private fun keyBg() = ThemeUtil.key(prefs.dark)

    override fun onCreate() {
        super.onCreate()
        wiki.startImportIfNeeded()
        clipboard.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        clipboard.removePrimaryClipChangedListener(clipListener)
        super.onDestroy()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        val type = attribute?.inputType ?: 0
        val klass = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        privateField = (klass == InputType.TYPE_CLASS_TEXT &&
            variation in listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD)) ||
            (klass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        shifted = false
        if (::root.isInitialized) render()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!privateField) recordClipboard()
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(3), dp(5), dp(3), dp(6))
        }
        render()
        return root
    }

    private fun recordClipboard() {
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount < 1) return
        if (Build.VERSION.SDK_INT >= 33 &&
            clip.description.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true) return
        val text = clip.getItemAt(0).text?.toString() ?: return
        clips.add(text)
    }

    private fun button(label: String, flex: Float = 1f, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = if (label.length > 9) 12f else 18f
            gravity = Gravity.CENTER
            setTextColor(fg())
            background = ThemeUtil.shape(this@KefirInputService, keyBg())
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, dp(prefs.keyHeight), flex).apply {
                val margin = dp(prefs.keyGap)
                setMargins(margin, margin, margin, margin)
            }
        }
    }

    private fun line(parent: LinearLayout, height: Int? = null): LinearLayout {
        val row = LinearLayout(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                height?.let { dp(it) } ?: LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        parent.addView(row)
        return row
    }

    private fun toolbar() {
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(2), 0, dp(2), dp(3))
        }
        fun item(label: String, next: String, action: (() -> Unit)? = null) {
            val box = TextView(this).apply {
                text = label
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (pane == next) 0xFF17251B.toInt() else fg())
                background = ThemeUtil.shape(this@KefirInputService,
                    if (pane == next) ThemeUtil.ACCENT else keyBg())
                layoutParams = LinearLayout.LayoutParams(dp(62), dp(38)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    if (action != null) action() else { pane = next; render() }
                }
            }
            bar.addView(box)
        }
        item("АБВ", "keys")
        item("▣ Буфер", "clips")
        item("⌕ Вики", "wiki")
        item("∑", "math")
        item("⚙", "settings") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        item(if (prefs.ru) "RU" else "EN", "keys") {
            prefs.ru = !prefs.ru
            pane = "keys"
            render()
        }
        scroll.addView(bar)
        root.addView(scroll)
    }

    private fun render() {
        if (!::root.isInitialized) return
        root.removeAllViews()
        root.setBackgroundColor(bg())
        toolbar()
        when (pane) {
            "keys" -> keyboard(root)
            "clips" -> clipboardPanel()
            "wiki" -> wikiPanel()
            "math" -> mathPanel()
            else -> { pane = "keys"; keyboard(root) }
        }
    }

    private fun keyboard(parent: LinearLayout, wikiTyping: Boolean = false) {
        if (!wikiTyping && prefs.suggestions && !privateField) {
            suggestionStrip = line(parent, 34)
            updateSuggestions()
        } else suggestionStrip = null
        line(parent).also { row ->
            "1234567890".forEach { digit -> row.addView(touchKey(digit.toString(), digit.toString(), wikiTyping)) }
        }
        val rows = if (prefs.ru)
            listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
        else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { index, letters ->
            val row = line(parent)
            if (index == 2) row.addView(touchKey("⇧", "SHIFT", wikiTyping, 1.3f))
            letters.forEach { ch ->
                val value = if (shifted) ch.uppercase() else ch.toString()
                row.addView(touchKey(value, value, wikiTyping))
            }
            if (index == 2) row.addView(touchKey("⌫", "BACK", wikiTyping, 1.3f))
        }
        line(parent).also { row ->
            row.addView(touchKey(",", ",", wikiTyping))
            row.addView(touchKey("☺", "EMOJI", wikiTyping))
            row.addView(touchKey(if (prefs.ru) "Русский" else "English", "SPACE", wikiTyping, 4f))
            row.addView(touchKey(".", ".", wikiTyping))
            row.addView(touchKey("↵", "ENTER", wikiTyping, 1.4f))
        }
    }

    private fun touchKey(label: String, code: String, wikiTyping: Boolean, flex: Float = 1f): TextView {
        val key = button(label, flex) {}
        if (code == "SHIFT" && shifted) key.background = ThemeUtil.shape(this, ThemeUtil.ACCENT)
        var firstX = 0f
        var firstY = 0f
        var started = 0L
        var previousSteps = 0
        var movedCursor = false
        key.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    firstX = e.x; firstY = e.y
                    started = SystemClock.uptimeMillis()
                    previousSteps = 0; movedCursor = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val duration = SystemClock.uptimeMillis() - started
                    if (code == "SPACE" && !wikiTyping && duration > 280L) {
                        val steps = ((e.x - firstX) / dp(13)).toInt()
                        val delta = steps - previousSteps
                        if (delta != 0) {
                            repeat(abs(delta).coerceAtMost(15)) { moveCursor(delta > 0) }
                            previousSteps = steps
                            movedCursor = true
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = e.x - firstX
                    val dy = e.y - firstY
                    val elapsed = SystemClock.uptimeMillis() - started
                    if (dy > dp(72)) requestHideSelf(0)
                    else when {
                        code == "BACK" && dx < -dp(24) && !wikiTyping -> deleteWord()
                        code == "SPACE" && !wikiTyping && !movedCursor && abs(dx) > dp(25) && elapsed < 280L -> {
                            prefs.ru = !prefs.ru; render()
                        }
                        code == "SPACE" && movedCursor -> Unit
                        else -> process(code, wikiTyping)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> true
                else -> true
            }
        }
        return key
    }

    private fun process(code: String, wikiTyping: Boolean) {
        if (code == "SHIFT") { shifted = !shifted; render(); return }
        if (wikiTyping) {
            wikiQuery = when (code) {
                "BACK" -> wikiQuery.dropLast(1)
                "SPACE" -> wikiQuery + " "
                "ENTER" -> wikiQuery
                "EMOJI" -> wikiQuery
                else -> wikiQuery + code
            }
            wikiPanelReopen()
            return
        }
        when (code) {
            "BACK" -> currentInputConnection?.deleteSurroundingTextInCodePoints(1, 0)
            "SPACE" -> currentInputConnection?.commitText(" ", 1)
            "ENTER" -> {
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
            }
            "EMOJI" -> currentInputConnection?.commitText("🙂", 1)
            else -> currentInputConnection?.commitText(code, 1)
        }
        updateSuggestions()
    }

    private fun deleteWord() {
        val preceding = currentInputConnection?.getTextBeforeCursor(120, 0)?.toString() ?: return
        val amount = Regex("""\s*\S+\s*$""").find(preceding)?.value?.length ?: 1
        currentInputConnection?.deleteSurroundingText(amount, 0)
        updateSuggestions()
    }

    private fun moveCursor(right: Boolean) {
        val key = if (right) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
        currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    }

    private fun updateSuggestions() {
        val bar = suggestionStrip ?: return
        bar.removeAllViews()
        val preceding = currentInputConnection?.getTextBeforeCursor(50, 0)?.toString() ?: ""
        val prefix = Regex("""[\p{L}]{2,}$""").find(preceding)?.value ?: ""
        val matches = wiki.suggest(prefix, 3)
        if (matches.isEmpty()) {
            bar.addView(TextView(this).apply {
                text = if (wiki.isReady()) "  KefirBoard  •  Офлайн" else "  Индексация вики Аурелии..."
                textSize = 12f; setTextColor(ThemeUtil.muted(prefs.dark))
            })
        } else matches.forEach { title ->
            val box = button(title, 1f) {
                currentInputConnection?.deleteSurroundingText(prefix.length, 0)
                currentInputConnection?.commitText(title, 1)
                updateSuggestions()
            }
            box.textSize = 11f
            box.layoutParams = LinearLayout.LayoutParams(0, dp(31), 1f)
            bar.addView(box)
        }
    }

    private fun headline(label: String): TextView = TextView(this).apply {
        text = label
        textSize = 17f
        setTextColor(ThemeUtil.ACCENT)
        setPadding(dp(9), dp(6), dp(4), dp(6))
    }

    private fun clipboardPanel() {
        root.addView(headline(if (privateField) "Буфер отключен в защищенном поле" else "Буфер: локально, AES-GCM"))
        if (privateField) return
        recordClipboard()
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clips.recent(limit = 30).forEach { entry ->
            val row = line(list)
            row.addView(button(entry.text.take(55).replace("\n", " "), 5f) {
                currentInputConnection?.commitText(entry.text, 1)
                pane = "keys"; render()
            })
            row.addView(button(if (entry.pinned) "★" else "☆") {
                clips.pin(entry.id, !entry.pinned); render()
            })
            row.addView(button("×") { clips.delete(entry.id); render() })
        }
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(215)))
    }

    private fun wikiPanelReopen() {
        if (pane == "wiki") render()
    }

    private fun wikiPanel() {
        root.addView(headline("Аурелия • поиск по локальным статьям"))
        root.addView(headline("⌕  $wikiQuery"))
        if (!wiki.isReady()) root.addView(headline("Идет первая индексация выгрузки..."))
        val scroll = ScrollView(this)
        val result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wiki.search(wikiQuery, 12).forEach { article ->
            val display = article.title + "  ·  " + article.extract.replace("\n", " ").take(80)
            val row = line(result)
            row.addView(button(display, 4f) {
                currentInputConnection?.commitText(article.title, 1)
                pane = "keys"; render()
            }.apply { textSize = 12f })
        }
        scroll.addView(result)
        root.addView(scroll, LinearLayout.LayoutParams(-1, dp(130)))
        keyboard(root, wikiTyping = true)
    }

    private fun mathPanel() {
        root.addView(headline("Калькулятор • офлайн"))
        root.addView(headline(mathText.ifBlank { "Введите выражение" }))
        if (mathResult.answer.isNotEmpty()) {
            root.addView(headline(mathResult.answer))
            mathResult.steps.take(4).forEach { step ->
                root.addView(TextView(this).apply { text = step; textSize = 12f; setTextColor(fg()) })
            }
        }
        listOf(
            listOf("7", "8", "9", "/", "(", ")"),
            listOf("4", "5", "6", "*", "x", "^"),
            listOf("1", "2", "3", "-", "sqrt(", "⌫"),
            listOf("0", ".", "+", "=", "C", "Вставить")
        ).forEach { keys ->
            val row = line(root)
            keys.forEach { k ->
                row.addView(button(k, if (k == "Вставить") 2.1f else 1f) {
                    when (k) {
                        "C" -> { mathText = ""; mathResult = MathEngine.Result("", emptyList()) }
                        "⌫" -> mathText = mathText.dropLast(1)
                        "=" -> mathResult = MathEngine.solve(mathText)
                        "Вставить" -> {
                            if (mathResult.answer.isNotEmpty() && mathResult.answer != "Ошибка")
                                currentInputConnection?.commitText(mathResult.answer, 1)
                        }
                        else -> mathText += k
                    }
                    render()
                }.apply { textSize = 13f })
            }
        }
    }
}
