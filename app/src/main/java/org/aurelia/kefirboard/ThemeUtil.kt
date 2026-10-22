package org.aurelia.kefirboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

object ThemeUtil {
    val ACCENT: Int = 0xFFA9F38F.toInt()
    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
    fun background(dark: Boolean) = if (dark) Color.rgb(36, 40, 46) else Color.rgb(230, 233, 237)
    fun key(dark: Boolean) = if (dark) Color.rgb(57, 62, 70) else Color.WHITE
    fun foreground(dark: Boolean) = if (dark) Color.WHITE else Color.rgb(30, 35, 41)
    fun muted(dark: Boolean) = if (dark) Color.rgb(188, 194, 201) else Color.rgb(75, 85, 97)
    fun shape(context: Context, fill: Int, border: Int = 0xFF676D76.toInt(), radius: Int = 9): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radius).toFloat()
            setStroke(dp(context, 1).coerceAtLeast(1), border)
        }
}
