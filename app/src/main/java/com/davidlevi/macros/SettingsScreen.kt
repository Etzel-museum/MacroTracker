package com.davidlevi.macros

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

/** Goals (per metric: none / min / max / range) and backup. */
class SettingsScreen(private val a: MainActivity) {
    private val pal get() = a.pal
    val view: View
    private val welcome: TextView
    private val rows = HashMap<Metric, GoalRow>()
    private val suggestion: TextView

    private inner class GoalRow(val m: Metric) {
        var mode = GoalMode.NONE
        val modeBtns = HashMap<GoalMode, TextView>()
        val minBox: LinearLayout
        val maxBox: LinearLayout
        val minE: EditText
        val maxE: EditText
        val root: LinearLayout = a.card()

        init {
            val head = a.hbox()
            head.addView(View(a).apply { background = roundRect(pal.strong(m), a.dp(6f)) },
                lp(a.dpi(12), a.dpi(12)).apply { marginEnd = a.dpi(8) })
            head.addView(a.label(m.label, 17f, pal.text, bold = true), lp(0, WRAP, 1f))
            head.addView(a.label(m.unit, 13f, pal.text2), lp(WRAP, WRAP))
            root.addView(head, lp(MATCH, WRAP))

            val seg = a.hbox().apply {
                background = roundRect(pal.line, a.dp(10f))
                setPadding(a.dpi(3), a.dpi(3), a.dpi(3), a.dpi(3))
            }
            for (gm in GoalMode.values()) {
                val t = a.label(gm.label, 13f, pal.text2).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, a.dpi(7), 0, a.dpi(7))
                    setOnClickListener { mode = gm; paint() }
                }
                modeBtns[gm] = t
                seg.addView(t, lp(0, WRAP, 1f))
            }
            root.addView(seg, lp(MATCH, WRAP).apply { topMargin = a.dpi(10) })

