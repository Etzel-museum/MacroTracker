package com.davidlevi.macros

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * All data lives in one JSON file in the app's private storage. The same JSON is
 * what backup export/import reads and writes.
 */
class Store(context: Context) {
    private val file = File(context.filesDir, "data.json")

    private val entries = mutableListOf<Entry>()
    /** Sorted by [GoalSet.from]. */
    private val goalSets = mutableListOf<GoalSet>()
    private var nextId = 1L

    init {
        if (file.exists()) {
            runCatching { load(JSONObject(file.readText())) }
        }
    }

    val hasGoals: Boolean get() = goalSets.isNotEmpty()

    // ---------- entries ----------

    fun entriesOn(date: LocalDate): List<Entry> =
        entries.filter { it.date == date }.sortedByDescending { it.time }

    fun totalsOn(date: LocalDate): Totals =
        entries.filter { it.date == date }.fold(Totals()) { t, e -> t + e }

    /** Totals for every day in [from]..[to] that has at least one entry. */
    fun dailyTotals(from: LocalDate, to: LocalDate): Map<LocalDate, Totals> {
        val out = HashMap<LocalDate, Totals>()
        for (e in entries) {
            if (e.date < from || e.date > to) continue
            out[e.date] = (out[e.date] ?: Totals()) + e
        }
        return out
    }

    fun addEntry(date: LocalDate, time: String, p: Double, c: Double, f: Double) {
        entries += Entry(nextId++, date, time, p, c, f)
        save()
    }

    fun updateEntry(e: Entry) {
        val i = entries.indexOfFirst { it.id == e.id }
        if (i >= 0) entries[i] = e
        save()
    }

    fun deleteEntry(id: Long) {
        entries.removeAll { it.id == id }
        save()
    }

    // ---------- goals ----------

    /** Goals in force on [date]; days before the first goal set use the first one. */
    fun goalsOn(date: LocalDate): GoalSet {
        if (goalSets.isEmpty()) return defaultGoals(date)
        return goalSets.lastOrNull { it.from <= date } ?: goalSets.first()
    }

    fun currentGoals(): GoalSet = goalsOn(LocalDate.now())

    /** New goals apply from [from] onward; earlier days keep the goals they had. */
    fun setGoals(from: LocalDate, goals: Map<Metric, Goal>) {
        goalSets.removeAll { it.from >= from }
        goalSets += GoalSet(from, goals)
        goalSets.sortBy { it.from }
        save()
    }

    // ---------- persistence ----------

    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("app", "macros")
        root.put("version", 1)
        root.put("nextId", nextId)
        root.put("entries", JSONArray().apply {
            for (e in entries) put(JSONObject().apply {
                put("id", e.id)
                put("date", e.date.toString())
                put("time", e.time)
                put("p", e.protein)
                put("c", e.carbs)
                put("f", e.fat)
            })
        })
        root.put("goals", JSONArray().apply {
            for (gs in goalSets) put(JSONObject().apply {
                put("from", gs.from.toString())
                for ((m, g) in gs.goals) put(m.name, JSONObject().apply {
                    put("mode", g.mode.name)
                    put("min", g.min)
                    put("max", g.max)
                })
            })
        })
        return root
    }

    /** Replaces all data with the backup. Throws if the JSON isn't a valid backup. */
    fun importJson(text: String) {
        val root = JSONObject(text)
        require(root.optString("app") == "macros") { "not a backup" }
        load(root)
        save()
    }

    private fun load(root: JSONObject) {
        val newEntries = mutableListOf<Entry>()
        val ea = root.optJSONArray("entries") ?: JSONArray()
        for (i in 0 until ea.length()) {
            val o = ea.getJSONObject(i)
            newEntries += Entry(
                o.getLong("id"),
                LocalDate.parse(o.getString("date")),
                o.optString("time", "00:00"),
                o.optDouble("p", 0.0),
                o.optDouble("c", 0.0),
                o.optDouble("f", 0.0),
            )
        }
        val newGoals = mutableListOf<GoalSet>()
        val ga = root.optJSONArray("goals") ?: JSONArray()
        for (i in 0 until ga.length()) {
            val o = ga.getJSONObject(i)
            val map = HashMap<Metric, Goal>()
            for (m in Metric.values()) {
                val g = o.optJSONObject(m.name) ?: continue
                val mode = runCatching { GoalMode.valueOf(g.getString("mode")) }.getOrDefault(GoalMode.NONE)
                map[m] = Goal(mode, g.optDouble("min", 0.0), g.optDouble("max", 0.0))
            }
            newGoals += GoalSet(LocalDate.parse(o.getString("from")), map)
        }
        entries.clear(); entries += newEntries
        goalSets.clear(); goalSets += newGoals.sortedBy { it.from }
        nextId = maxOf(root.optLong("nextId", 1L), (entries.maxOfOrNull { it.id } ?: 0L) + 1)
    }

    private fun save() {
        val tmp = File(file.parentFile, "data.json.tmp")
        tmp.writeText(toJson().toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    companion object {
        fun defaultGoals(from: LocalDate) = GoalSet(
            from,
            mapOf(
                Metric.PROTEIN to Goal(GoalMode.MIN, min = 140.0),
                Metric.CARBS to Goal(GoalMode.MAX, max = 200.0),
                Metric.FAT to Goal(GoalMode.MAX, max = 65.0),
                Metric.CALORIES to Goal(GoalMode.MAX, max = 2000.0),
            )
        )
    }
}
