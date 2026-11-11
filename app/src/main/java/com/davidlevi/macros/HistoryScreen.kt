package com.davidlevi.macros

import android.view.Gravity
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import java.time.LocalDate

/** History charts: week / month / 3 months / year, per metric, with paging back in time. */
class HistoryScreen(private val a: MainActivity) {
    enum class Period(val label: String) { WEEK("שבוע"), MONTH("חודש"), QUARTER("3 חודשים"), YEAR("שנה") }

    private var period = Period.WEEK
    private var metric = Metric.PROTEIN
    /** 0 = the period ending today, 1 = the one before it, ... */
    private var offset = 0

    private val pal get() = a.pal
    val view: View
    private val periodTabs = HashMap<Period, TextView>()
    private val metricChips = HashMap<Metric, TextView>()
    private val rangeLabel: TextView
    private val newerBtn: TextView
    private val avgValue: TextView
    private val avgLabel: TextView
    private val metValue: TextView
    private val chart: BarChartView
    private val selLabel: TextView
    private val openDayBtn: TextView
    private val legend: TextView

    /** Per bucket: first and last day it covers (for the selection label). */
    private var bucketDays: List<Pair<LocalDate, LocalDate>> = emptyList()
    private var bucketValues: List<Double?> = emptyList()

    init {
        val scroll = ScrollView(a)
        val col = a.vbox().apply { setPadding(a.dpi(16), a.dpi(14), a.dpi(16), a.dpi(24)) }
        scroll.addView(col)

        col.addView(a.label("היסטוריה", 20f, pal.text, bold = true), lp(MATCH, WRAP))

        // Period segmented control
        val seg = a.hbox().apply {
            background = roundRect(pal.line, a.dp(10f))
            setPadding(a.dpi(3), a.dpi(3), a.dpi(3), a.dpi(3))
        }
        for (p in Period.values()) {
            val t = a.label(p.label, 14f, pal.text2).apply {
                gravity = Gravity.CENTER
                setPadding(0, a.dpi(8), 0, a.dpi(8))
                setOnClickListener { period = p; offset = 0; refresh() }
            }
            periodTabs[p] = t
            seg.addView(t, lp(0, WRAP, 1f))
        }
        col.addView(seg, lp(MATCH, WRAP).apply { topMargin = a.dpi(12) })

        // Metric chips
        val chips = a.hbox()
        for (m in Metric.values()) {
            val t = a.label(m.label, 14f, pal.text2).apply {
                gravity = Gravity.CENTER
                setPadding(a.dpi(14), a.dpi(7), a.dpi(14), a.dpi(7))
                setOnClickListener { metric = m; refresh() }
            }
            metricChips[m] = t
            chips.addView(t, lp(WRAP, WRAP).apply { marginEnd = a.dpi(8) })
        }
        val chipScroll = android.widget.HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
        }
        col.addView(chipScroll, lp(MATCH, WRAP).apply { topMargin = a.dpi(12) })