            val vals = a.hbox()
            fun numBox(caption: String): Pair<LinearLayout, EditText> {
                val b = a.hbox()
                b.addView(a.label(caption, 14f, pal.text2), lp(WRAP, WRAP).apply { marginEnd = a.dpi(8) })
                val e = EditText(a).apply {
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    gravity = Gravity.CENTER
                    textSize = 17f
                    setTextColor(pal.text)
                }
                b.addView(e, lp(0, WRAP, 1f))
                return b to e
            }
            val (mnB, mnE) = numBox("לפחות")
            val (mxB, mxE) = numBox("לכל היותר")
            minBox = mnB; minE = mnE; maxBox = mxB; maxE = mxE
            vals.addView(minBox, lp(0, WRAP, 1f))
            vals.addView(maxBox, lp(0, WRAP, 1f).apply { marginStart = a.dpi(12) })
            root.addView(vals, lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })
        }

        fun load(g: Goal) {
            mode = g.mode
            minE.setText(if (g.min > 0) fmt(g.min).replace(",", "") else "")
            maxE.setText(if (g.max > 0) fmt(g.max).replace(",", "") else "")
            paint()
        }

        fun paint() {
            for ((gm, t) in modeBtns) {
                val on = gm == mode
                t.background = if (on) roundRect(pal.card, a.dp(8f)) else null
                t.setTextColor(if (on) pal.text else pal.text2)
            }
            minBox.visibility = if (mode == GoalMode.MIN || mode == GoalMode.RANGE) View.VISIBLE else View.GONE
            maxBox.visibility = if (mode == GoalMode.MAX || mode == GoalMode.RANGE) View.VISIBLE else View.GONE
        }

        /** null = invalid input (an error toast was shown). */
        fun read(): Goal? {
            val mn = parseNum(minE.text.toString())
            val mx = parseNum(maxE.text.toString())
            fun bad(msg: String): Goal? {
                Toast.makeText(a, "${m.label}: $msg", Toast.LENGTH_LONG).show(); return null
            }
            return when (mode) {
                GoalMode.NONE -> Goal(GoalMode.NONE)
                GoalMode.MIN -> if (mn == null || mn <= 0) bad("צריך מספר מינימום") else Goal(GoalMode.MIN, min = mn)
                GoalMode.MAX -> if (mx == null || mx <= 0) bad("צריך מספר מקסימום") else Goal(GoalMode.MAX, max = mx)
                GoalMode.RANGE ->
                    if (mn == null || mx == null || mn <= 0 || mx <= 0) bad("צריך מינימום ומקסימום")
                    else if (mn > mx) bad("המינימום גדול מהמקסימום")
                    else Goal(GoalMode.RANGE, mn, mx)
            }
        }
    }

    init {
        val scroll = ScrollView(a)
        val col = a.vbox().apply { setPadding(a.dpi(16), a.dpi(14), a.dpi(16), a.dpi(32)) }
        scroll.addView(col)
        col.addView(a.label("יעדים יומיים", 20f, pal.text, bold = true), lp(MATCH, WRAP))
        welcome = a.label(
            "כאן קובעים יעד לכל מדד. למשל חלבון – מינימום שרוצים להגיע אליו, " +
                "קלוריות – מקסימום שלא רוצים לעבור. אפשר לשנות בכל זמן.",
            14f, pal.text2
        )
        col.addView(welcome, lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })

        for (m in Metric.values()) {
            val r = GoalRow(m)
            rows[m] = r
            col.addView(r.root, lp(MATCH, WRAP).apply { topMargin = a.dpi(12) })
        }

        suggestion = a.label("", 13f, pal.text2)
        col.addView(suggestion, lp(MATCH, WRAP).apply { topMargin = a.dpi(10) })

        val save = a.label("שמירת יעדים", 16f, 0xFFFFFFFF.toInt(), bold = true).apply {
            gravity = Gravity.CENTER
            background = roundRect(pal.accentFill, a.dp(12f))
            setPadding(0, a.dpi(14), 0, a.dpi(14))
            setOnClickListener { save() }
        }
        col.addView(save, lp(MATCH, WRAP).apply { topMargin = a.dpi(14) })
        col.addView(a.label("יעד חדש חל מהיום והלאה. ימים קודמים נמדדים מול היעד שהיה בתוקף אז.", 12f, pal.muted),
            lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })

        col.addView(a.label("גיבוי", 20f, pal.text, bold = true), lp(MATCH, WRAP).apply { topMargin = a.dpi(28) })
        col.addView(a.label("הנתונים שמורים רק בטלפון. כדאי לייצא גיבוי מדי פעם (למשל ל-Google Drive), " +
            "ובטלפון חדש – לייבא אותו.", 14f, pal.text2), lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })
        val bRow = a.hbox()
        bRow.addView(outlineBtn("ייצוא גיבוי") { a.backup.export() }, lp(0, WRAP, 1f))
        bRow.addView(outlineBtn("ייבוא גיבוי") { a.backup.import() }, lp(0, WRAP, 1f).apply { marginStart = a.dpi(12) })
        col.addView(bRow, lp(MATCH, WRAP).apply { topMargin = a.dpi(12) })

        val ver = runCatching { a.packageManager.getPackageInfo(a.packageName, 0).versionName }.getOrNull() ?: ""
        col.addView(a.label("מאקרו $ver", 12f, pal.muted).apply { gravity = Gravity.CENTER },
            lp(MATCH, WRAP).apply { topMargin = a.dpi(28) })
        view = scroll
    }

    private fun outlineBtn(text: String, onClick: () -> Unit) = a.label(text, 15f, pal.accent, bold = true).apply {
        gravity = Gravity.CENTER
        background = roundRect(0, a.dp(12f), pal.accent, a.dpi(1))
        setPadding(0, a.dpi(12), 0, a.dpi(12))
        setOnClickListener { onClick() }
    }

    fun refresh() {
        welcome.visibility = if (a.store.hasGoals) View.GONE else View.VISIBLE
        val gs = a.store.currentGoals()
        for ((m, r) in rows) r.load(gs.of(m))
        updateSuggestion()
    }

    /** Shows how many calories the macro goals add up to, to help set the calorie goal. */
    private fun updateSuggestion() {
        val gs = a.store.currentGoals()
        fun t(m: Metric) = gs.of(m).target
        val k = t(Metric.PROTEIN) * KCAL_PROTEIN + t(Metric.CARBS) * KCAL_CARBS + t(Metric.FAT) * KCAL_FAT
        suggestion.text = if (k > 0) "לפי יעדי החלבון, הפחמימות והשומן השמורים: כ-${fmt(k)} קק״ל ביום." else ""
    }

    private fun save() {
        val map = HashMap<Metric, Goal>()
        for (m in Metric.values()) map[m] = rows[m]!!.read() ?: return
        a.store.setGoals(LocalDate.now(), map)
        Toast.makeText(a, "היעדים נשמרו", Toast.LENGTH_SHORT).show()
        welcome.visibility = View.GONE
        updateSuggestion()
    }
}
