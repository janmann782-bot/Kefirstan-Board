package org.aurelia.kefirboard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Settings and a live native mockup using the shared KefirBoard palette. */
class PreviewDashboard : Activity() {
    private val prefs by lazy { Prefs(this) }
    private var previewRu = true
    private var previewMode = "keys"
    private fun dp(n: Int) = ThemeUtil.dp(this, n)
    private fun bg() = ThemeUtil.background(prefs.dark)
    private fun panel() = ThemeUtil.panel(prefs.dark)
    private fun ink() = ThemeUtil.foreground(prefs.dark)
    private fun muted() = ThemeUtil.muted(prefs.dark)
    private fun tile() = ThemeUtil.key(prefs.dark)
    private fun shape(color: Int, radius: Int = 13) = ThemeUtil.shape(this, color, ThemeUtil.border(prefs.dark), radius)
    private fun text(value: String, size: Float = 14f, color: Int = ink(), bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }
    private fun stack(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun padded(view: View, pad: Int) { view.setPadding(dp(pad), dp(pad), dp(pad), dp(pad)) }
    private fun spaced(parent: LinearLayout, child: View, top: Int = 0) {
        parent.addView(child, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top) })
    }
    private fun pill(label: String, active: Boolean = false, click: () -> Unit): TextView =
        text(label, 12f, if (active) Color.rgb(23, 38, 27) else ink(), active).apply {
            gravity = Gravity.CENTER
            background = shape(if (active) ThemeUtil.ACCENT else tile(), 12)
            setPadding(dp(13), dp(9), dp(13), dp(9))
            setOnClickListener { click() }
        }
    private fun menuCard(title: String, detail: String, symbol: String, parent: LinearLayout, click: () -> Unit) {
        val card = row().apply {
            background = shape(panel(), 17)
            setPadding(dp(14), dp(17), dp(14), dp(17))
            setOnClickListener { click() }
        }
        card.addView(text(symbol, 28f, ThemeUtil.ACCENT), LinearLayout.LayoutParams(dp(48), -2))
        val content = stack()
        content.addView(text(title, 16f, ink(), true))
        content.addView(text("● Офлайн", 12f, ThemeUtil.ACCENT))
        content.addView(text(detail, 12f, muted()))
        card.addView(content, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(text("›", 26f, muted()))
        spaced(parent, card, 9)
    }
    private fun previewKey(value: String, weight: Float = 1f, accent: Boolean = false): TextView =
        text(value, 15f, if (accent) Color.rgb(18, 40, 20) else ink(), accent).apply {
            gravity = Gravity.CENTER
            minHeight = dp(39)
            background = shape(if (accent) ThemeUtil.ACCENT else tile(), 8)
            layoutParams = LinearLayout.LayoutParams(0, dp(39), weight).apply {
                val gap = dp(2); setMargins(gap, gap, gap, gap)
            }
        }
    private fun keyboardPreview(parent: LinearLayout) {
        val wrapper = stack().apply {
            background = shape(panel(), 17)
            setPadding(dp(7), dp(10), dp(7), dp(12))
        }
        val suggestions = row()
        for (word in listOf("привет", "приветик", "приветствую")) {
            val chip = pill(word, word == "привет") {}
            suggestions.addView(chip, LinearLayout.LayoutParams(0, dp(34), 1f).apply { setMargins(dp(2), 0, dp(2), 0) })
        }
        wrapper.addView(suggestions)
        val digits = row()
        for (number in "1234567890") digits.addView(previewKey(number.toString()))
        wrapper.addView(digits)
        val rows = if (previewRu) listOf("йцукенгшщзх", "фывапролджэ", "ячсмитьбю")
            else listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        for ((i, letters) in rows.withIndex()) {
            val r = row()
            if (i == 2) r.addView(previewKey("⇧", 1.2f, true))
            for (key in letters) r.addView(previewKey(key.toString().uppercase(), 1f, key == 'к' && previewRu))
            if (i == 2) r.addView(previewKey("⌫", 1.2f))
            wrapper.addView(r)
        }
        val bottom = row()
        bottom.addView(previewKey("?123", 1.4f))
        bottom.addView(previewKey(",", .65f))
        bottom.addView(previewKey("☺", .8f))
        val space = previewKey(if (previewRu) "Русский ▾" else "English ▾", 4f)
        space.setOnClickListener { previewRu = !previewRu; recreate() }
        bottom.addView(space)
        bottom.addView(previewKey(".", .65f))
        bottom.addView(previewKey("↵", 1.2f, true))
        wrapper.addView(bottom)
        spaced(parent, wrapper, 10)
    }
    private fun chemistryPreview(parent: LinearLayout) {
        val wrapper = stack().apply { background = shape(panel(), 15); padded(this, 12) }
        wrapper.addView(text("⚗  Химический режим", 17f, ThemeUtil.ACCENT, true))
        val formulas = row()
        for (symbol in listOf("H₂O", "CO₂", "NaCl", "H₂SO₄")) {
            formulas.addView(pill(symbol) {}, LinearLayout.LayoutParams(0, dp(36), 1f).apply { setMargins(dp(2), dp(6), dp(2), 0) })
        }
        wrapper.addView(formulas)
        spaced(wrapper, text("2H₂ + O₂ → 2H₂O", 20f, ink(), true), 10)
        spaced(wrapper, text("Индексы • заряды • формулы • баланс реакций", 12f, muted()), 8)
        spaced(parent, wrapper, 10)
    }
    private fun controls(parent: LinearLayout) {
        spaced(parent, text("Установка и настройки", 19f, ink(), true), 18)
        fun control(label: String, action: () -> Unit) {
            spaced(parent, pill(label, false, action), 6)
        }
        control("1. Включить KefirBoard в Android") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        control("2. Выбрать KefirBoard") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        control("Тема: " + if (prefs.dark) "темная" else "светлая") {
            prefs.dark = !prefs.dark; recreate()
        }
        control("Высота клавиш: " + prefs.keyHeight + " dp  (+4)") {
            prefs.keyHeight = if (prefs.keyHeight >= 68) 34 else prefs.keyHeight + 4; recreate()
        }
        control("Межклавишный зазор: " + prefs.keyGap + " dp  (+1)") {
            prefs.keyGap = if (prefs.keyGap >= 8) 0 else prefs.keyGap + 1; recreate()
        }
        control("Подсказки: " + if (prefs.suggestions) "включены" else "выключены") {
            prefs.suggestions = !prefs.suggestions; recreate()
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg()
        window.navigationBarColor = bg()
        val outer = stack().apply {
            setBackgroundColor(bg())
            setPadding(dp(15), dp(18), dp(15), dp(22))
        }
        val hero = row()
        hero.addView(text("Kefir", 34f, ink(), true))
        hero.addView(text("Board", 34f, ThemeUtil.ACCENT, true))
        outer.addView(hero)
        spaced(outer, text("Больше, чем клавиатура", 16f, ink(), true), 4)
        spaced(outer, text("Офлайн-знания. Инструменты. Везде с вами.", 13f, muted()), 3)
        menuCard("Aurelia Wiki", "Статьи и подсказки без сети", "▤", outer) { previewMode = "wiki"; recreate() }
        menuCard("Калькулятор", "Формулы и базовые уравнения", "∑", outer) { previewMode = "math"; recreate() }
        menuCard("Химия", "Формулы, заряды, баланс реакций", "⚗", outer) { previewMode = "chem"; recreate() }
        spaced(outer, text("Панель инструментов", 14f, ink(), true), 16)
        val menu = row()
        val tabs = listOf("⌨" to "keys", "▣" to "clips", "▤" to "wiki", "∑" to "math", "⚗" to "chem")
        for ((icon, mode) in tabs) {
            menu.addView(pill(icon, previewMode == mode) { previewMode = mode; recreate() },
                LinearLayout.LayoutParams(0, dp(42), 1f).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        outer.addView(menu)
        when (previewMode) {
            "chem" -> chemistryPreview(outer)
            "math" -> spaced(outer, text("2x + 4 = 12     →     x = 4", 20f, ThemeUtil.ACCENT, true), 15)
            "wiki" -> spaced(outer, text("Aurelia Wiki: локальная энциклопедия", 16f, ink()), 15)
            "clips" -> spaced(outer, text("История буфера хранится только на устройстве", 14f, ink()), 15)
            else -> keyboardPreview(outer)
        }
        controls(outer)
        spaced(outer, text("Версия 0.2.0. Все сетевые разрешения отключены. Не все функции из технического плана уже реализованы.", 12f, muted()), 17)
        val scroller = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(bg())
            addView(outer)
        }
        setContentView(scroller)
    }
}
