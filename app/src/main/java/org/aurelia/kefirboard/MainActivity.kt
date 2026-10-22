package org.aurelia.kefirboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { Prefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF24282E.toInt())
        }
        val title = TextView(this).apply {
            text = "KefirBoard"
            textSize = 30f
            setTextColor(0xFFA9F38F.toInt())
        }
        root.addView(title)
        val subtitle = TextView(this).apply {
            text = "Локальная Android-клавиатура. Интернет не требуется.\nВстроена офлайн-энциклопедия Аурелии."
            textSize = 16f
            setTextColor(0xFFE5E5E5.toInt())
            setPadding(0, 10, 0, 20)
        }
        root.addView(subtitle)
        fun action(label: String, handler: () -> Unit) {
            val button = Button(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { handler() }
            }
            root.addView(button)
        }
        action("1. Включить KefirBoard в настройках Android") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        action("2. Выбрать KefirBoard для ввода") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        action("Тема: переключить темную / светлую") {
            prefs.dark = !prefs.dark
            Toast.makeText(this, if (prefs.dark) "Темная тема" else "Светлая тема", Toast.LENGTH_SHORT).show()
        }
        action("Высота клавиш: ${prefs.keyHeight} dp (+4)") {
            prefs.keyHeight = if (prefs.keyHeight >= 68) 34 else prefs.keyHeight + 4
            recreate()
        }
        action("Расстояние между клавишами: ${prefs.keyGap} dp (+1)") {
            prefs.keyGap = if (prefs.keyGap >= 8) 0 else prefs.keyGap + 1
            recreate()
        }
        action("Подсказки: ${if (prefs.suggestions) "вкл." else "выкл."}") {
            prefs.suggestions = !prefs.suggestions
            recreate()
        }
        val note = TextView(this).apply {
            text = "\nБаза вики индексируется в фоне при первом запуске клавиатуры. Данные, буфер и ввод не отправляются в интернет.\n\nВажно: голосовой ввод, полный переводчик, GIF, интегралы, построение графиков, свайповый ввод слов и редактор раскладки еще в разработке."
            textSize = 14f
            setTextColor(0xFFCAD0D6.toInt())
        }
        root.addView(note)
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
