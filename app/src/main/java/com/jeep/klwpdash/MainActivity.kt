package com.jeep.klwpdash

import android.app.Activity
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.jeep.klwpdash.data.AudioGlowLayout
import com.jeep.klwpdash.data.DashApp
import com.jeep.klwpdash.data.DashPrefs
import com.jeep.klwpdash.data.GeminiLaunchMode
import com.jeep.klwpdash.data.WindowMode
import com.jeep.klwpdash.launcher.FloatingWindowLauncher
import com.jeep.klwpdash.service.YouTubeMusicMonitorService
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: DashPrefs
    private var pendingShortcutApp: DashApp? = null

    private lateinit var tvCoreStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var containerAppBindings: LinearLayout
    private lateinit var tvBoundsSummary: TextView
    private lateinit var tvLeftLabel: TextView
    private lateinit var tvTopLabel: TextView
    private lateinit var tvRightLabel: TextView
    private lateinit var tvBottomLabel: TextView
    private lateinit var tvSplitLabel: TextView
    private lateinit var sbLeft: SeekBar
    private lateinit var sbTop: SeekBar
    private lateinit var sbRight: SeekBar
    private lateinit var sbBottom: SeekBar
    private lateinit var sbSplit: SeekBar

    private val shortcutPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val targetApp = pendingShortcutApp
        pendingShortcutApp = null
        if (result.resultCode == Activity.RESULT_OK && targetApp != null && result.data != null) {
            @Suppress("DEPRECATION")
            val shortcutIntent = result.data?.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
            @Suppress("DEPRECATION")
            val shortcutName = result.data?.getStringExtra(Intent.EXTRA_SHORTCUT_NAME)
            if (shortcutIntent != null) {
                val uri = shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)
                prefs.setShortcutUri(targetApp, uri, shortcutName ?: "Floating Apps Shortcut")
                Toast.makeText(
                    this,
                    "Linked ${targetApp.displayName} → ${shortcutName ?: "Floating Apps"}",
                    Toast.LENGTH_SHORT
                ).show()
                refreshStatusUi()
            } else {
                Toast.makeText(this, "No shortcut returned by Floating Apps.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = DashPrefs(this)

        tvCoreStatus = findViewById(R.id.tvCoreStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        containerAppBindings = findViewById(R.id.containerAppBindings)
        tvBoundsSummary = findViewById(R.id.tvBoundsSummary)
        tvLeftLabel = findViewById(R.id.tvLeftLabel)
        tvTopLabel = findViewById(R.id.tvTopLabel)
        tvRightLabel = findViewById(R.id.tvRightLabel)
        tvBottomLabel = findViewById(R.id.tvBottomLabel)
        tvSplitLabel = findViewById(R.id.tvSplitLabel)
        sbLeft = findViewById(R.id.sbLeft)
        sbTop = findViewById(R.id.sbTop)
        sbRight = findViewById(R.id.sbRight)
        sbBottom = findViewById(R.id.sbBottom)
        sbSplit = findViewById(R.id.sbSplit)

        loadPresetPreviewThumbnail()
        setupHeaderActions()
        setupSpinnersAndSwitches()
        setupBoundsControls()
    }

    override fun onResume() {
        super.onResume()
        refreshStatusUi()
    }

    private fun loadPresetPreviewThumbnail() {
        val ivPreview = findViewById<ImageView>(R.id.ivPresetPreview)
        try {
            assets.open(DashApp.PRESET_ASSET_PATH).use { input ->
                ZipInputStream(input).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name == "preset_thumb_portrait.jpg") {
                            val bmp = BitmapFactory.decodeStream(zis)
                            if (bmp != null) {
                                ivPreview.setImageBitmap(bmp)
                            }
                            break
                        }
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (_: Exception) {
            ivPreview.setImageResource(R.mipmap.ic_launcher)
        }
    }

    private fun setupHeaderActions() {
        findViewById<MaterialButton>(R.id.btnApplyKlwp).setOnClickListener {
            openPresetInKlwp()
        }

        findViewById<MaterialButton>(R.id.btnExportKlwp).setOnClickListener {
            exportPresetToDownloads()
        }

        findViewById<MaterialButton>(R.id.btnOpenFloatingApps).setOnClickListener {
            val pkg = FloatingWindowLauncher.findFloatingAppsPackage(this)
            if (pkg != null) {
                packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            } else {
                openPlayStore("com.lwi.android.flapps")
            }
        }

        findViewById<MaterialButton>(R.id.btnDevOptions).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<MaterialButton>(R.id.btnEnableAccessibility).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                Toast.makeText(
                    this,
                    "Enable \"Jeep HUD: YouTube Music → Audio Glow\"",
                    Toast.LENGTH_LONG
                ).show()
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }

        findViewById<MaterialButton>(R.id.btnTestGemini).setOnClickListener {
            FloatingWindowLauncher.launchGeminiVoice(this)
        }

        findViewById<MaterialButton>(R.id.btnAssistantSettings).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Exception) {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
    }

    private fun setupSpinnersAndSwitches() {
        val spWindowMode = findViewById<Spinner>(R.id.spWindowMode)
        val windowModes = WindowMode.entries
        spWindowMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            windowModes.map { it.label }
        )
        spWindowMode.setSelection(windowModes.indexOf(prefs.windowMode).coerceAtLeast(0))
        spWindowMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.windowMode = windowModes[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val swAutoGlow = findViewById<SwitchMaterial>(R.id.swAutoAudioGlow)
        swAutoGlow.isChecked = prefs.autoLaunchAudioGlow
        swAutoGlow.setOnCheckedChangeListener { _, isChecked ->
            prefs.autoLaunchAudioGlow = isChecked
        }

        val spAudioGlowLayout = findViewById<Spinner>(R.id.spAudioGlowLayout)
        val layouts = AudioGlowLayout.entries
        spAudioGlowLayout.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            layouts.map { it.label }
        )
        spAudioGlowLayout.setSelection(layouts.indexOf(prefs.audioGlowLayout).coerceAtLeast(0))
        spAudioGlowLayout.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.audioGlowLayout = layouts[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val spGeminiMode = findViewById<Spinner>(R.id.spGeminiMode)
        val geminiModes = GeminiLaunchMode.entries
        spGeminiMode.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            geminiModes.map { it.label }
        )
        spGeminiMode.setSelection(geminiModes.indexOf(prefs.geminiMode).coerceAtLeast(0))
        spGeminiMode.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                prefs.geminiMode = geminiModes[pos]
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupBoundsControls() {
        fun syncSeekBars() {
            sbLeft.progress = prefs.windowLeftPct
            sbTop.progress = prefs.windowTopPct
            sbRight.progress = prefs.windowRightPct
            sbBottom.progress = prefs.windowBottomPct
            sbSplit.progress = prefs.ytMusicSplitPct
            updateBoundsLabels()
        }

        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                when (seekBar?.id) {
                    R.id.sbLeft -> prefs.windowLeftPct = progress
                    R.id.sbTop -> prefs.windowTopPct = progress
                    R.id.sbRight -> prefs.windowRightPct = progress.coerceAtLeast(prefs.windowLeftPct + 25)
                    R.id.sbBottom -> prefs.windowBottomPct = progress.coerceAtLeast(prefs.windowTopPct + 25)
                    R.id.sbSplit -> prefs.ytMusicSplitPct = progress.coerceIn(30, 75)
                }
                updateBoundsLabels()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        sbLeft.setOnSeekBarChangeListener(listener)
        sbTop.setOnSeekBarChangeListener(listener)
        sbRight.setOnSeekBarChangeListener(listener)
        sbBottom.setOnSeekBarChangeListener(listener)
        sbSplit.setOnSeekBarChangeListener(listener)

        findViewById<MaterialButton>(R.id.btnResetBounds).setOnClickListener {
            prefs.resetBoundsToDefault()
            syncSeekBars()
            Toast.makeText(this, "HUD window bounds reset to defaults.", Toast.LENGTH_SHORT).show()
        }

        syncSeekBars()
    }

    private fun updateBoundsLabels() {
        val rect = FloatingWindowLauncher.computeHudFrameBounds(this, prefs)
        tvBoundsSummary.text =
            "Right HUD frame: (${rect.left}, ${rect.top}) → (${rect.right}, ${rect.bottom}) px"
        tvLeftLabel.text = "Left edge: ${prefs.windowLeftPct}%"
        tvTopLabel.text = "Top edge: ${prefs.windowTopPct}%"
        tvRightLabel.text = "Right edge: ${prefs.windowRightPct}%"
        tvBottomLabel.text = "Bottom edge (above climate bar): ${prefs.windowBottomPct}%"
        tvSplitLabel.text = "YouTube Music / Audio Glow split: ${prefs.ytMusicSplitPct}% / ${100 - prefs.ytMusicSplitPct}%"
    }

    private fun refreshStatusUi() {
        val klwpOk = FloatingWindowLauncher.isPackageInstalled(this, DashApp.KLWP_PACKAGE)
        val flappsPkg = FloatingWindowLauncher.findFloatingAppsPackage(this)
        val audioGlowPkg = FloatingWindowLauncher.findInstalledPackage(this, DashApp.AUDIO_GLOW, prefs)

        tvCoreStatus.text = buildString {
            append(if (klwpOk) "✓ KLWP installed" else "✗ KLWP not installed (required)")
            append("  •  ")
            append(if (flappsPkg != null) "✓ Floating Apps installed" else "⚠ Floating Apps not detected")
            append("\n")
            append(if (audioGlowPkg != null) "✓ Audio Glow installed" else "⚠ Audio Glow not installed yet")
        }

        val accEnabled = YouTubeMusicMonitorService.isEnabled(this)
        tvAccessibilityStatus.text = if (accEnabled) {
            "✓ Background YouTube Music monitor is ACTIVE — Audio Glow will auto-open whenever YouTube Music starts."
        } else {
            "⚠ Optional: Enable the monitor below so Audio Glow also opens if YouTube Music is launched outside the sidebar."
        }
        tvAccessibilityStatus.setTextColor(
            ContextCompat.getColor(this, if (accEnabled) R.color.hud_green else R.color.hud_amber)
        )

        rebuildAppBindingRows()
    }

    private fun rebuildAppBindingRows() {
        containerAppBindings.removeAllViews()
        val inflater = LayoutInflater.from(this)
        val hasFloatingApps = FloatingWindowLauncher.findFloatingAppsPackage(this) != null

        for (app in DashApp.entries) {
            val row = inflater.inflate(R.layout.item_app_binding, containerAppBindings, false)
            val tvName = row.findViewById<TextView>(R.id.tvAppName)
            val tvStatus = row.findViewById<TextView>(R.id.tvAppStatus)
            val btnLink = row.findViewById<MaterialButton>(R.id.btnLinkFloating)
            val btnTest = row.findViewById<MaterialButton>(R.id.btnTestApp)

            val installedPkg = FloatingWindowLauncher.findInstalledPackage(this, app, prefs)
            val linkedUri = prefs.getShortcutUri(app)
            val linkedLabel = prefs.getShortcutLabel(app)

            tvName.text = app.displayName
            tvStatus.text = when {
                linkedUri != null -> "✓ Linked to Floating Apps (${linkedLabel ?: "Shortcut"})"
                installedPkg != null -> "✓ Ready ($installedPkg)"
                else -> "✗ Not installed — tap Test to install"
            }
            tvStatus.setTextColor(
                ContextCompat.getColor(
                    this,
                    when {
                        linkedUri != null -> R.color.hud_cyan
                        installedPkg != null -> R.color.hud_green
                        else -> R.color.hud_red
                    }
                )
            )

            btnLink.text = if (linkedUri != null) "Clear FA" else "Link FA"
            btnLink.setOnClickListener {
                if (linkedUri != null) {
                    prefs.setShortcutUri(app, null)
                    Toast.makeText(this, "Cleared Floating Apps shortcut for ${app.displayName}", Toast.LENGTH_SHORT).show()
                    refreshStatusUi()
                } else if (!hasFloatingApps) {
                    Toast.makeText(this, "Install Floating Apps first to link shortcuts.", Toast.LENGTH_LONG).show()
                    openPlayStore("com.lwi.android.flapps")
                } else {
                    val picker = FloatingWindowLauncher.buildFloatingAppsShortcutPickerIntent(this)
                    if (picker != null) {
                        pendingShortcutApp = app
                        shortcutPickerLauncher.launch(picker)
                    } else {
                        Toast.makeText(this, "Floating Apps shortcut picker unavailable.", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            btnTest.setOnClickListener {
                if (installedPkg == null && linkedUri == null && app != DashApp.GEMINI) {
                    openPlayStore(app.playStoreId)
                } else {
                    when (app) {
                        DashApp.GEMINI -> FloatingWindowLauncher.launchGeminiVoice(this)
                        DashApp.YOUTUBE_MUSIC -> FloatingWindowLauncher.launchYouTubeMusicWithAudioGlow(this)
                        DashApp.AUDIO_GLOW -> FloatingWindowLauncher.launchAudioGlow(this, asCompanion = false)
                        else -> FloatingWindowLauncher.launchApp(this, app)
                    }
                }
            }

            containerAppBindings.addView(row)
        }
    }

    private fun openPresetInKlwp() {
        if (!FloatingWindowLauncher.isPackageInstalled(this, DashApp.KLWP_PACKAGE)) {
            Toast.makeText(this, "KLWP Live Wallpaper is not installed. Opening Google Play...", Toast.LENGTH_LONG).show()
            openPlayStore(DashApp.KLWP_PACKAGE)
            return
        }
        try {
            val kfileUri = Uri.Builder()
                .scheme("kfile")
                .authority("$packageName.kustom.provider")
                .appendPath(DashApp.PRESET_ASSET_PATH)
                .build()
            val intent = Intent().apply {
                component = ComponentName(DashApp.KLWP_PACKAGE, DashApp.KLWP_EDITOR_ACTIVITY)
                data = kfileUri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Open KLWP → Load Preset → Installed → Jeep HUD Dashboard",
                Toast.LENGTH_LONG
            ).show()
            packageManager.getLaunchIntentForPackage(DashApp.KLWP_PACKAGE)?.let { startActivity(it) }
        }
    }

    private fun exportPresetToDownloads() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, DashApp.PRESET_FILE_NAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { out ->
                        assets.open(DashApp.PRESET_ASSET_PATH).use { inp -> inp.copyTo(out) }
                    }
                    Toast.makeText(this, "Exported to Downloads/${DashApp.PRESET_FILE_NAME}", Toast.LENGTH_LONG).show()
                    return
                }
            }
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            val outFile = File(dir, DashApp.PRESET_FILE_NAME)
            FileOutputStream(outFile).use { out ->
                assets.open(DashApp.PRESET_ASSET_PATH).use { inp -> inp.copyTo(out) }
            }
            Toast.makeText(this, "Exported to ${outFile.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPlayStore(pkg: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
        } catch (_: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")))
        }
    }
}
