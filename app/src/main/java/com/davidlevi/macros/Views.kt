package com.davidlevi.macros

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A progress ring with the value in its center. */
class RingView(ctx: Context) : View(ctx) {
    var track = 0
    var color = 0
    var textColor = 0
    var progress = 0f
    var center = ""
    var check = false

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    override fun onMeasure(w: Int, h: Int) {
        val s = context.dpi(84)
        setMeasuredDimension(s, s)
    }

    override fun onDraw(c: Canvas) {
        val sw = context.dp(9f)
        val r = min(width, height) / 2f - sw / 2f
        box.set(width / 2f - r, height / 2f - r, width / 2f + r, height / 2f + r)
        p.style = Paint.Style.STROKE
        p.strokeWidth = sw
        p.strokeCap = Paint.Cap.ROUND
        p.color = track
        c.drawArc(box, 0f, 360f, false, p)
        val pr = progress.coerceIn(0f, 1f)
        if (pr > 0f) {
            p.color = color
            // Counter-clockwise from the top, so the ring fills the RTL way.
            c.drawArc(box, -90f, -360f * pr, false, p)
        }
        p.style = Paint.Style.FILL
        p.color = textColor
        p.textAlign = Paint.Align.CENTER
        p.textSize = context.dp(if (center.length > 4) 15f else 18f)
        p.isFakeBoldText = true
        val y = height / 2f - (p.descent() + p.ascent()) / 2f - (if (check) context.dp(6f) else 0f)
        c.drawText(center, width / 2f, y, p)
        if (check) {
            p.textSize = context.dp(12f)
            c.drawText("✓", width / 2f, y + context.dp(15f), p)
        }
        p.isFakeBoldText = false
    }
}

/**
 * A horizontal bar split into calories from protein, carbs and fat, drawn right to
 * left, with a tick where the calorie goal sits.
 */
class CalorieBarView(ctx: Context) : View(ctx) {
    var parts = doubleArrayOf(0.0, 0.0, 0.0)
    var colors = intArrayOf(0, 0, 0)
    var track = 0
    var goal = 0.0
    var goalColor = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(View.MeasureSpec.getSize(w), context.dpi(18))
    }

    override fun onDraw(c: Canvas) {
        val barH = context.dp(12f)
        val top = (height - barH) / 2f
        val rad = barH / 2f
        val total = parts.sum()
        val scale = max(max(total, goal), 1.0)
        p.style = Paint.Style.FILL
        p.color = track
        box.set(0f, top, width.toFloat(), top + barH)
        c.drawRoundRect(box, rad, rad, p)

        c.save()
        val clip = android.graphics.Path().apply { addRoundRect(box, rad, rad, android.graphics.Path.Direction.CW) }
        c.clipPath(clip)
        var x = width.toFloat()
        for (i in parts.indices) {
            val w = (parts[i] / scale * width).toFloat()
            if (w <= 0f) continue
            p.color = colors[i]
            c.drawRect(x - w, top, x, top + barH, p)
            x -= w
        }
        c.restore()

        if (goal > 0 && total > 0) {
            val gx = (width - goal / scale * width).toFloat()
            p.color = goalColor
            p.strokeWidth = context.dp(2f)
            c.drawLine(gx, 0f, gx, height.toFloat(), p)
        }
    }
}

/** One bar of the history chart. */
class Bucket(
    val label: String,
    /** Stacked segments (one for a single metric, three for calories). */
    val segs: DoubleArray,
    /** Goal lines to draw over this bar (0, 1 or 2 values). */
    val goalLines: DoubleArray,
    /** true/false = met/missed the goal, null = no goal or no data. */
    val met: Boolean?,
    val hasData: Boolean,
    val isCurrent: Boolean,
)

/**
 * Bar chart, oldest bar on the right (RTL), with a dashed goal line per bar so a
 * goal change mid-period shows as a step.
 */
