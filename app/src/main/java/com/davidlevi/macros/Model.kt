package com.davidlevi.macros

import java.time.LocalDate
import java.util.Locale

/** The four tracked metrics. Calories are never entered; they're derived from the macros. */
enum class Metric(val label: String, val unit: String) {
    PROTEIN("חלבון", "ג׳"),
    CARBS("פחמימות", "ג׳"),
    FAT("שומן", "ג׳"),
    CALORIES("קלוריות", "קק״ל");
}

enum class GoalMode(val label: String) {
    NONE("ללא יעד"),
    MIN("מינימום"),
    MAX("מקסימום"),
    RANGE("טווח");
}

data class Goal(val mode: GoalMode, val min: Double = 0.0, val max: Double = 0.0) {
    /** The number the ring/bar fills toward. */
    val target: Double
        get() = when (mode) {
            GoalMode.NONE -> 0.0
            GoalMode.MIN -> min
            GoalMode.MAX, GoalMode.RANGE -> max
        }

    fun isMet(v: Double): Boolean? = when (mode) {
        GoalMode.NONE -> null
        GoalMode.MIN -> v >= min
        GoalMode.MAX -> v <= max
        GoalMode.RANGE -> v in min..max
    }

    fun isOver(v: Double): Boolean =
        (mode == GoalMode.MAX || mode == GoalMode.RANGE) && v > max

    /** Short text such as "מתוך 140 · מינ׳". */
    fun describe(): String = when (mode) {
        GoalMode.NONE -> "ללא יעד"
        GoalMode.MIN -> "מתוך ${fmt(min)} · מינ׳"
        GoalMode.MAX -> "מתוך ${fmt(max)} · מקס׳"
        GoalMode.RANGE -> "${fmt(min)}–${fmt(max)} · טווח"
    }
}

/** Goals that are in force from [from] (inclusive) until the next GoalSet. */
data class GoalSet(val from: LocalDate, val goals: Map<Metric, Goal>) {
    fun of(m: Metric): Goal = goals[m] ?: Goal(GoalMode.NONE)
}

data class Entry(
    val id: Long,
    val date: LocalDate,
    val time: String,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

/** Calories per gram. */
const val KCAL_PROTEIN = 4.0
const val KCAL_CARBS = 4.0
const val KCAL_FAT = 9.0

data class Totals(val protein: Double = 0.0, val carbs: Double = 0.0, val fat: Double = 0.0) {
    val kcalProtein get() = protein * KCAL_PROTEIN
    val kcalCarbs get() = carbs * KCAL_CARBS
    val kcalFat get() = fat * KCAL_FAT
    val calories get() = kcalProtein + kcalCarbs + kcalFat

    fun of(m: Metric): Double = when (m) {
        Metric.PROTEIN -> protein
        Metric.CARBS -> carbs
        Metric.FAT -> fat
        Metric.CALORIES -> calories
    }

    operator fun plus(e: Entry) = Totals(protein + e.protein, carbs + e.carbs, fat + e.fat)
}

/** Whole numbers without decimals, others with one decimal. */
fun fmt(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) String.format(Locale.US, "%,d", r.toLong())
    else String.format(Locale.US, "%,.1f", r)
}

fun parseNum(s: String): Double? {
    val t = s.trim().replace(',', '.')
    if (t.isEmpty()) return 0.0
    return t.toDoubleOrNull()?.takeIf { it >= 0 && !it.isNaN() && !it.isInfinite() }
}
