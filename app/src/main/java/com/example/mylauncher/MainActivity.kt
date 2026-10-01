package com.example.mylauncher

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextClock
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var clockTime: TextClock
    private lateinit var clockDate: TextClock
    private lateinit var clockContainer: LinearLayout
    private lateinit var dockRecyclerView: RecyclerView
    private lateinit var drawerRecyclerView: RecyclerView
    private lateinit var drawerSearchInput: EditText
    private lateinit var homeSearchBar: LinearLayout

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>
    private lateinit var drawerAdapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()

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
        dockRecyclerView = findViewById(R.id.dockRecyclerView)
        drawerRecyclerView = findViewById(R.id.drawerRecyclerView)
        drawerSearchInput = findViewById(R.id.drawerSearchInput)
        homeSearchBar = findViewById(R.id.homeSearchBar)

        val bottomSheet = findViewById<LinearLayout>(R.id.appDrawerBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN

        // 1. Clock Font Setup
        val savedFont = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
            .getString("clock_font", fontNames[0]) ?: fontNames[0]
        applyClockFont(savedFont)

        clockContainer.setOnLongClickListener {
            showFontSelectorDialog()
            true
        }

        // 2. Open drawer on search bar click
        homeSearchBar.setOnClickListener {
            openAppDrawer()
        }

        // 3. Swipe-up gesture anywhere on Home Screen to open drawer
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 != null && e1.y - e2.y > 120 && abs(velocityY) > 150) {
                    openAppDrawer()
                    return true
                }
                return false
            }
        })

        findViewById<android.view.View>(R.id.homeScreenView).setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // 4. Load Apps
        loadInstalledApps()

        // 5. Drawer Search Filter
        drawerSearchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim() ?: ""
            val filtered = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.label.contains(query, ignoreCase = true) }
            }
            drawerAdapter.updateList(filtered)
        }

        // 6. Handle Back Button: closes drawer before exiting
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    drawerSearchInput.text.clear()
                }
            }
        })
    }

    private fun openAppDrawer() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        drawerSearchInput.requestFocus()
    }

    private fun showFontSelectorDialog() {
        MaterialAlertDialogBuilder(this)
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

    private fun loadInstalledApps() {
        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        allApps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map { resolveInfo ->
                AppInfo(
                    label = resolveInfo.loadLabel(pm).toString(),
                    packageName = resolveInfo.activityInfo.packageName,
                    icon = resolveInfo.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }

        // Setup Home Dock: Top 8 apps (2 rows of 4, like the reference image)
        dockRecyclerView.layoutManager = GridLayoutManager(this, 4)
        dockRecyclerView.adapter = AppAdapter(allApps.take(8)) { app ->
            launchApp(app)
        }

        // Setup App Drawer Grid (4 columns)
        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        drawerAdapter = AppAdapter(allApps) { app ->
            launchApp(app)
        }
        drawerRecyclerView.adapter = drawerAdapter
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        launchIntent?.let { startActivity(it) }
    }
}