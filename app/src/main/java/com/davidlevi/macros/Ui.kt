package com.davidlevi.macros

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.DayOfWeek
import java.time.LocalDate

/** Screen colors for light and dark mode. Metric colors keep their hue in both. */
class Palette(val dark: Boolean) {
    val bg = if (dark) 0xFF1A1A19.toInt() else 0xFFF7F6F2.toInt()
    val card = if (dark) 0xFF262624.toInt() else 0xFFFFFFFF.toInt()
    val text = if (dark) 0xFFF1EFE8.toInt() else 0xFF2C2C2A.toInt()
    val text2 = if (dark) 0xFFB4B2A9.toInt() else 0xFF5F5E5A.toInt()
    val muted = if (dark) 0xFF888780.toInt() else 0xFF888780.toInt()
    val line = if (dark) 0x33FFFFFF else 0x1F000000
    val accent = if (dark) 0xFF85B7EB.toInt() else 0xFF185FA5.toInt()
    val accentFill = 0xFF378ADD.toInt()
    val danger = if (dark) 0xFFF09595.toInt() else 0xFFA32D2D.toInt()
    val success = if (dark) 0xFF97C459.toInt() else 0xFF3B6D11.toInt()

    /** Strong color of a metric (rings, bars that met the goal). */
    fun strong(m: Metric): Int = when (m) {
        Metric.PROTEIN -> if (dark) 0xFF7F77DD.toInt() else 0xFF534AB7.toInt()
        Metric.CARBS -> if (dark) 0xFFEF9F27.toInt() else 0xFFBA7517.toInt()
        Metric.FAT -> if (dark) 0xFFF0997B.toInt() else 0xFFD85A30.toInt()
        Metric.CALORIES -> if (dark) 0xFF5DCAA5.toInt() else 0xFF0F6E56.toInt()
    }

    /** Light color of a metric (ring track, bars that missed the goal). */
    fun light(m: Metric): Int = when (m) {
        Metric.PROTEIN -> if (dark) 0xFF3C3489.toInt() else 0xFFCECBF6.toInt()
        Metric.CARBS -> if (dark) 0xFF633806.toInt() else 0xFFFAC775.toInt()
        Metric.FAT -> if (dark) 0xFF712B13.toInt() else 0xFFF5C4B3.toInt()
        Metric.CALORIES -> if (dark) 0xFF085041.toInt() else 0xFF9FE1CB.toInt()
    }

    companion object {
        fun of(ctx: Context) = Palette(
            (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        )
    }
}

fun Context.dp(v: Float): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

fun Context.dpi(v: Int): Int = dp(v.toFloat()).toInt()

fun Context.label(text: String, sizeSp: Float = 15f, color: Int, bold: Boolean = false): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

fun roundRect(color: Int, radiusPx: Float, strokeColor: Int = Color.TRANSPARENT, strokePx: Int = 0) =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx
        if (strokePx > 0) setStroke(strokePx, strokeColor)
    }

fun Context.vbox(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.hbox(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT

fun View.margins(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
    (layoutParams as? LinearLayout.LayoutParams)?.setMargins(l, t, r, b)
}

/** "א".."ש" for Sunday..Saturday. */
fun dayLetter(d: LocalDate): String = when (d.dayOfWeek) {
    DayOfWeek.SUNDAY -> "א"
    DayOfWeek.MONDAY -> "ב"
    DayOfWeek.TUESDAY -> "ג"
    DayOfWeek.WEDNESDAY -> "ד"
    DayOfWeek.THURSDAY -> "ה"
    DayOfWeek.FRIDAY -> "ו"
    DayOfWeek.SATURDAY -> "ש"
    else -> ""
}

/** "יום ד׳ 11.11". */
fun dayTitle(d: LocalDate): String = "יום ${dayLetter(d)}׳ ${shortDate(d)}"

fun shortDate(d: LocalDate): String = "${d.dayOfMonth}.${d.monthValue}"

val MONTHS_HE = arrayOf(
    "ינואר", "פברואר", "מרץ", "אפריל", "מאי", "יוני",
    "יולי", "אוגוסט", "ספטמבר", "אוקטובר", "נובמבר", "דצמבר"
)