        // Paging row: right arrow = older, left arrow = newer (RTL)
        val nav = a.hbox()
        val olderBtn = a.label("›", 28f, pal.text2).apply {
            gravity = Gravity.CENTER
            setOnClickListener { offset++; refresh() }
        }
        newerBtn = a.label("‹", 28f, pal.text2).apply {
            gravity = Gravity.CENTER
            setOnClickListener { if (offset > 0) { offset--; refresh() } }
        }
        rangeLabel = a.label("", 15f, pal.text).apply { gravity = Gravity.CENTER }
        nav.addView(olderBtn, lp(a.dpi(48), a.dpi(44)))
        nav.addView(rangeLabel, lp(0, WRAP, 1f))
        nav.addView(newerBtn, lp(a.dpi(48), a.dpi(44)))
        col.addView(nav, lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })

        // Stats
        val card = a.card()
        val stats = a.hbox()
        val s1 = a.vbox()
        avgLabel = a.label("ממוצע יומי", 13f, pal.text2)
        s1.addView(avgLabel)
        avgValue = a.label("", 24f, pal.text, bold = true)
        s1.addView(avgValue)
        stats.addView(s1, lp(0, WRAP, 1f))
        val s2 = a.vbox().apply { gravity = Gravity.END }
        s2.addView(a.label("ימים ביעד", 13f, pal.text2))
        metValue = a.label("", 24f, pal.text, bold = true)
        s2.addView(metValue)
        stats.addView(s2, lp(WRAP, WRAP))
        card.addView(stats, lp(MATCH, WRAP))

        chart = BarChartView(a).apply {
            axisColor = pal.line
            textColor = pal.muted
            goalColor = pal.text
            selectColor = pal.line
            onSelect = { i -> showSelection(i) }
        }
        card.addView(chart, lp(MATCH, WRAP).apply { topMargin = a.dpi(14) })

        val selRow = a.hbox()
        selLabel = a.label("", 14f, pal.text)
        selRow.addView(selLabel, lp(0, WRAP, 1f))
        openDayBtn = a.label("פתח את היום ›", 14f, pal.accent, bold = true).apply {
            setPadding(a.dpi(8), a.dpi(8), 0, a.dpi(8))
            visibility = View.GONE
        }
        selRow.addView(openDayBtn, lp(WRAP, WRAP))
        card.addView(selRow, lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })

        legend = a.label("", 12f, pal.muted)
        card.addView(legend, lp(MATCH, WRAP).apply { topMargin = a.dpi(4) })
        col.addView(card, lp(MATCH, WRAP).apply { topMargin = a.dpi(6) })

        view = scroll
    }

    fun refresh() {
        for ((p, t) in periodTabs) {
            val on = p == period
            t.background = if (on) roundRect(pal.card, a.dp(8f)) else null
            t.setTextColor(if (on) pal.text else pal.text2)
        }
        for ((m, t) in metricChips) {
            val on = m == metric
            t.background = if (on) roundRect(pal.light(m), a.dp(18f), pal.strong(m), a.dpi(1))
            else roundRect(0, a.dp(18f), pal.line, a.dpi(1))
            t.setTextColor(if (on) pal.text else pal.text2)
        }
        newerBtn.alpha = if (offset > 0) 1f else 0.25f

        val today = LocalDate.now()
        // Bucket ranges, oldest first.
        val ranges = ArrayList<Pair<LocalDate, LocalDate>>()
        when (period) {
            Period.WEEK, Period.MONTH -> {
                val len = if (period == Period.WEEK) 7L else 30L
                val end = today.minusDays(len * offset)
                for (i in len - 1 downTo 0) { val d = end.minusDays(i); ranges += d to d }
            }
            Period.QUARTER -> {
                val end = today.minusWeeks(13L * offset)
                for (i in 12 downTo 0) {
                    val e = end.minusWeeks(i.toLong())
                    ranges += e.minusDays(6) to e
                }
            }
            Period.YEAR -> {
                val endMonth = today.withDayOfMonth(1).minusMonths(12L * offset)
                for (i in 11 downTo 0) {
                    val s = endMonth.minusMonths(i.toLong())
                    ranges += s to s.plusMonths(1).minusDays(1)
                }
            }
        }
        val from = ranges.first().first
        val to = ranges.last().second
        rangeLabel.text = when (period) {
            Period.YEAR -> "${MONTHS_HE[from.monthValue - 1]} ${from.year} – ${MONTHS_HE[to.monthValue - 1]} ${to.year}"
            else -> "${shortDate(from)}.${from.year % 100} – ${shortDate(to)}.${to.year % 100}"
        }

        val daily = a.store.dailyTotals(from, to)
        val buckets = ArrayList<Bucket>()
        val values = ArrayList<Double?>()
        for ((i, r) in ranges.withIndex()) {
            val (s, e) = r
            val days = generateSequence(s) { it.plusDays(1) }.takeWhile { it <= e }
                .mapNotNull { daily[it] }.toList()
            val hasData = days.isNotEmpty()
            val avg = if (hasData) Totals(
                days.sumOf { it.protein } / days.size,
                days.sumOf { it.carbs } / days.size,
                days.sumOf { it.fat } / days.size,
            ) else Totals()
            val goal = a.store.goalsOn(minOf(e, today)).of(metric)
            val v = avg.of(metric)
            val segs = if (metric == Metric.CALORIES)
                doubleArrayOf(avg.kcalProtein, avg.kcalCarbs, avg.kcalFat)
            else doubleArrayOf(v)
            val lines = when (goal.mode) {
                GoalMode.NONE -> doubleArrayOf()
                GoalMode.MIN -> doubleArrayOf(goal.min)
                GoalMode.MAX -> doubleArrayOf(goal.max)
                GoalMode.RANGE -> doubleArrayOf(goal.min, goal.max)
            }
            buckets += Bucket(
                label = bucketLabel(i, s, ranges.size),
                segs = segs,
                goalLines = lines,
                // Calories bars stay in their three macro colors.
                met = if (hasData && metric != Metric.CALORIES) goal.isMet(v) else null,
                hasData = hasData,
                isCurrent = !e.isBefore(today) && !s.isAfter(today),
            )
            values += if (hasData) v else null
        }
        bucketDays = ranges
        bucketValues = values

        if (metric == Metric.CALORIES) {
            chart.strong = intArrayOf(pal.strong(Metric.PROTEIN), pal.strong(Metric.CARBS), pal.strong(Metric.FAT))
            chart.light = chart.strong
        } else {
            chart.strong = intArrayOf(pal.strong(metric))
            chart.light = intArrayOf(pal.light(metric))
        }
        chart.buckets = buckets

        // Stats over individual days with data, each judged against its own day's goal.
        val dayList = daily.entries.filter { it.key <= today }
        if (dayList.isEmpty()) {
            avgValue.text = "—"
            metValue.text = "—"
        } else {
            val avg = dayList.sumOf { it.value.of(metric) } / dayList.size
            avgValue.text = "${fmt(avg)} ${metric.unit}"
            val judged = dayList.mapNotNull { a.store.goalsOn(it.key).of(metric).isMet(it.value.of(metric)) }
            metValue.text = if (judged.isEmpty()) "—" else "${judged.count { it }}/${judged.size}"
        }
        avgLabel.text = "ממוצע יומי (${dayList.size} ימים עם נתונים)"

        legend.text = if (metric == Metric.CALORIES)
            "צבעי העמודה: חלבון · פחמימות · שומן. קו מקווקו = יעד."
        else if (period == Period.WEEK || period == Period.MONTH)
            "כהה = עמד ביעד · בהיר = לא עמד · קו מקווקו = יעד"
        else
            "כל עמודה = ממוצע יומי ל${if (period == Period.YEAR) "חודש" else "שבוע"} · כהה = עמד ביעד · קו מקווקו = יעד"

        selLabel.text = "לחיצה על עמודה מציגה את הערך שלה"
        selLabel.setTextColor(pal.muted)
        openDayBtn.visibility = View.GONE
    }

    private fun bucketLabel(i: Int, start: LocalDate, n: Int): String = when (period) {
        Period.WEEK -> dayLetter(start)
        Period.MONTH -> if ((n - 1 - i) % 5 == 0) start.dayOfMonth.toString() else ""
        Period.QUARTER -> if ((n - 1 - i) % 3 == 0) shortDate(start) else ""
        Period.YEAR -> start.monthValue.toString()
    }

    private fun showSelection(i: Int) {
        val (s, e) = bucketDays[i]
        val v = bucketValues[i]
        val name = when (period) {
            Period.WEEK, Period.MONTH -> dayTitle(s)
            Period.QUARTER -> "שבוע ${shortDate(s)}–${shortDate(e)}"
            Period.YEAR -> "${MONTHS_HE[s.monthValue - 1]} ${s.year}"
        }
        val prefix = if (period == Period.WEEK || period == Period.MONTH) "" else "ממוצע "
        selLabel.text = if (v == null) "$name: אין נתונים" else "$name: $prefix${fmt(v)} ${metric.unit}"
        selLabel.setTextColor(pal.text)
        val daily = period == Period.WEEK || period == Period.MONTH
        openDayBtn.visibility = if (daily) View.VISIBLE else View.GONE
        openDayBtn.setOnClickListener { a.openDay(s) }
    }

}
