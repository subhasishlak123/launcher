package com.example.mylauncher

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextClock
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private lateinit var clockTime: TextClock
    private lateinit var clockDate: TextClock
    private lateinit var clockContainer: LinearLayout
    private lateinit var appRecyclerView: RecyclerView

    // Available font choices
    private val fontNames = arrayOf(
        "Modern Sans-Serif (Default)",
        "Serif (Classic)",
        "Monospace (Tech)",
        "Casual (Playful)",
        "Condensed (Compact)",
        "Light (Minimal)"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        clockTime = findViewById(R.id.clockTime)
        clockDate = findViewById(R.id.clockDate)
        clockContainer = findViewById(R.id.clockContainer)
        appRecyclerView = findViewById(R.id.appRecyclerView)

        // 1. Initialize and apply saved font preference
        val savedFont = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
            .getString("clock_font", fontNames[0]) ?: fontNames[0]
        applyClockFont(savedFont)

        // 2. Long press clock to change font
        clockContainer.setOnLongClickListener {
            showFontSelectorDialog()
            true
        }

        // 3. Load apps in a 4-column grid (standard Android layout)
        setupAppGrid()
    }

    private fun showFontSelectorDialog() {
        AlertDialog.Builder(this)
            .setTitle("Select Clock Font")
            .setItems(fontNames) { _, index ->
                val selected = fontNames[index]
                getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString("clock_font", selected)
                    .apply()
                applyClockFont(selected)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun applyClockFont(fontName: String) {
        val typeface: Typeface = when (fontName) {
            "Serif (Classic)" -> Typeface.SERIF
            "Monospace (Tech)" -> Typeface.MONOSPACE
            "Casual (Playful)" -> Typeface.create("casual", Typeface.NORMAL)
            "Condensed (Compact)" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
            "Light (Minimal)" -> Typeface.create("sans-serif-light", Typeface.NORMAL)
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }

        clockTime.typeface = typeface
        clockDate.typeface = typeface
    }

    private fun setupAppGrid() {
        appRecyclerView.layoutManager = GridLayoutManager(this, 4)

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val apps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName } // Exclude self
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }

        appRecyclerView.adapter = AppAdapter(apps) { app ->
            val launchIntent = pm.getLaunchIntentForPackage(app.packageName)
            launchIntent?.let { startActivity(it) }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Prevents back button from exiting the launcher
    }
}
