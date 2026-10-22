package org.aurelia.kefirboard

import android.content.Context

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("kefirboard", Context.MODE_PRIVATE)
    var dark: Boolean
        get() = prefs.getBoolean("dark", true)
        set(value) { prefs.edit().putBoolean("dark", value).apply() }
    var ru: Boolean
        get() = prefs.getBoolean("ru", true)
        set(value) { prefs.edit().putBoolean("ru", value).apply() }
    var keyHeight: Int
        get() = prefs.getInt("key_height", 44).coerceIn(34, 68)
        set(value) { prefs.edit().putInt("key_height", value.coerceIn(34, 68)).apply() }
    var keyGap: Int
        get() = prefs.getInt("key_gap", 2).coerceIn(0, 8)
        set(value) { prefs.edit().putInt("key_gap", value.coerceIn(0, 8)).apply() }
    var suggestions: Boolean
        get() = prefs.getBoolean("suggestions", true)
        set(value) { prefs.edit().putBoolean("suggestions", value).apply() }
}
