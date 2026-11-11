package com.davidlevi.macros

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

/** One activity; the bottom bar swaps between the three screens. */
class MainActivity : Activity() {
    lateinit var store: Store
    lateinit var pal: Palette

    private lateinit var content: FrameLayout
    private val tabs = mutableListOf<TextView>()

    private lateinit var today: TodayScreen
    private lateinit var history: HistoryScreen
    private lateinit var settings: SettingsScreen
    private var current = 0

    lateinit var backup: Backup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this)
        pal = Palette.of(this)
        backup = Backup(this)
        window.statusBarColor = pal.bg
        window.navigationBarColor = pal.card
        if (!pal.dark) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }

        val root = vbox().apply {
            layoutDirection = View.LAYOUT_DIRECTION_RTL
            setBackgroundColor(pal.bg)
        }
        content = FrameLayout(this)
        root.addView(content, lp(MATCH, 0, 1f))

        val bar = hbox().apply {
            setBackgroundColor(pal.card)
            setPadding(0, dpi(6), 0, dpi(6))
        }
        for ((i, name) in listOf("היום", "היסטוריה", "הגדרות").withIndex()) {
            val t = label(name, 15f, pal.text2, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(0, dpi(10), 0, dpi(10))
                setOnClickListener { show(i) }
            }
            tabs += t
            bar.addView(t, lp(0, WRAP, 1f))
        }
        root.addView(View(this).apply { setBackgroundColor(pal.line) }, lp(MATCH, dpi(1)))
        root.addView(bar, lp(MATCH, WRAP))
        setContentView(root)

        today = TodayScreen(this)
        history = HistoryScreen(this)
        settings = SettingsScreen(this)

        val start = savedInstanceState?.getInt("tab") ?: if (store.hasGoals) 0 else 2
        show(start)
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putInt("tab", current)
    }

    override fun onResume() {
        super.onResume()
        // The day may have changed while the app sat in the background.
        refreshCurrent()
    }

    fun show(i: Int) {
        current = i
        content.removeAllViews()
        val v = when (i) {
            0 -> today.view
            1 -> history.view
            else -> settings.view
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        for ((j, t) in tabs.withIndex()) t.setTextColor(if (j == i) pal.accent else pal.text2)
        refreshCurrent()
    }

    fun openDay(d: LocalDate) {
        today.date = d
        show(0)
    }

    /** Called after data changes (entries, goals, backup import). */
    fun refreshCurrent() {
        when (current) {
            0 -> today.refresh()
            1 -> history.refresh()
            else -> settings.refresh()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        backup.onResult(requestCode, resultCode, data)
    }

    /** A white rounded card container. */
    fun card(): LinearLayout = vbox().apply {
        background = roundRect(pal.card, dp(16f))
        setPadding(dpi(16), dpi(14), dpi(16), dpi(14))
    }
}
