package com.davidlevi.macros

import android.app.AlertDialog
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The daily screen: three rings, the calorie split bar and the day's entries. */
class TodayScreen(private val a: MainActivity) {
    var date: LocalDate = LocalDate.now()
    private var lastSeenToday: LocalDate = LocalDate.now()

    private val pal get() = a.pal
    val view: View
    private val title: TextView
    private val prevBtn: TextView
    private val nextBtn: TextView
    private val rings = HashMap<Metric, RingView>()
    private val ringSubs = HashMap<Metric, TextView>()
    private val kcalValue: TextView
    private val kcalBar: CalorieBarView
    private val kcalLegend = arrayOfNulls<TextView>(3)
    private val list: LinearLayout

    init {
        val frame = FrameLayout(a)
        val scroll = ScrollView(a)
        val col = a.vbox().apply { setPadding(a.dpi(16), a.dpi(12), a.dpi(16), a.dpi(96)) }
        scroll.addView(col)
        frame.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))

        // Header: in RTL the right arrow goes back a day, the left one forward.
        val head = a.hbox()
        prevBtn = arrow("›") { date = date.minusDays(1); refresh() }
        nextBtn = arrow("‹") { if (date < LocalDate.now()) { date = date.plusDays(1); refresh() } }
        title = a.label("", 18f, pal.text, bold = true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { date = LocalDate.now(); refresh() }
        }
        head.addView(prevBtn, lp(a.dpi(48), a.dpi(48)))
        head.addView(title, lp(0, WRAP, 1f))
        head.addView(nextBtn, lp(a.dpi(48), a.dpi(48)))
        col.addView(head, lp(MATCH, WRAP))

        // Rings
        val ringRow = a.hbox().apply { gravity = Gravity.CENTER }
        for (m in listOf(Metric.PROTEIN, Metric.CARBS, Metric.FAT)) {
            val cell = a.vbox().apply { gravity = Gravity.CENTER_HORIZONTAL }
            val ring = RingView(a)
            rings[m] = ring
            cell.addView(ring, lp(WRAP, WRAP))
            cell.addView(a.label(m.label, 15f, pal.text, bold = true).apply { gravity = Gravity.CENTER },
                lp(WRAP, WRAP).apply { topMargin = a.dpi(6) })
            val sub = a.label("", 12f, pal.text2).apply { gravity = Gravity.CENTER }
            ringSubs[m] = sub
            cell.addView(sub, lp(WRAP, WRAP))
            ringRow.addView(cell, lp(0, WRAP, 1f))
        }
        col.addView(ringRow, lp(MATCH, WRAP).apply { topMargin = a.dpi(8) })

        // Calories card
        val card = a.card()
        val kRow = a.hbox()
        kRow.addView(a.label("קלוריות", 15f, pal.text, bold = true), lp(0, WRAP, 1f))
        kcalValue = a.label("", 15f, pal.text)
        kRow.addView(kcalValue, lp(WRAP, WRAP))
        card.addView(kRow, lp(MATCH, WRAP))
        kcalBar = CalorieBarView(a)
        card.addView(kcalBar, lp(MATCH, WRAP).apply { topMargin = a.dpi(10); bottomMargin = a.dpi(8) })
        val legend = a.hbox()
        val legendMetrics = listOf(Metric.PROTEIN, Metric.CARBS, Metric.FAT)
        for ((i, m) in legendMetrics.withIndex()) {
            val item = a.hbox()
            item.addView(View(a).apply { background = roundRect(pal.strong(m), a.dp(5f)) },
                lp(a.dpi(10), a.dpi(10)).apply { marginEnd = a.dpi(5) })
            val t = a.label("", 12f, pal.text2)
            kcalLegend[i] = t
            item.addView(t, lp(WRAP, WRAP))
            legend.addView(item, lp(0, WRAP, 1f))
        }
        card.addView(legend, lp(MATCH, WRAP))
        col.addView(card, lp(MATCH, WRAP).apply { topMargin = a.dpi(18) })

        col.addView(a.label("הוספות", 13f, pal.text2), lp(MATCH, WRAP).apply { topMargin = a.dpi(18); bottomMargin = a.dpi(4) })
        list = a.vbox()
        col.addView(list, lp(MATCH, WRAP))

        // Floating add button
        val add = a.label("+  הוסף", 17f, 0xFFFFFFFF.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            background = roundRect(pal.accentFill, a.dp(28f))
            setPadding(a.dpi(32), a.dpi(14), a.dpi(32), a.dpi(14))
            elevation = a.dp(4f)
            setOnClickListener { editDialog(null) }
        }
        frame.addView(add, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = a.dpi(18)
        })
        view = frame
    }

    private fun arrow(s: String, onClick: () -> Unit) = a.label(s, 28f, pal.text2).apply {
        gravity = Gravity.CENTER
        setOnClickListener { onClick() }
    }

    fun refresh() {
        // If the app was left open on "today" overnight, follow the calendar.
        val now = LocalDate.now()
        if (date == lastSeenToday && now != lastSeenToday) date = now
        lastSeenToday = now
        if (date > now) date = now

        title.text = if (date == now) "היום, ${dayTitle(date)}" else dayTitle(date)
        nextBtn.alpha = if (date < now) 1f else 0.25f

        val goals = a.store.goalsOn(date)
        val tot = a.store.totalsOn(date)
        for ((m, ring) in rings) {
            val g = goals.of(m)
            val v = tot.of(m)
            val over = g.isOver(v)
            ring.track = pal.light(m)
            ring.color = if (over) pal.danger else pal.strong(m)
            ring.textColor = if (over) pal.danger else pal.text
            ring.progress = if (g.target > 0) (v / g.target).toFloat() else 0f
            ring.center = fmt(v)
            // A check mark only for goals you have to reach, not for staying under a cap.
            ring.check = (g.mode == GoalMode.MIN || g.mode == GoalMode.RANGE) && g.isMet(v) == true
            ring.invalidate()
            ringSubs[m]!!.apply {
                text = g.describe()
                setTextColor(if (over) pal.danger else pal.text2)
            }
        }

        val kg = goals.of(Metric.CALORIES)
        val kcal = tot.calories
        val goalText = if (kg.mode == GoalMode.NONE) "" else "  /  " + when (kg.mode) {
            GoalMode.MIN -> "${fmt(kg.min)} מינ׳"
            GoalMode.MAX -> fmt(kg.max)
            GoalMode.RANGE -> "${fmt(kg.min)}–${fmt(kg.max)}"
            GoalMode.NONE -> ""
        }
        kcalValue.text = fmt(kcal) + goalText
        kcalValue.setTextColor(if (kg.isOver(kcal)) pal.danger else pal.text)
        kcalBar.parts = doubleArrayOf(tot.kcalProtein, tot.kcalCarbs, tot.kcalFat)
        kcalBar.colors = intArrayOf(pal.strong(Metric.PROTEIN), pal.strong(Metric.CARBS), pal.strong(Metric.FAT))
        kcalBar.track = pal.line
        kcalBar.goal = kg.target
        kcalBar.goalColor = pal.text
        kcalBar.invalidate()
        val parts = listOf(tot.kcalProtein, tot.kcalCarbs, tot.kcalFat)
        val names = listOf("חלבון", "פחמימות", "שומן")
        for (i in 0..2) {
            val pct = if (kcal > 0) " (${Math.round(parts[i] / kcal * 100)}%)" else ""
            kcalLegend[i]!!.text = "${names[i]} ${fmt(parts[i])}$pct"
        }

        list.removeAllViews()
        val entries = a.store.entriesOn(date)
        if (entries.isEmpty()) {
            list.addView(a.label("עדיין לא הוזן כלום. לחיצה על ״הוסף״ מוסיפה כמויות.", 14f, pal.muted),
                lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })
        }
        for (e in entries) {
            list.addView(View(a).apply { setBackgroundColor(pal.line) }, lp(MATCH, a.dpi(1)))
            val row = a.hbox().apply {
                setPadding(0, a.dpi(12), 0, a.dpi(12))
                setOnClickListener { editDialog(e) }
            }
            row.addView(a.label(e.time, 14f, pal.text2), lp(a.dpi(56), WRAP))
            row.addView(a.label("ח ${fmt(e.protein)} · פ ${fmt(e.carbs)} · ש ${fmt(e.fat)}", 15f, pal.text), lp(0, WRAP, 1f))
            val k = e.protein * KCAL_PROTEIN + e.carbs * KCAL_CARBS + e.fat * KCAL_FAT
            row.addView(a.label("${fmt(k)} קק״ל", 13f, pal.muted), lp(WRAP, WRAP))
            list.addView(row, lp(MATCH, WRAP))
        }
        if (entries.isNotEmpty()) {
            list.addView(View(a).apply { setBackgroundColor(pal.line) }, lp(MATCH, a.dpi(1)))
            list.addView(a.label("לחיצה על שורה לעריכה או מחיקה", 12f, pal.muted), lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })
        }
    }

    /** Add (entry == null) or edit an entry. */
    private fun editDialog(entry: Entry?) {
        val box = a.vbox().apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setPadding(a.dpi(24), a.dpi(8), a.dpi(24), 0)
        }
        fun field(m: Metric, initial: Double?): EditText {
            val row = a.hbox()
            row.addView(View(a).apply { background = roundRect(pal.strong(m), a.dp(6f)) },
                lp(a.dpi(12), a.dpi(12)).apply { marginEnd = a.dpi(10) })
            row.addView(a.label(m.label, 16f, pal.text), lp(a.dpi(90), WRAP))
            val et = EditText(a).apply {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                hint = "0"
                textSize = 18f
                gravity = Gravity.CENTER
                if (initial != null && initial > 0) setText(fmt(initial).replace(",", ""))
            }
            row.addView(et, lp(0, WRAP, 1f))
            row.addView(a.label("ג׳", 14f, pal.text2), lp(WRAP, WRAP).apply { marginStart = a.dpi(6) })
            box.addView(row, lp(MATCH, WRAP))
            return et
        }
        val pE = field(Metric.PROTEIN, entry?.protein)
        val cE = field(Metric.CARBS, entry?.carbs)
        val fE = field(Metric.FAT, entry?.fat)
        val preview = a.label("", 14f, pal.text2)
        box.addView(preview, lp(MATCH, WRAP).apply { topMargin = a.dpi(10) })

        fun update() {
            val p = parseNum(pE.text.toString())
            val c = parseNum(cE.text.toString())
            val f = parseNum(fE.text.toString())
            preview.text = if (p == null || c == null || f == null) "מספר לא תקין"
            else "= ${fmt(p * KCAL_PROTEIN + c * KCAL_CARBS + f * KCAL_FAT)} קק״ל"
        }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = update()
        }
        pE.addTextChangedListener(watcher)
        cE.addTextChangedListener(watcher)
        fE.addTextChangedListener(watcher)
        update()

        val titleText = if (entry == null) {
            if (date == LocalDate.now()) "הוספה" else "הוספה ל${dayTitle(date)}"
        } else "עריכה (${entry.time})"

        val b = AlertDialog.Builder(a)
            .setTitle(titleText)
            .setView(box)
            .setPositiveButton("שמירה", null)
            .setNegativeButton("ביטול", null)
        if (entry != null) b.setNeutralButton("מחיקה") { _, _ ->
            AlertDialog.Builder(a)
                .setMessage("למחוק את ההוספה?")
                .setPositiveButton("מחיקה") { _, _ -> a.store.deleteEntry(entry.id); refresh() }
                .setNegativeButton("ביטול", null)
                .show()
        }
        val d = b.create()
        d.window?.decorView?.layoutDirection = View.LAYOUT_DIRECTION_RTL
        d.setOnShowListener {
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val p = parseNum(pE.text.toString())
                val c = parseNum(cE.text.toString())
                val f = parseNum(fE.text.toString())
                if (p == null || c == null || f == null) return@setOnClickListener
                if (p + c + f == 0.0) { d.dismiss(); return@setOnClickListener }
                if (entry == null) {
                    val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
                    a.store.addEntry(date, time, p, c, f)
                } else {
                    a.store.updateEntry(entry.copy(protein = p, carbs = c, fat = f))
                }
                d.dismiss()
                refresh()
            }
            pE.requestFocus()
            d.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }
        d.show()
    }
}