class BarChartView(ctx: Context) : View(ctx) {
    var buckets: List<Bucket> = emptyList()
        set(v) { field = v; selected = -1; invalidate() }
    var strong = intArrayOf(0)
    var light = intArrayOf(0)
    var axisColor = 0
    var textColor = 0
    var goalColor = 0
    var selectColor = 0
    var selected = -1
    var onSelect: ((Int) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = DashPathEffect(floatArrayOf(10f, 7f), 0f)

    private val leftAxis get() = context.dp(34f)
    private val bottomAxis get() = context.dp(20f)
    private val topPad get() = context.dp(8f)

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(View.MeasureSpec.getSize(w), context.dpi(230))
    }

    private fun niceStep(maxV: Double): Double {
        if (maxV <= 0) return 1.0
        val raw = maxV / 4
        val mag = 10.0.pow(floor(log10(raw)))
        val n = raw / mag
        val step = when {
            n <= 1 -> 1.0
            n <= 2 -> 2.0
            n <= 2.5 -> 2.5
            n <= 5 -> 5.0
            else -> 10.0
        }
        return step * mag
    }

    override fun onDraw(c: Canvas) {
        val n = buckets.size
        if (n == 0) return
        var maxV = 0.0
        for (b in buckets) {
            maxV = max(maxV, b.segs.sum())
            for (g in b.goalLines) maxV = max(maxV, g)
        }
        val step = niceStep(maxV * 1.1)
        val top = max(step, ceil(maxV * 1.1 / step) * step)

        val plotL = leftAxis
        val plotR = width.toFloat()
        val plotT = topPad
        val plotB = height - bottomAxis
        val plotH = plotB - plotT
        fun y(v: Double) = (plotB - v / top * plotH).toFloat()

        // grid + y labels
        p.pathEffect = null
        p.textSize = context.dp(11f)
        p.textAlign = Paint.Align.LEFT
        p.strokeWidth = context.dp(0.7f)
        var g = 0.0
        while (g <= top + 1e-9) {
            p.color = axisColor
            c.drawLine(plotL, y(g), plotR, y(g), p)
            p.color = textColor
            c.drawText(fmt(g), 0f, y(g) + context.dp(4f), p)
            g += step
        }

        val slot = (plotR - plotL) / n
        val barW = min(slot * 0.62f, context.dp(30f))
        p.textAlign = Paint.Align.CENTER
        for (i in 0 until n) {
            val b = buckets[i]
            // index 0 is the oldest and sits at the right edge
            val cx = plotR - slot * (i + 0.5f)
            if (i == selected) {
                p.style = Paint.Style.FILL
                p.color = selectColor
                c.drawRect(cx - slot / 2f, plotT, cx + slot / 2f, plotB, p)
            }
            var base = 0.0
            for (s in b.segs.indices) {
                val v = b.segs[s]
                if (v <= 0) continue
                val colorIdx = min(s, strong.size - 1)
                p.style = Paint.Style.FILL
                p.color = if (b.met == false) light[colorIdx] else strong[colorIdx]
                if (b.isCurrent) p.alpha = 150
                c.drawRect(cx - barW / 2f, y(base + v), cx + barW / 2f, y(base), p)
                p.alpha = 255
                base += v
            }
            // goal line over this slot
            p.style = Paint.Style.STROKE
            p.color = goalColor
            p.strokeWidth = context.dp(1.6f)
            p.pathEffect = dash
            for (gv in b.goalLines) {
                if (gv <= 0) continue
                c.drawLine(cx - slot / 2f, y(gv), cx + slot / 2f, y(gv), p)
            }
            p.pathEffect = null
            p.style = Paint.Style.FILL
            if (b.label.isNotEmpty()) {
                p.color = textColor
                p.textSize = context.dp(11f)
                c.drawText(b.label, cx, height - context.dp(5f), p)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (buckets.isEmpty()) return false
        if (e.action == MotionEvent.ACTION_DOWN) return true
        if (e.action == MotionEvent.ACTION_UP) {
            val slot = (width - leftAxis) / buckets.size
            val i = ((width - e.x) / slot).toInt()
            if (i in buckets.indices && e.x >= leftAxis) {
                selected = i
                invalidate()
                onSelect?.invoke(i)
            }
            return true
        }
        return super.onTouchEvent(e)
    }
}
