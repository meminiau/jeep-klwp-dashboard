package com.jeep.klwpdash.data

import android.content.Context
import android.content.SharedPreferences

enum class DashApp(
    val id: String,
    val displayName: String,
    val candidatePackages: List<String>,
    val playStoreId: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Gemini Voice",
        candidatePackages = listOf(
            "com.google.android.apps.bard",
            "com.google.android.googlequicksearchbox"
        ),
        playStoreId = "com.google.android.apps.bard"
    ),
    TORQUE(
        id = "torque",
        displayName = "Torque",
        candidatePackages = listOf(
            "org.prowl.torque",
            "org.prowl.torquefree"
        ),
        playStoreId = "org.prowl.torque"
    ),
    JELLYFIN(
        id = "jellyfin",
        displayName = "Jellyfin",
        candidatePackages = listOf(
            "org.jellyfin.mobile",
            "org.jellyfin.androidtv"
        ),
        playStoreId = "org.jellyfin.mobile"
    ),
    WAZE(
        id = "waze",
        displayName = "Waze",
        candidatePackages = listOf("com.waze"),
        playStoreId = "com.waze"
    ),
    YOUTUBE(
        id = "youtube",
        displayName = "YouTube",
        candidatePackages = listOf(
            "com.google.android.youtube",
            "app.revanced.android.youtube",
            "com.vanced.android.youtube"
        ),
        playStoreId = "com.google.android.youtube"
    ),
    YOUTUBE_MUSIC(
        id = "ytmusic",
        displayName = "YouTube Music",
        candidatePackages = listOf(
            "com.google.android.apps.youtube.music",
            "app.revanced.android.apps.youtube.music"
        ),
        playStoreId = "com.google.android.apps.youtube.music"
    ),
    AUDIO_GLOW(
        id = "audioglow",
        displayName = "Audio Glow",
        candidatePackages = listOf(
            "com.cyphercove.audioglowfs",
            "com.cyphercove.audioglow"
        ),
        playStoreId = "com.cyphercove.audioglowfs"
    );

    companion object {
        val FLOATING_APPS_PACKAGES = listOf(
            "com.lwi.android.flappsfull",
            "com.lwi.android.flapps"
        )
        const val KLWP_PACKAGE = "org.kustom.wallpaper"
        const val KLWP_EDITOR_ACTIVITY = "org.kustom.lib.editor.WpAdvancedEditorActivity"
        const val PRESET_ASSET_PATH = "wallpapers/Jeep_HUD_Dashboard.klwp"
        const val PRESET_FILE_NAME = "Jeep_HUD_Dashboard.klwp"

        fun fromId(id: String?): DashApp? = entries.firstOrNull { it.id == id }
    }
}

enum class WindowMode(val id: String, val label: String) {
    FLOATING_OR_FREEFORM("floating_or_freeform", "Floating Apps Shortcut / Freeform Window (Recommended)"),
    FREEFORM_ONLY("freeform_only", "Native Android Freeform Window (HUD Frame)"),
    STANDARD("standard", "Standard Fullscreen Launch");

    companion object {
        fun fromId(id: String?): WindowMode =
            entries.firstOrNull { it.id == id } ?: FLOATING_OR_FREEFORM
    }
}

enum class GeminiLaunchMode(val id: String, val label: String) {
    VOICE_COMMAND("voice_command", "Voice Conversation (ACTION_VOICE_COMMAND)"),
    ASSIST_OVERLAY("assist_overlay", "Digital Assistant Overlay (ACTION_ASSIST)"),
    GEMINI_APP("gemini_app", "Open Gemini App Directly");

    companion object {
        fun fromId(id: String?): GeminiLaunchMode =
            entries.firstOrNull { it.id == id } ?: VOICE_COMMAND
    }
}

enum class AudioGlowLayout(val id: String, val label: String) {
    SPLIT_BOTTOM("split_bottom", "Split Inside HUD Frame (YT Music Top / Audio Glow Bottom)"),
    FULL_HUD_WINDOW("full_hud", "Audio Glow Full HUD Window (Foreground)");

    companion object {
        fun fromId(id: String?): AudioGlowLayout =
            entries.firstOrNull { it.id == id } ?: SPLIT_BOTTOM
    }
}

class DashPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // HUD right-hand frame bounds as percentage (0..100) of screen width/height.
    // Tuned to match the chamfered cyan right-hand frame of Jeep_HUD_Dashboard.klwp.
    var windowLeftPct: Int
        get() = prefs.getInt(KEY_WIN_LEFT, 27)
        set(value) = prefs.edit().putInt(KEY_WIN_LEFT, value.coerceIn(0, 70)).apply()

    var windowTopPct: Int
        get() = prefs.getInt(KEY_WIN_TOP, 21)
        set(value) = prefs.edit().putInt(KEY_WIN_TOP, value.coerceIn(0, 60)).apply()

    var windowRightPct: Int
        get() = prefs.getInt(KEY_WIN_RIGHT, 95)
        set(value) = prefs.edit().putInt(KEY_WIN_RIGHT, value.coerceIn(30, 100)).apply()

    var windowBottomPct: Int
        get() = prefs.getInt(KEY_WIN_BOTTOM, 91)
        set(value) = prefs.edit().putInt(KEY_WIN_BOTTOM, value.coerceIn(40, 100)).apply()

    var ytMusicSplitPct: Int
        get() = prefs.getInt(KEY_YTM_SPLIT, 55)
        set(value) = prefs.edit().putInt(KEY_YTM_SPLIT, value.coerceIn(30, 75)).apply()

    var windowMode: WindowMode
        get() = WindowMode.fromId(prefs.getString(KEY_WINDOW_MODE, null))
        set(value) = prefs.edit().putString(KEY_WINDOW_MODE, value.id).apply()

    var geminiMode: GeminiLaunchMode
        get() = GeminiLaunchMode.fromId(prefs.getString(KEY_GEMINI_MODE, null))
        set(value) = prefs.edit().putString(KEY_GEMINI_MODE, value.id).apply()

    var autoLaunchAudioGlow: Boolean
        get() = prefs.getBoolean(KEY_AUTO_AUDIO_GLOW, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_AUDIO_GLOW, value).apply()

    var audioGlowLayout: AudioGlowLayout
        get() = AudioGlowLayout.fromId(prefs.getString(KEY_AUDIO_GLOW_LAYOUT, null))
        set(value) = prefs.edit().putString(KEY_AUDIO_GLOW_LAYOUT, value.id).apply()

    fun getShortcutUri(app: DashApp): String? =
        prefs.getString("$KEY_SHORTCUT_PREFIX${app.id}", null)?.takeIf { it.isNotBlank() }

    fun setShortcutUri(app: DashApp, intentUri: String?, label: String? = null) {
        val editor = prefs.edit()
        if (intentUri.isNullOrBlank()) {
            editor.remove("$KEY_SHORTCUT_PREFIX${app.id}")
            editor.remove("$KEY_SHORTCUT_LABEL_PREFIX${app.id}")
        } else {
            editor.putString("$KEY_SHORTCUT_PREFIX${app.id}", intentUri)
            if (!label.isNullOrBlank()) {
                editor.putString("$KEY_SHORTCUT_LABEL_PREFIX${app.id}", label)
            }
        }
        editor.apply()
    }

    fun getShortcutLabel(app: DashApp): String? =
        prefs.getString("$KEY_SHORTCUT_LABEL_PREFIX${app.id}", null)

    fun getCustomPackage(app: DashApp): String? =
        prefs.getString("$KEY_CUSTOM_PKG_PREFIX${app.id}", null)?.takeIf { it.isNotBlank() }

    fun setCustomPackage(app: DashApp, pkg: String?) {
        val editor = prefs.edit()
        if (pkg.isNullOrBlank()) {
            editor.remove("$KEY_CUSTOM_PKG_PREFIX${app.id}")
        } else {
            editor.putString("$KEY_CUSTOM_PKG_PREFIX${app.id}", pkg.trim())
        }
        editor.apply()
    }

    fun resetBoundsToDefault() {
        windowLeftPct = 27
        windowTopPct = 21
        windowRightPct = 95
        windowBottomPct = 91
        ytMusicSplitPct = 55
    }

    companion object {
        private const val PREFS_NAME = "jeep_klwp_dash_prefs"
        private const val KEY_WIN_LEFT = "win_left_pct"
        private const val KEY_WIN_TOP = "win_top_pct"
        private const val KEY_WIN_RIGHT = "win_right_pct"
        private const val KEY_WIN_BOTTOM = "win_bottom_pct"
        private const val KEY_YTM_SPLIT = "ytm_split_pct"
        private const val KEY_WINDOW_MODE = "window_mode"
        private const val KEY_GEMINI_MODE = "gemini_mode"
        private const val KEY_AUTO_AUDIO_GLOW = "auto_audio_glow"
        private const val KEY_AUDIO_GLOW_LAYOUT = "audio_glow_layout"
        private const val KEY_SHORTCUT_PREFIX = "shortcut_uri_"
        private const val KEY_SHORTCUT_LABEL_PREFIX = "shortcut_label_"
        private const val KEY_CUSTOM_PKG_PREFIX = "custom_pkg_"
    }
}
