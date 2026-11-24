package com.jeep.klwpdash.launcher

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import com.jeep.klwpdash.data.AudioGlowLayout
import com.jeep.klwpdash.data.DashApp
import com.jeep.klwpdash.data.DashPrefs
import com.jeep.klwpdash.data.GeminiLaunchMode
import com.jeep.klwpdash.data.WindowMode

/**
 * Handles launching apps inside Floating Apps (LWi s.r.o.) / Android Freeform windows
 * aligned to the right-hand Jeep HUD window frame, as well as Gemini Voice Conversation
 * and automatic Audio Glow companion launches alongside YouTube Music.
 */
object FloatingWindowLauncher {
    private const val TAG = "FloatingWindowLauncher"
    private const val WINDOWING_MODE_FREEFORM = 5
    private const val FREEFORM_WORKSPACE_STACK_ID = 2
    private const val AUDIO_GLOW_LAUNCH_DELAY_MS = 600L

    @Volatile
    var lastAudioGlowLaunchMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    fun findInstalledPackage(context: Context, app: DashApp, prefs: DashPrefs = DashPrefs(context)): String? {
        val custom = prefs.getCustomPackage(app)
        if (!custom.isNullOrBlank() && isPackageInstalled(context, custom)) {
            return custom
        }
        return app.candidatePackages.firstOrNull { isPackageInstalled(context, it) }
    }

    fun findFloatingAppsPackage(context: Context): String? {
        return DashApp.FLOATING_APPS_PACKAGES.firstOrNull { isPackageInstalled(context, it) }
    }

    /**
     * Builds an Intent that opens Floating Apps' native shortcut creation picker
     * (`QLFAShortcut`), allowing the user to bind any normal app or floating mini-app
     * directly to a dashboard button.
     */
    fun buildFloatingAppsShortcutPickerIntent(context: Context): Intent? {
        val flappsPkg = findFloatingAppsPackage(context) ?: return null
        val explicit = Intent(Intent.ACTION_CREATE_SHORTCUT).apply {
            component = ComponentName(flappsPkg, "com.lwi.android.flapps.activities.QLFAShortcut")
        }
        if (explicit.resolveActivity(context.packageManager) != null) {
            return explicit
        }
        // Fallback: query any CREATE_SHORTCUT activity exposed by Floating Apps
        val generic = Intent(Intent.ACTION_CREATE_SHORTCUT).setPackage(flappsPkg)
        if (generic.resolveActivity(context.packageManager) != null) {
            return generic
        }
        return null
    }

    /**
     * Computes screen-space pixel bounds for the right-hand HUD window frame.
     */
    fun computeHudFrameBounds(context: Context, prefs: DashPrefs = DashPrefs(context)): Rect {
        val (screenW, screenH) = getScreenSize(context)
        val left = (screenW * prefs.windowLeftPct / 100f).toInt()
        val top = (screenH * prefs.windowTopPct / 100f).toInt()
        val right = (screenW * prefs.windowRightPct / 100f).toInt()
        val bottom = (screenH * prefs.windowBottomPct / 100f).toInt()
        return Rect(
            left.coerceAtLeast(0),
            top.coerceAtLeast(0),
            right.coerceAtLeast(left + 200),
            bottom.coerceAtLeast(top + 200)
        )
    }

    /**
     * Splits the HUD frame vertically into upper (YouTube Music) and lower (Audio Glow) windows.
     */
    fun computeYouTubeMusicSplitBounds(
        context: Context,
        prefs: DashPrefs = DashPrefs(context)
    ): Pair<Rect, Rect> {
        val frame = computeHudFrameBounds(context, prefs)
        val gapPx = (frame.height() * 0.015f).toInt().coerceAtLeast(8)
        val splitY = frame.top + (frame.height() * prefs.ytMusicSplitPct / 100f).toInt()
        val topRect = Rect(frame.left, frame.top, frame.right, (splitY - gapPx / 2).coerceAtLeast(frame.top + 150))
        val bottomRect = Rect(frame.left, (splitY + gapPx / 2).coerceAtMost(frame.bottom - 150), frame.right, frame.bottom)
        return topRect to bottomRect
    }

