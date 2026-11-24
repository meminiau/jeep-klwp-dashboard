# Jeep HUD Launcher

![Jeep HUD Dashboard Preview — Idle & Active States](klwp-preset/preview_inspect.png)

A standalone, full-screen **Android Home Launcher APK** for portrait Jeep Android head units (e.g., UNISOC UIS7865 Tesla-style stereos) — no KLWP or Live Wallpaper setup required.

## Features

1. **Standalone Android Home Launcher (`CATEGORY_HOME` + `CATEGORY_DEFAULT`)**
   - Uses `singleTask`, `stateNotNeeded="true"`, and `clearTaskOnLaunch="true"` so pressing the stereo's **Home** button always returns cleanly to the Jeep HUD.
   - Renders the full-screen Jeep Cyber HUD directly with the bottom climate control panel removed, live **Clock** (`12h`/`24h`), live **Date**, and top-left **Battery / Bluetooth / Wi-Fi** status.
   - Discreet top-right **Settings Gear** icon opens `SettingsActivity` for quick setup and calibration.

2. **6 Interactive Sidebar Buttons with Dynamic Cyan Highlight**
   - **Gemini** *(above Torque)* — opens Google Gemini Voice Conversation (`ACTION_VOICE_COMMAND` / `ACTION_ASSIST` / Gemini app / linked shortcut)
   - **Torque** — opens Torque Pro / Lite in a window
   - **Jellyfin** — opens Jellyfin in a window
   - **Waze** — opens Waze in a window
   - **YouTube** — opens YouTube in a window
   - **YouTube Music** — opens YouTube Music in a window **and** automatically opens **Audio Glow Music Visualizer**

3. **Built-In Swipe-Up App Drawer**
   - Swipe upward from anywhere on the HUD (or tap **▲ APPS** at the bottom) to open the searchable grid of all installed Android apps (Settings, Termux, File Manager, etc.).
   - **Tap** an app to open normally, or **long-press** to open inside the right-hand HUD window.

4. **Floating Apps (`com.lwi.android.flapps`) & Audio Glow Integration**
   - Bound **Floating Apps (`Link FA`)** shortcuts launch first, falling back smoothly to calibrated Android Freeform windows (`WINDOWING_MODE_FREEFORM`) aligned inside the right-hand cyan HUD frame.
   - Tapping **YouTube Music** opens YouTube Music and **Audio Glow Music Visualizer** (`com.cyphercove.audioglowfs`) together, and the background **YouTube Music Monitor (`AccessibilityService`)** also auto-opens Audio Glow whenever YouTube Music starts from anywhere on the stereo.

## Installation & Stereo Setup

1. Install **`Jeep-HUD-Launcher-v2.0.0.apk`** from the [Releases](../../releases) page.
2. Press the stereo's **Home** button (or tap the top-right **Gear** icon → **SET AS DEFAULT HOME LAUNCHER**) and select **Jeep HUD Launcher** as Always.
3. Open **Settings** (top-right gear icon):
   - Tap **Link FA** beside any app to bind a **Floating Apps** shortcut directly (or enable Freeform Mode in Floating Apps & Developer Options).
   - Tap **Enable Background YouTube Music Monitor** in Accessibility Settings so Audio Glow always launches whenever YouTube Music opens.
   - Tap **Default Assistant** and confirm **Google Gemini** is selected.

## Project Structure

- `app/src/main/java/com/jeep/klwpdash/MainActivity.kt` — Live Jeep HUD Home Launcher + swipe-up App Drawer
- `app/src/main/java/com/jeep/klwpdash/SettingsActivity.kt` — Floating Apps (`Link FA`), Accessibility Monitor, Gemini, and HUD window calibration UI
- `app/src/main/java/com/jeep/klwpdash/launcher/FloatingWindowLauncher.kt` — Floating Apps shortcut + Android Freeform window launcher + Audio Glow & Gemini handlers
- `app/src/main/java/com/jeep/klwpdash/service/YouTubeMusicMonitorService.kt` — Accessibility monitor for automatic Audio Glow launching
- `app/src/main/res/drawable-nodpi/` — Cleaned Jeep HUD artwork, glow cards, rail pointer, and sidebar icons
- `.github/workflows/build-apk.yml` — Automated signed APK build & GitHub Release workflow
