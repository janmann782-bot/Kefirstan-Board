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
    private var symbols = false
    private var capsLocked = false
    private var lastShiftTouch = 0L
    private var privateField = false
    private var wikiQuery = ""
    private var mathText = ""
    private var chemistryInput = ""
    private var chemistryResult = ChemistryEngine.Result("", emptyList())
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
        capsLocked = false
        symbols = false
        pane = "keys"
        if (::root.isInitialized) render()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!privateField) recordClipboard()
    }

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(5), dp(5), dp(5), dp(6))
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

    private fun button(label: String, flex: Float = 1f, action: () -> Unit): TextView =
        TextView(this).apply {
            text = label
            textSize = when {
                label.length > 13 -> 11f
                label.length > 7 -> 12f
                else -> 16f
            }
            gravity = Gravity.CENTER
            includeFontPadding = false
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(fg())
            background = ThemeUtil.keyState(this@KefirInputService, prefs.dark)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(0, dp(prefs.keyHeight), flex).apply {
                val margin = dp(prefs.keyGap)
                setMargins(margin, margin, margin, margin)
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
        val barScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            fillViewport = true
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(1), 0, dp(1), dp(5))
        }
        fun item(symbol: String, caption: String, tab: String, action: (() -> Unit)? = null) {
            val active = pane == tab && action == null
            val group = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = ThemeUtil.shape(
                    this@KefirInputService,
                    if (active) ThemeUtil.ACCENT else ThemeUtil.panel(prefs.dark),
                    ThemeUtil.border(prefs.dark), 11
                )
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(44)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                contentDescription = caption
                setOnClickListener {
                    if (action != null) action()
                    else { pane = tab; render() }
                }
            }
            group.addView(TextView(this).apply {
                text = symbol
                textSize = 19f
                gravity = Gravity.CENTER
                includeFontPadding = false
                setTextColor(if (active) 0xFF17251B.toInt() else fg())
            }, LinearLayout.LayoutParams(-1, dp(26)))
            group.addView(TextView(this).apply {
                text = caption
                textSize = 9f
                gravity = Gravity.CENTER
                maxLines = 1
                includeFontPadding = false
                setTextColor(if (active) 0xFF17251B.toInt() else ThemeUtil.muted(prefs.dark))
            }, LinearLayout.LayoutParams(-1, dp(13)))
            bar.addView(group)
        }
        item("⌨", "Буквы", "keys")
        item("▣", "Буфер", "clips")
        item("▤", "Вики", "wiki")
        item("∑", "Матем.", "math")
        item("⚗", "Химия", "chem")
        item("⚙", "Опции", "settings") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        item(if (prefs.ru) "RU" else "EN", "Язык", "lang") {
            prefs.ru = !prefs.ru
            shifted = false
            pane = "keys"
            render()
        }
        barScroll.addView(bar)
        root.addView(barScroll)
    }

    private fun render() {
        if (!::root.isInitialized) return
        root.removeAllViews()
        root.background = ThemeUtil.shape(this, bg(), ThemeUtil.border(prefs.dark), 16)
        toolbar()
        when (pane) {
            "keys" -> keyboard(root)
            "clips" -> clipboardPanel()
            "wiki" -> wikiPanel()
            "math" -> mathPanel()
            "chem" -> chemistryPanel()
            else -> { pane = "keys"; keyboard(root) }
        }
    }

    private fun keyboard(parent: LinearLayout, wikiTyping: Boolean = false) {
        if (!wikiTyping && prefs.suggestions && !privateField) {
            suggestionStrip = line(parent, 35)
            updateSuggestions()
        } else suggestionStrip = null

        val keys = if (symbols) {
            listOf("@#€%&-+()/", "*\\:;!?\u0027\"[]", "<>^={}\\$~|")
        } else if (prefs.ru) {
            listOf("йцукенгшщзхъ", "фывапролджэ", "ячсмитьбю")
        } else {
            listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        }
        // The permanent number row is deliberately shorter than letter keys.
        line(parent).also { row ->
            for (digit in "1234567890") {
                val key = touchKey(digit.toString(), digit.toString(), wikiTyping)
                key.layoutParams = LinearLayout.LayoutParams(0, dp(31), 1f).apply {
                    val g = dp(prefs.keyGap)
                    setMargins(g, g, g, g)
                }
                key.textSize = 14f
                row.addView(key)
            }
        }
        keys.forEachIndexed { index, letters ->
            val row = line(parent)
            if (index == 2) {
                row.addView(touchKey(if (symbols) "ABC" else "⇧", if (symbols) "SYMBOL" else "SHIFT", wikiTyping, 1.35f))
            }
            for (ch in letters) {
                val code = if (symbols) ch.toString() else {
                    if (shifted || capsLocked) ch.uppercase() else ch.toString()
                }
                val label = if (symbols) ch.toString() else ch.uppercase()
                row.addView(touchKey(label, code, wikiTyping, 1f))
            }
            if (index == 2) row.addView(touchKey("⌫", "BACK", wikiTyping, 1.35f))
        }
        line(parent).also { row ->
            row.addView(touchKey(if (symbols) "ABC" else "?123", "SYMBOL", wikiTyping, 1.35f))
            row.addView(touchKey(",", ",", wikiTyping, .7f))
            row.addView(touchKey("☺", "EMOJI", wikiTyping, .8f))
            row.addView(touchKey(if (prefs.ru) "Русский" else "English", "SPACE", wikiTyping, 4.4f))
            row.addView(touchKey(".", ".", wikiTyping, .7f))
            row.addView(touchKey("↵", "ENTER", wikiTyping, 1.2f))
        }
    }

    private fun touchKey(label: String, code: String, wikiTyping: Boolean, flex: Float = 1f): TextView {
        val key = button(label, flex) {}
        if ((code == "SHIFT" && (shifted || capsLocked)) || code == "ENTER") {
            key.background = ThemeUtil.shape(this, ThemeUtil.ACCENT, ThemeUtil.border(prefs.dark), 9)
            key.setTextColor(0xFF17251B.toInt())
        }
        if (code == "SPACE") key.textSize = 13f
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
        if (code == "SHIFT") {
            val now = SystemClock.uptimeMillis()
            if (now - lastShiftTouch < 420L) {
                capsLocked = !capsLocked
                shifted = capsLocked
            } else {
                shifted = !shifted
                if (capsLocked) capsLocked = false
            }
            lastShiftTouch = now
            render()
            return
        }
        if (code == "SYMBOL") { symbols = !symbols; render(); return }
        if (wikiTyping) {
            wikiQuery = when (code) {
                "BACK" -> wikiQuery.dropLast(1)
                "SPACE" -> wikiQuery + " "
                "ENTER", "EMOJI" -> wikiQuery
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
            else -> {
                currentInputConnection?.commitText(code, 1)
                if (shifted && !capsLocked) { shifted = false; render() }
            }
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
        val previous = currentInputConnection?.getTextBeforeCursor(60, 0)?.toString() ?: ""
        val prefix = Regex("""[\\p{L}]{3,}$""").find(previous)?.value ?: ""
        val results = if (prefix.length >= 3) wiki.suggest(prefix, 4) else emptyList()
        // Only show suggestions grounded in the offline wiki. Never invent a completion.
        val terms = results.filter { it.length <= 24 && it.startsWith(prefix, ignoreCase = true) }.take(3)
        if (terms.isEmpty()) {
            val hint = TextView(this).apply {
                text = if (wiki.isReady()) "KefirBoard  ·  офлайн" else "Индексируем вики Аурелии"
                textSize = 12f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), 0, 0, 0)
                setTextColor(ThemeUtil.muted(prefs.dark))
            }
            bar.addView(hint, LinearLayout.LayoutParams(-1, dp(32)))
        } else {
            for (word in terms) {
                val chip = button(word, 1f) {
                    currentInputConnection?.deleteSurroundingText(prefix.length, 0)
                    currentInputConnection?.commitText(word, 1)
                    updateSuggestions()
                }
                chip.textSize = 12f
                chip.layoutParams = LinearLayout.LayoutParams(0, dp(30), 1f).apply {
                    val g = dp(3); setMargins(g, 0, g, 0)
                }
                bar.addView(chip)
            }
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


    /** Chemistry input is a separate offline panel, not a network function. */
    private fun chemistryPanel() {
        root.addView(headline("⚗ Химия • офлайн"))
        val subheading = TextView(this).apply {
            text = "Формулы, нижние индексы, заряды и уравнивание реакций"
            textSize = 12f
            setTextColor(ThemeUtil.muted(prefs.dark))
            setPadding(dp(8), 0, 0, dp(5))
        }
        root.addView(subheading)

        val exampleScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val examples = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("H₂O", "CO₂", "NaCl", "H₂SO₄", "CH₄", "H₂ + O₂ → H₂O").forEach { sample ->
            val b = TextView(this).apply {
                text = sample
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(14), 0, dp(14), 0)
                setTextColor(fg())
                background = ThemeUtil.shape(this@KefirInputService, keyBg(), ThemeUtil.border(prefs.dark))
                layoutParams = LinearLayout.LayoutParams(-2, dp(36)).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener {
                    chemistryInput = sample
                    chemistryResult = ChemistryEngine.Result("", emptyList())
                    render()
                }
            }
            examples.addView(b)
        }
        exampleScroll.addView(examples)
        root.addView(exampleScroll)
        val input = TextView(this).apply {
            text = chemistryInput.ifBlank { "H₂ + O₂ → H₂O" }
            setTextColor(if (chemistryInput.isEmpty()) ThemeUtil.muted(prefs.dark) else fg())
            textSize = 19f
            minHeight = dp(44)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(5), dp(12), dp(5))
            background = ThemeUtil.shape(this@KefirInputService, ThemeUtil.panel(prefs.dark), ThemeUtil.border(prefs.dark), 12)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(3), dp(7), dp(3), dp(3))
        })
        if (chemistryResult.result.isNotBlank()) {
            root.addView(TextView(this).apply {
                text = chemistryResult.result
                textSize = 16f
                setTextColor(if (chemistryResult.error) 0xFFFF9C8D.toInt() else ThemeUtil.ACCENT)
                setPadding(dp(10), dp(2), dp(10), dp(3))
            })
            chemistryResult.steps.take(2).forEach { step ->
                root.addView(TextView(this).apply {
                    text = "• " + step
                    textSize = 11f
                    setTextColor(ThemeUtil.muted(prefs.dark))
                    setPadding(dp(10), 0, dp(10), 0)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
            }
        }
        val layouts = listOf(
            listOf("H", "C", "N", "O", "Na", "Cl", "Fe", "Ca"),
            listOf("₀", "₁", "₂", "₃", "₄", "₅", "₆", "₇"),
            listOf("₈", "₉", "⁺", "⁻", "²⁺", "→", "⇌", "+"),
            listOf("(", ")", "⌫", "Очистить", "Уравнять", "Вставить")
        )
        layouts.forEach { items ->
            val line = line(root)
            items.forEach { token ->
                val span = when (token) {
                    "Очистить", "Уравнять", "Вставить" -> 1.9f
                    else -> 1f
                }
                val btn = button(token, span) {
                    when (token) {
                        "⌫" -> chemistryInput = chemistryInput.dropLast(1)
                        "Очистить" -> {
                            chemistryInput = ""
                            chemistryResult = ChemistryEngine.Result("", emptyList())
                        }
                        "Уравнять" -> chemistryResult = ChemistryEngine.balance(chemistryInput)
                        "Вставить" -> {
                            val toInsert = if (!chemistryResult.error && chemistryResult.result.isNotBlank())
                                chemistryResult.result else chemistryInput
                            if (toInsert.isNotBlank()) currentInputConnection?.commitText(toInsert, 1)
                        }
                        else -> chemistryInput += token
                    }
                    if (token !in listOf("Уравнять", "Вставить")) {
                        chemistryResult = ChemistryEngine.Result("", emptyList())
                    }
                    render()
                }
                btn.textSize = if (token.length > 4) 11f else 17f
                if (token == "Уравнять") {
                    btn.background = ThemeUtil.shape(this, ThemeUtil.ACCENT)
                    btn.setTextColor(0xFF17251B.toInt())
                }
                line.addView(btn)
            }
        }
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
            listOf("0", ".", "+", "=", "Решить", "C", "Вставить")
        ).forEach { keys ->
            val row = line(root)
            keys.forEach { k ->
                row.addView(button(k, if (k == "Вставить") 2.1f else 1f) {
                    when (k) {
                        "C" -> { mathText = ""; mathResult = MathEngine.Result("", emptyList()) }
                        "⌫" -> mathText = mathText.dropLast(1)
                        "Решить" -> mathResult = MathEngine.solve(mathText)
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
