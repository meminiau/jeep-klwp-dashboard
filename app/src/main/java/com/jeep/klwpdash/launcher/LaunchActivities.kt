package com.jeep.klwpdash.launcher

import android.app.Activity
import android.os.Bundle
import com.jeep.klwpdash.data.DashApp

/**
 * Invisible zero-animation trampoline base activity invoked by KLWP button touch actions.
 */
abstract class BaseLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        executeLaunch()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    protected abstract fun executeLaunch()
}

class LaunchGeminiActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchGeminiVoice(this)
    }
}

class LaunchTorqueActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchApp(this, DashApp.TORQUE)
    }
}

class LaunchJellyfinActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchApp(this, DashApp.JELLYFIN)
    }
}

class LaunchWazeActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchApp(this, DashApp.WAZE)
    }
}

class LaunchYouTubeActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchApp(this, DashApp.YOUTUBE)
    }
}

class LaunchYouTubeMusicActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchYouTubeMusicWithAudioGlow(this)
    }
}

class LaunchAudioGlowActivity : BaseLauncherActivity() {
    override fun executeLaunch() {
        FloatingWindowLauncher.launchAudioGlow(this, asCompanion = false)
    }
}
