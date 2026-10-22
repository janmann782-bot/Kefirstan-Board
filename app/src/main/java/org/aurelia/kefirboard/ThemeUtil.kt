package org.aurelia.kefirboard
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.util.TypedValue

object ThemeUtil {
    val ACCENT: Int = 0xFFA9F38F.toInt()
    fun dp(context: Context, value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()
    fun background(dark: Boolean) = if (dark) Color.rgb(13, 17, 16) else Color.rgb(248, 250, 249)
    fun panel(dark: Boolean) = if (dark) Color.rgb(26, 33, 32) else Color.rgb(233, 237, 237)
    fun border(dark: Boolean) = if (dark) Color.rgb(55, 72, 68) else Color.rgb(201, 212, 209)
    fun key(dark: Boolean) = if (dark) Color.rgb(40, 47, 46) else Color.rgb(244, 247, 246)
    fun foreground(dark: Boolean) = if (dark) Color.WHITE else Color.rgb(26, 31, 30)
    fun muted(dark: Boolean) = if (dark) Color.rgb(168, 179, 176) else Color.rgb(89, 102, 98)
    fun keyState(context: Context, dark: Boolean): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed),
                shape(context, ACCENT, border(dark), 9))
            addState(intArrayOf(), shape(context, key(dark), border(dark), 9))
        }

    fun shape(context: Context, fill: Int, border: Int = Color.rgb(55, 72, 68), radius: Int = 9): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = dp(context, radius).toFloat()
            setStroke(dp(context, 1).coerceAtLeast(1), border)
        }
}
