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
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
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
    private lateinit var dockAdapter: AppAdapter

    private var allApps: List<AppInfo> = emptyList()
    private var dockApps: MutableList<AppInfo> = mutableListOf()

    private val fontNames = arrayOf(
        "🌸 Kawaii (Casual)",
        "🎀 Cursive (Cute)",
        "🧸 Chubby (Rounded)",
        "✨ Minimal (Modern Sans)",
        "📖 Vintage (Serif)",
        "👾 Pixel / Tech (Monospace)"
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

        // 1. Clock font setup
        val savedFont = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
            .getString("clock_font", fontNames[0]) ?: fontNames[0]
        applyClockFont(savedFont)

        clockContainer.setOnLongClickListener {
            showFontSelectorDialog()
            true
        }

        // 2. Open drawer via search bar
        homeSearchBar.setOnClickListener { openAppDrawer() }

        // 3. Swipe up on home screen opens drawer
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

        // 5. Setup Swipe-to-Remove on Home Dock
        setupSwipeToRemove()

        // 6. Search filtering in drawer
        drawerSearchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim() ?: ""
            val filtered = if (query.isEmpty()) {
                allApps
            } else {
                allApps.filter { it.label.contains(query, ignoreCase = true) }
            }
            drawerAdapter.updateList(filtered)
        }

        // 7. Back button closes drawer
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                    drawerSearchInput.text.clear()
                }
            }
        })
    }

    private fun setupSwipeToRemove() {
        val swipeCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.UP or ItemTouchHelper.DOWN) {
            override fun onMove(r: RecyclerView, v: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val removedApp = dockApps[position]

                dockApps.removeAt(position)
                dockAdapter.notifyItemRemoved(position)
                saveDockPackages()

                Snackbar.make(dockRecyclerView, "Removed ${removedApp.label} 🌸", Snackbar.LENGTH_LONG)
                    .setAction("Undo ✨") {
                        dockApps.add(position, removedApp)
                        dockAdapter.notifyItemInserted(position)
                        saveDockPackages()
                    }
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(dockRecyclerView)
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

        // Restore saved dock apps or use first 8
        val savedDockPackages = getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
            .getStringSet("dock_packages", null)

        dockApps = if (savedDockPackages != null) {
            allApps.filter { savedDockPackages.contains(it.packageName) }.toMutableList()
        } else {
            allApps.take(8).toMutableList()
        }

        // Setup Home Dock Adapter
        dockRecyclerView.layoutManager = GridLayoutManager(this, 4)
        dockAdapter = AppAdapter(dockApps,
            onAppClick = { launchApp(it) },
            onAppLongClick = { app ->
                // Long press dock app allows direct removal
                MaterialAlertDialogBuilder(this)
                    .setTitle("Remove from Home? 🌸")
                    .setMessage("Remove ${app.label} from your home screen?")
                    .setPositiveButton("Remove") { _, _ ->
                        val idx = dockApps.indexOf(app)
                        if (idx != -1) {
                            dockApps.removeAt(idx)
                            dockAdapter.notifyItemRemoved(idx)
                            saveDockPackages()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        dockRecyclerView.adapter = dockAdapter

        // Setup Drawer Adapter: Long-press adds to home dock
        drawerRecyclerView.layoutManager = GridLayoutManager(this, 4)
        drawerAdapter = AppAdapter(allApps.toMutableList(),
            onAppClick = { launchApp(it) },
            onAppLongClick = { app ->
                if (dockApps.none { it.packageName == app.packageName }) {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("💖 Add to Home")
                        .setMessage("Add ${app.label} to your home dock?")
                        .setPositiveButton("Add ✨") { _, _ ->
                            dockApps.add(app)
                            dockAdapter.notifyItemInserted(dockApps.size - 1)
                            saveDockPackages()
                            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    Snackbar.make(drawerRecyclerView, "${app.label} is already on Home! 🎀", Snackbar.LENGTH_SHORT).show()
                }
            }
        )
        drawerRecyclerView.adapter = drawerAdapter
    }

    private fun saveDockPackages() {
        val packageSet = dockApps.map { it.packageName }.toSet()
        getSharedPreferences("LauncherPrefs", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("dock_packages", packageSet)
            .apply()
    }

    private fun applyClockFont(fontName: String) {
        val typeface: Typeface = when (fontName) {
            "🎀 Cursive (Cute)" -> Typeface.create("cursive", Typeface.BOLD)
            "🌸 Kawaii (Casual)" -> Typeface.create("casual", Typeface.BOLD)
            "🧸 Chubby (Rounded)" -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
            "📖 Vintage (Serif)" -> Typeface.SERIF
            "👾 Pixel / Tech (Monospace)" -> Typeface.MONOSPACE
            else -> Typeface.create("sans-serif", Typeface.NORMAL)
        }
        clockTime.typeface = typeface
        clockDate.typeface = typeface
    }

    private fun showFontSelectorDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("✨ Select Clock Font ✨")
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

    private fun openAppDrawer() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        drawerSearchInput.requestFocus()
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        launchIntent?.let { startActivity(it) }
    }
}
