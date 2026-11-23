package com.jeep.klwpdash.service

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.jeep.klwpdash.data.DashApp
import com.jeep.klwpdash.data.DashPrefs
import com.jeep.klwpdash.launcher.FloatingWindowLauncher

/**
 * Lightweight AccessibilityService that watches ONLY for YouTube Music entering
 * the foreground so Audio Glow automatically opens whenever YouTube Music is opened —
 * even if launched from outside the KLWP sidebar (e.g. voice command, Floating Apps
 * menu, or steering wheel controls).
 */
class YouTubeMusicMonitorService : AccessibilityService() {

    private var lastObservedPackage: String? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == lastObservedPackage) return

        val previousPkg = lastObservedPackage
        lastObservedPackage = pkg

        val prefs = DashPrefs(this)
        if (!prefs.autoLaunchAudioGlow) return

        val isYouTubeMusic = DashApp.YOUTUBE_MUSIC.candidatePackages.contains(pkg) ||
            pkg == prefs.getCustomPackage(DashApp.YOUTUBE_MUSIC)
        if (!isYouTubeMusic) return

        // Avoid re-triggering if returning directly from Audio Glow or within cooldown window
        val isReturningFromAudioGlow = previousPkg != null &&
            (DashApp.AUDIO_GLOW.candidatePackages.contains(previousPkg) ||
                previousPkg == prefs.getCustomPackage(DashApp.AUDIO_GLOW))
        val elapsed = System.currentTimeMillis() - FloatingWindowLauncher.lastAudioGlowLaunchMs
        if (isReturningFromAudioGlow || elapsed < COOLDOWN_MS) {
            Log.d(TAG, "Skipping Audio Glow auto-launch (cooldown=${elapsed}ms, prev=$previousPkg)")
            return
        }

        Log.i(TAG, "Detected YouTube Music foreground ($pkg); opening Audio Glow visualizer")
        FloatingWindowLauncher.scheduleAudioGlowCompanionLaunch(this, delayMs = 450L)
    }

    override fun onInterrupt() {
        // No-op
    }

    companion object {
        private const val TAG = "YtmAudioGlowMonitor"
        private const val COOLDOWN_MS = 8000L

        fun isEnabled(context: Context): Boolean {
            val expected = ComponentName(context, YouTubeMusicMonitorService::class.java)
                .flattenToString()
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val component = splitter.next()
                if (component.equals(expected, ignoreCase = true) ||
                    component.endsWith("/.${YouTubeMusicMonitorService::class.java.simpleName}")
                ) {
                    return true
                }
            }
            return false
        }
    }
}
