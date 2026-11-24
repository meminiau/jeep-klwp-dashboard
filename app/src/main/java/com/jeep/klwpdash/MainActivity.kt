package com.jeep.klwpdash

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.jeep.klwpdash.data.DashApp
import com.jeep.klwpdash.data.DashPrefs
import com.jeep.klwpdash.launcher.FloatingWindowLauncher
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private data class SidebarSlot(
        val app: DashApp,
        val label: String,
        val iconRes: Int,
        val root: View,
        val cardBg: ImageView,
        val iconView: ImageView,
        val labelView: TextView,
        val arrowView: ImageView
    )

    private data class DrawerAppItem(
        val label: String,
        val packageName: String,
        val icon: Drawable
    )

    private lateinit var prefs: DashPrefs
    private lateinit var hudOverlayContainer: View
    private lateinit var tvClock: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvHudStatus: TextView
    private lateinit var layoutAppDrawer: LinearLayout
    private lateinit var layoutDrawerHeader: LinearLayout
    private lateinit var etSearchApps: EditText
    private lateinit var rvAppDrawer: RecyclerView

    private val sidebarSlots = mutableListOf<SidebarSlot>()
    private val drawerAdapter = AppDrawerAdapter()
    private val allApps = mutableListOf<DrawerAppItem>()
    private val appLoaderExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isDrawerOpen = false
    private var touchSlop = 24
    private var swipeThresholdPx = 90f
    private var downRawX = 0f
    private var downRawY = 0f
    private var swipeHandledForGesture = false
    private var headerDownRawY = 0f

    private var batteryPct: Int = -1

    private val clockTickRunnable = object : Runnable {
        override fun run() {
            updateClockAndStatus()
            mainHandler.postDelayed(this, 15_000L)
        }
    }

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_BATTERY_CHANGED -> {
                    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                    if (level >= 0 && scale > 0) {
                        batteryPct = (level * 100) / scale
                    }
                    updateClockAndStatus()
                }
                Intent.ACTION_TIME_TICK,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_DATE_CHANGED -> updateClockAndStatus()
            }
        }
    }

    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadInstalledAppsAsync()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = DashPrefs(this)
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        swipeThresholdPx = 48f * resources.displayMetrics.density

        hudOverlayContainer = findViewById(R.id.hudOverlayContainer)
        tvClock = findViewById(R.id.tvClock)
        tvDate = findViewById(R.id.tvDate)
        tvHudStatus = findViewById(R.id.tvHudStatus)
        layoutAppDrawer = findViewById(R.id.layoutAppDrawer)
        layoutDrawerHeader = findViewById(R.id.layoutDrawerHeader)
        etSearchApps = findViewById(R.id.etSearchApps)
        rvAppDrawer = findViewById(R.id.rvAppDrawer)

        applySystemBarInsets()
        setupSidebarButtons()
        setupHudInteractions()
        setupAppDrawer()
        setupBackBehavior()
        registerReceivers()

        restoreActiveHighlight()
        updateClockAndStatus()
        loadInstalledAppsAsync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Pressing Home returns cleanly to the Jeep HUD
        if (isDrawerOpen) {
            closeAppDrawer(animate = false)
        }
        etSearchApps.text?.clear()
        hideKeyboard()
        updateClockAndStatus()
    }

    override fun onResume() {
        super.onResume()
        restoreActiveHighlight()
        updateClockAndStatus()
        mainHandler.removeCallbacks(clockTickRunnable)
        mainHandler.post(clockTickRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(clockTickRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(systemReceiver)
        } catch (_: Exception) {
        }
        try {
            unregisterReceiver(packageChangeReceiver)
        } catch (_: Exception) {
        }
        appLoaderExecutor.shutdownNow()
    }

    private fun applySystemBarInsets() {
        val baseStatusTop = (8 * resources.displayMetrics.density).toInt()
        val baseGearTop = (6 * resources.displayMetrics.density).toInt()
        val baseDrawerTop = (12 * resources.displayMetrics.density).toInt()
        val baseHandleBottom = (10 * resources.displayMetrics.density).toInt()

        val btnGear = findViewById<ImageButton>(R.id.btnSettingsGear)
        val drawerHandle = findViewById<View>(R.id.layoutDrawerHandle)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rootHudContainer)) { _, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            (tvHudStatus.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = baseStatusTop + sysBars.top
                tvHudStatus.layoutParams = lp
            }
            (btnGear.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.topMargin = baseGearTop + sysBars.top
                btnGear.layoutParams = lp
            }
            (drawerHandle.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                lp.bottomMargin = baseHandleBottom + sysBars.bottom
                drawerHandle.layoutParams = lp
            }
            layoutDrawerHeader.setPadding(
                layoutDrawerHeader.paddingLeft,
                baseDrawerTop + sysBars.top,
                layoutDrawerHeader.paddingRight,
                layoutDrawerHeader.paddingBottom
            )
            rvAppDrawer.setPadding(
                rvAppDrawer.paddingLeft,
                rvAppDrawer.paddingTop,
                rvAppDrawer.paddingRight,
                (24 * resources.displayMetrics.density).toInt() + sysBars.bottom
            )
            insets
        }
    }

    private fun setupSidebarButtons() {
        val definitions = listOf(
            Triple(R.id.btnGemini, DashApp.GEMINI, R.drawable.icon_gemini to "Gemini"),
            Triple(R.id.btnTorque, DashApp.TORQUE, R.drawable.icon_torque to "Torque"),
            Triple(R.id.btnJellyfin, DashApp.JELLYFIN, R.drawable.icon_jellyfin to "Jellyfin"),
            Triple(R.id.btnWaze, DashApp.WAZE, R.drawable.icon_waze to "Waze"),
            Triple(R.id.btnYouTube, DashApp.YOUTUBE, R.drawable.icon_youtube to "YouTube"),
            Triple(R.id.btnYouTubeMusic, DashApp.YOUTUBE_MUSIC, R.drawable.icon_ytmusic to "YouTube\nMusic")
        )

        sidebarSlots.clear()
        for ((viewId, dashApp, iconAndLabel) in definitions) {
            val root = findViewById<View>(viewId)
            val cardBg = root.findViewById<ImageView>(R.id.ivCardBg)
            val iconView = root.findViewById<ImageView>(R.id.ivAppIcon)
            val labelView = root.findViewById<TextView>(R.id.tvAppLabel)
            val arrowView = root.findViewById<ImageView>(R.id.ivActiveArrow)

            iconView.setImageResource(iconAndLabel.first)
            labelView.text = iconAndLabel.second
            if (dashApp == DashApp.YOUTUBE_MUSIC) {
                labelView.textSize = 10f
            }

            val slot = SidebarSlot(
                app = dashApp,
                label = iconAndLabel.second,
                iconRes = iconAndLabel.first,
                root = root,
                cardBg = cardBg,
                iconView = iconView,
                labelView = labelView,
                arrowView = arrowView
            )
            sidebarSlots += slot

            root.setOnClickListener {
                setActiveApp(dashApp)
                launchSidebarApp(dashApp)
            }
            root.setOnLongClickListener {
                openSettingsScreen()
                true
            }
        }
    }

    private fun setupHudInteractions() {
        findViewById<ImageButton>(R.id.btnSettingsGear).setOnClickListener {
            openSettingsScreen()
        }

        findViewById<View>(R.id.layoutClockBezel).setOnClickListener {
            openSettingsScreen()
        }

        findViewById<View>(R.id.viewHudFrameTouch).setOnClickListener {
            setActiveApp(null)
        }

        findViewById<View>(R.id.layoutDrawerHandle).setOnClickListener {
            openAppDrawer()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!isDrawerOpen) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX
                    downRawY = ev.rawY
                    swipeHandledForGesture = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (!swipeHandledForGesture) {
                        val dyUp = downRawY - ev.rawY
                        val dx = abs(ev.rawX - downRawX)
                        if (dyUp > swipeThresholdPx && dyUp > dx * 1.3f) {
                            swipeHandledForGesture = true
                            val cancelEvent = MotionEvent.obtain(
                                ev.downTime,
                                SystemClock.uptimeMillis(),
                                MotionEvent.ACTION_CANCEL,
                                ev.x,
                                ev.y,
                                0
                            )
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                            openAppDrawer()
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (swipeHandledForGesture) {
                        swipeHandledForGesture = false
                        return true
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun setupAppDrawer() {
        rvAppDrawer.layoutManager = GridLayoutManager(this, 4)
        rvAppDrawer.adapter = drawerAdapter

        findViewById<ImageButton>(R.id.btnCloseDrawer).setOnClickListener {
            closeAppDrawer()
        }
        findViewById<MaterialButton>(R.id.btnDrawerSettings).setOnClickListener {
            closeAppDrawer(animate = false)
            openSettingsScreen()
        }

        // Allow swiping down on the drawer header to dismiss
        layoutDrawerHeader.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> headerDownRawY = event.rawY
                MotionEvent.ACTION_MOVE -> {
                    if (event.rawY - headerDownRawY > swipeThresholdPx) {
                        closeAppDrawer()
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }

        etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterDrawerApps(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupBackBehavior() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isDrawerOpen) {
                    closeAppDrawer()
                }
                // Home Launcher ignores Back when already on Home HUD
            }
        })
    }

    private fun registerReceivers() {
        val sysFilter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, sysFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(systemReceiver, sysFilter)
        }

        val pkgFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageChangeReceiver, pkgFilter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(packageChangeReceiver, pkgFilter)
        }
    }

    private fun restoreActiveHighlight() {
        setActiveApp(DashApp.fromId(prefs.activeAppId), persist = false)
    }

    private fun setActiveApp(activeApp: DashApp?, persist: Boolean = true) {
        if (persist) {
            prefs.activeAppId = activeApp?.id
        }
        for (slot in sidebarSlots) {
            val isActive = slot.app == activeApp
            slot.cardBg.setImageResource(
                if (isActive) R.drawable.btn_active_glow else R.drawable.btn_inactive
            )
            slot.arrowView.visibility = if (isActive) View.VISIBLE else View.GONE
        }
    }

    private fun launchSidebarApp(app: DashApp) {
        when (app) {
            DashApp.GEMINI -> FloatingWindowLauncher.launchGeminiVoice(this)
            DashApp.YOUTUBE_MUSIC -> FloatingWindowLauncher.launchYouTubeMusicWithAudioGlow(this)
            else -> FloatingWindowLauncher.launchApp(this, app)
        }
    }

    private fun openSettingsScreen() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun openAppDrawer() {
        if (isDrawerOpen) return
        isDrawerOpen = true
        if (allApps.isEmpty()) {
            loadInstalledAppsAsync()
        }
        layoutAppDrawer.animate().cancel()
        val startOffset = (hudOverlayContainer.height.takeIf { it > 0 } ?: 600).toFloat()
        layoutAppDrawer.translationY = startOffset * 0.35f
        layoutAppDrawer.alpha = 0f
        layoutAppDrawer.visibility = View.VISIBLE
        layoutAppDrawer.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(220L)
            .start()
    }

    private fun closeAppDrawer(animate: Boolean = true) {
        if (!isDrawerOpen && layoutAppDrawer.visibility != View.VISIBLE) return
        isDrawerOpen = false
        hideKeyboard()
        layoutAppDrawer.animate().cancel()
        if (!animate) {
            layoutAppDrawer.visibility = View.GONE
            layoutAppDrawer.translationY = 0f
            layoutAppDrawer.alpha = 1f
            return
        }
        layoutAppDrawer.animate()
            .translationY(260f)
            .alpha(0f)
            .setDuration(180L)
            .withEndAction {
                if (!isDrawerOpen) {
                    layoutAppDrawer.visibility = View.GONE
                    layoutAppDrawer.translationY = 0f
                    layoutAppDrawer.alpha = 1f
                }
            }
            .start()
    }

    private fun hideKeyboard() {
        etSearchApps.clearFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etSearchApps.windowToken, 0)
    }

    private fun updateClockAndStatus() {
        val now = Date()
        val timeFmt = SimpleDateFormat(if (prefs.clock24h) "HH:mm" else "hh:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())
        tvClock.text = timeFmt.format(now)
        tvDate.text = dateFmt.format(now)

        val parts = mutableListOf<String>()
        val pct = if (batteryPct >= 0) batteryPct else readBatteryPercent()
        if (pct >= 0) parts += "$pct%"
        if (isBluetoothOn()) parts += "BT"
        if (isWifiConnected()) parts += "WIFI"
        tvHudStatus.text = parts.joinToString("  ")
    }

    private fun readBatteryPercent(): Int {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (_: Exception) {
            -1
        }
    }

    private fun isBluetoothOn(): Boolean {
        return try {
            val bm = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            bm?.adapter?.isEnabled == true
        } catch (_: Exception) {
            false
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: Exception) {
            false
        }
    }

    private fun loadInstalledAppsAsync() {
        appLoaderExecutor.execute {
            val pm = packageManager
            val queryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(queryIntent, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(queryIntent, 0)
            }

            val seenPackages = HashSet<String>()
            val loaded = mutableListOf<DrawerAppItem>()
            for (ri in resolved) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == packageName || !seenPackages.add(pkg)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrEmpty() } ?: pkg
                val icon = ri.loadIcon(pm)
                loaded += DrawerAppItem(label = label, packageName = pkg, icon = icon)
            }
            loaded.sortBy { it.label.lowercase(Locale.getDefault()) }

            mainHandler.post {
                allApps.clear()
                allApps.addAll(loaded)
                filterDrawerApps(etSearchApps.text?.toString().orEmpty())
            }
        }
    }

    private fun filterDrawerApps(query: String) {
        val trimmed = query.trim().lowercase(Locale.getDefault())
        val filtered = if (trimmed.isEmpty()) {
            allApps.toList()
        } else {
            allApps.filter {
                it.label.lowercase(Locale.getDefault()).contains(trimmed) ||
                    it.packageName.lowercase(Locale.getDefault()).contains(trimmed)
            }
        }
        drawerAdapter.submitList(filtered)
    }

    private inner class AppDrawerAdapter : RecyclerView.Adapter<AppDrawerViewHolder>() {
        private var items: List<DrawerAppItem> = emptyList()

        fun submitList(newItems: List<DrawerAppItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppDrawerViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_drawer_app, parent, false)
            return AppDrawerViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppDrawerViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.label.text = item.label
            holder.itemView.setOnClickListener {
                closeAppDrawer(animate = false)
                val launchIntent = packageManager.getLaunchIntentForPackage(item.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        startActivity(launchIntent)
                    } catch (_: Exception) {
                        Toast.makeText(
                            this@MainActivity,
                            "Unable to open ${item.label}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            holder.itemView.setOnLongClickListener {
                closeAppDrawer(animate = false)
                FloatingWindowLauncher.launchPackageInWindow(this@MainActivity, item.packageName)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class AppDrawerViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.ivDrawerIcon)
        val label: TextView = view.findViewById(R.id.tvDrawerLabel)
    }
}