    private fun getScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val pt = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(pt)
            pt.x to pt.y
        }
    }

    /**
     * Builds an ActivityOptions bundle requesting Android Freeform windowing mode
     * pinned to [bounds]. Works alongside Floating Apps 4.29+ Free-Form Mode.
     */
    @SuppressLint("DiscouragedPrivateApi", "PrivateApi")
    private fun buildFreeformOptionsBundle(bounds: Rect): Bundle {
        val options = ActivityOptions.makeBasic()
        options.launchBounds = bounds
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val method = ActivityOptions::class.java.getMethod(
                    "setLaunchWindowingMode",
                    Int::class.javaPrimitiveType
                )
                method.invoke(options, WINDOWING_MODE_FREEFORM)
            } else {
                val method = ActivityOptions::class.java.getMethod(
                    "setLaunchStackId",
                    Int::class.javaPrimitiveType
                )
                method.invoke(options, FREEFORM_WORKSPACE_STACK_ID)
            }
        } catch (t: Throwable) {
            Log.d(TAG, "setLaunchWindowingMode unavailable on this Android build: ${t.message}")
        }
        return options.toBundle()
    }

    /**
     * Primary entry point for launching a standard dashboard app in a floating/freeform window.
     */
    fun launchApp(
        context: Context,
        app: DashApp,
        explicitBounds: Rect? = null,
        multipleTask: Boolean = false
    ): Boolean {
        val appContext = context.applicationContext
        val prefs = DashPrefs(appContext)
        val mode = prefs.windowMode

        // 1) If user linked a Floating Apps shortcut for this app and Floating Apps mode is enabled, fire it!
        if (mode == WindowMode.FLOATING_OR_FREEFORM) {
            val savedShortcutUri = prefs.getShortcutUri(app)
            if (!savedShortcutUri.isNullOrBlank()) {
                if (launchSavedShortcut(appContext, savedShortcutUri, explicitBounds)) {
                    return true
                }
            }
        }

        // 2) Resolve installed target package
        val pkg = findInstalledPackage(appContext, app, prefs)
        if (pkg == null) {
            showToast(appContext, "${app.displayName} is not installed on this stereo.")
            return false
        }

        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent == null) {
            showToast(appContext, "Cannot launch ${app.displayName} ($pkg).")
            return false
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (mode != WindowMode.STANDARD) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            if (multipleTask) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
            }
        }

        // 3) Launch with Freeform Window Bounds if windowed mode is active
        if (mode != WindowMode.STANDARD) {
            val bounds = explicitBounds ?: computeHudFrameBounds(appContext, prefs)
            try {
                val bundle = buildFreeformOptionsBundle(bounds)
                appContext.startActivity(launchIntent, bundle)
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Freeform launch failed for $pkg, falling back to standard launch", e)
            }
        }

        // 4) Graceful fallback: standard launch
        return try {
            appContext.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $pkg", e)
            showToast(appContext, "Failed to launch ${app.displayName}")
            false
        }
    }

    /**
     * Launches any installed Android package inside the calibrated right-hand HUD window
     * (used when long-pressing any app in the built-in App Drawer).
     */
    fun launchPackageInWindow(context: Context, packageName: String): Boolean {
        val appContext = context.applicationContext
        val prefs = DashPrefs(appContext)
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            showToast(appContext, "Cannot launch $packageName.")
            return false
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (prefs.windowMode != WindowMode.STANDARD) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT)
            val bounds = computeHudFrameBounds(appContext, prefs)
            try {
                appContext.startActivity(launchIntent, buildFreeformOptionsBundle(bounds))
                return true
            } catch (e: Exception) {
                Log.w(TAG, "Freeform launch failed for $packageName, falling back to standard launch", e)
            }
        }
        return try {
            appContext.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName", e)
            showToast(appContext, "Failed to launch $packageName.")
            false
        }
    }

    private fun launchSavedShortcut(context: Context, intentUri: String, bounds: Rect?): Boolean {
        return try {
            val shortcutIntent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val targetPkg = shortcutIntent.component?.packageName ?: shortcutIntent.`package`
            val isFloatingApps = targetPkg != null && DashApp.FLOATING_APPS_PACKAGES.contains(targetPkg)
            if (isFloatingApps || bounds == null) {
                context.startActivity(shortcutIntent)
            } else {
                context.startActivity(shortcutIntent, buildFreeformOptionsBundle(bounds))
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Saved shortcut failed: $intentUri", e)
            false
        }
    }

    /**
     * Launches YouTube Music in a window AND automatically launches Audio Glow
     * Music Visualizer so reactive graphics animate with the music.
     */
    fun launchYouTubeMusicWithAudioGlow(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = DashPrefs(appContext)
        val shouldOpenAudioGlow = prefs.autoLaunchAudioGlow

        val launchedYtm = if (shouldOpenAudioGlow && prefs.audioGlowLayout == AudioGlowLayout.SPLIT_BOTTOM) {
            val (ytmRect, _) = computeYouTubeMusicSplitBounds(appContext, prefs)
            launchApp(appContext, DashApp.YOUTUBE_MUSIC, explicitBounds = ytmRect, multipleTask = false)
        } else {
            launchApp(appContext, DashApp.YOUTUBE_MUSIC)
        }

        if (shouldOpenAudioGlow) {
            scheduleAudioGlowCompanionLaunch(appContext)
        }
        return launchedYtm
    }

    /**
     * Opens Audio Glow in its configured HUD window (used both when tapping the YT Music
     * button and when YouTubeMusicMonitorService detects YouTube Music opening anywhere).
     */
    fun scheduleAudioGlowCompanionLaunch(context: Context, delayMs: Long = AUDIO_GLOW_LAUNCH_DELAY_MS) {
        val appContext = context.applicationContext
        lastAudioGlowLaunchMs = System.currentTimeMillis()
        mainHandler.postDelayed({
            launchAudioGlow(appContext, asCompanion = true)
        }, delayMs)
    }

    fun launchAudioGlow(context: Context, asCompanion: Boolean = false): Boolean {
        val appContext = context.applicationContext
        val prefs = DashPrefs(appContext)
        lastAudioGlowLaunchMs = System.currentTimeMillis()

        val bounds = when (prefs.audioGlowLayout) {
            AudioGlowLayout.SPLIT_BOTTOM -> computeYouTubeMusicSplitBounds(appContext, prefs).second
            AudioGlowLayout.FULL_HUD_WINDOW -> computeHudFrameBounds(appContext, prefs)
        }

        val launched = launchApp(
            context = appContext,
            app = DashApp.AUDIO_GLOW,
            explicitBounds = bounds,
            multipleTask = asCompanion
        )
        if (!launched && !asCompanion) {
            showToast(appContext, "Install Audio Glow Music Visualizer from Google Play.")
        }
        return launched
    }

    /**
     * Launches Gemini Voice Conversation using the most reliable chain available on Android
     * head units, honoring user-configured mode and optional linked shortcut.
     */
    fun launchGeminiVoice(context: Context): Boolean {
        val appContext = context.applicationContext
        val prefs = DashPrefs(appContext)

        // 1) Respect custom linked shortcut if user configured one
        val savedShortcut = prefs.getShortcutUri(DashApp.GEMINI)
        if (!savedShortcut.isNullOrBlank()) {
            if (launchSavedShortcut(appContext, savedShortcut, null)) {
                return true
            }
        }

        val candidates = mutableListOf<Intent>()
        when (prefs.geminiMode) {
            GeminiLaunchMode.VOICE_COMMAND -> {
                candidates += Intent(Intent.ACTION_VOICE_COMMAND)
                candidates += Intent(Intent.ACTION_ASSIST)
                candidates += Intent("android.intent.action.VOICE_ASSIST")
                buildGeminiDirectIntent(appContext)?.let { candidates += it }
                candidates += Intent(RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE)
            }
            GeminiLaunchMode.ASSIST_OVERLAY -> {
                candidates += Intent(Intent.ACTION_ASSIST)
                candidates += Intent(Intent.ACTION_VOICE_COMMAND)
                candidates += Intent("android.intent.action.VOICE_ASSIST")
                buildGeminiDirectIntent(appContext)?.let { candidates += it }
            }
            GeminiLaunchMode.GEMINI_APP -> {
                buildGeminiDirectIntent(appContext)?.let { candidates += it }
                candidates += Intent(Intent.ACTION_VOICE_COMMAND)
                candidates += Intent(Intent.ACTION_ASSIST)
            }
        }

        for (intent in candidates) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                appContext.startActivity(intent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Try next fallback
            } catch (e: Exception) {
                Log.w(TAG, "Gemini launch candidate failed: ${intent.action}", e)
            }
        }

        showToast(appContext, "Please install Google Gemini and set it as default digital assistant.")
        return false
    }

    private fun buildGeminiDirectIntent(context: Context): Intent? {
        val bardPkg = "com.google.android.apps.bard"
        if (isPackageInstalled(context, bardPkg)) {
            context.packageManager.getLaunchIntentForPackage(bardPkg)?.let { return it }
            return Intent(Intent.ACTION_MAIN).apply {
                component = ComponentName(
                    bardPkg,
                    "com.google.android.apps.bard.shellapp.BardEntryPointActivity"
                )
            }
        }
        val googlePkg = "com.google.android.googlequicksearchbox"
        if (isPackageInstalled(context, googlePkg)) {
            return Intent(Intent.ACTION_VOICE_COMMAND).setPackage(googlePkg)
        }
        return null
    }

    private fun showToast(context: Context, message: String) {
        mainHandler.post {
            Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}
