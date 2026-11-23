$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repoRoot = Split-Path -Parent $PSScriptRoot
$presetWorkDir = Join-Path $repoRoot 'klwp-preset'
$bitmapsDir = Join-Path $presetWorkDir 'bitmaps'
$assetsWallDir = Join-Path $repoRoot 'app\src\main\assets\wallpapers'
$klwpOutPath = Join-Path $assetsWallDir 'Jeep_HUD_Dashboard.klwp'

# 6 Sidebar Buttons (Gemini placed ABOVE Torque as requested!)
# Base KLWP canvas: width=720, height=1290 (matches clean background 768x1376 -> scale 0.9375)
# Responsive scaling adapts automatically to any portrait Jeep head unit ratio (9:16, 10:16, 3:4).
$S = 'mu(min,1,si(rheight)/1290)'

$buttons = @(
    @{
        id       = 'gemini'
        label    = 'Gemini'
        icon     = 'icon_gemini.png'
        activity = 'com.jeep.klwpdash/com.jeep.klwpdash.launcher.LaunchGeminiActivity'
        y        = 216
        yFrac    = 0.1674
        twoLine  = $false
    },
    @{
        id       = 'torque'
        label    = 'Torque'
        icon     = 'icon_torque.png'
        activity = 'com.jeep.klwpdash/com.jeep.klwpdash.launcher.LaunchTorqueActivity'
        y        = 381
        yFrac    = 0.2953
        twoLine  = $false
    },
    @{
        id       = 'jellyfin'
        label    = 'Jellyfin'
        icon     = 'icon_jellyfin.png'
        activity = 'com.jeep.klwpdash/com.jeep.klwpdash.launcher.LaunchJellyfinActivity'
        y        = 546
        yFrac    = 0.4233
        twoLine  = $false
    },
    @{
        id       = 'waze'
        label    = 'Waze'
        icon     = 'icon_waze.png'
        activity = 'com.jeep.klwpdash/com.jeep.klwpdash.launcher.LaunchWazeActivity'
        y        = 711
        yFrac    = 0.5512
        twoLine  = $false
    },
    @{
        id       = 'youtube'
        label    = 'YouTube'
        icon     = 'icon_youtube.png'
        activity = 'com.jeep.klwpdash/com.jeep.klwpdash.launcher.LaunchYouTubeActivity'
        y        = 876
        yFrac    = 0.6791
        twoLine  = $false
    },
    @{
        id       = 'ytmusic'
        label    = "YouTube`nMusic"
        icon     = 'icon_ytmusic.png'
        activity = 'com.jeep.klwpdash/com.jeep.klwpdash.launcher.LaunchYouTubeMusicActivity'
        y        = 1041
        yFrac    = 0.8070
        twoLine  = $true
    }
)

$viewItems = New-Object System.Collections.ArrayList

# 1. Top HUD Live Clock & Date (tap opens Jeep HUD Companion Settings)
$topHudGroup = [ordered]@{
    internal_type     = 'OverlapLayerModule'
    internal_title    = 'Top HUD Clock & Date'
    position_anchor   = 'TOP'
    position_offset_x = 0
    position_offset_y = 38
    internal_toggles  = [ordered]@{
        position_offset_y = 10
    }
    internal_formulas = [ordered]@{
        position_offset_y = "`$si(rheight)*0.0295`$"
    }
    internal_events   = @(
        [ordered]@{
            type   = 'SINGLE_TAP'
            action = 'LAUNCH_ACTIVITY'
            intent = 'intent:#Intent;component=com.jeep.klwpdash/com.jeep.klwpdash.MainActivity;end'
        }
    )
    viewgroup_items   = @(
        [ordered]@{
            internal_type   = 'ShapeModule'
            internal_title  = 'Settings Hit Area'
            position_anchor = 'TOP'
            shape_type      = 'RECT'
            shape_width     = 300
            shape_height    = 150
            paint_color     = '#00000000'
        },
        [ordered]@{
            internal_type     = 'TextModule'
            internal_title    = 'Live Clock'
            position_anchor   = 'TOP'
            text_expression   = '$if(gv(clock_24h)=1, df(HH:mm), df(hh:mm))$'
            text_size         = 52
            text_align        = 'CENTER'
            paint_color       = '#FFEAF7FF'
            internal_toggles  = [ordered]@{
                paint_color = 100
                text_size   = 10
            }
            internal_globals  = [ordered]@{
                paint_color = 'accent_color'
            }
            internal_formulas = [ordered]@{
                text_size = "`$52*$S`$"
            }
        },
        [ordered]@{
            internal_type        = 'TextModule'
            internal_title       = 'Live Date'
            position_anchor      = 'TOP'
            position_padding_top = 60
            text_expression      = '$df(EEEE, d MMM)$'
            text_size            = 19
            text_align           = 'CENTER'
            paint_color          = '#FFC7E5F5'
            internal_toggles     = [ordered]@{
                position_padding_top = 10
                text_size            = 10
            }
            internal_formulas    = [ordered]@{
                position_padding_top = "`$60*$S`$"
                text_size            = "`$19*$S`$"
            }
        }
    )
}
[void]$viewItems.Add($topHudGroup)

# 2. Subtle Top-Right Live Status Readout (Battery / BT / Wi-Fi)
$statusGroup = [ordered]@{
    internal_type     = 'OverlapLayerModule'
    internal_title    = 'Top Right Status'
    position_anchor   = 'TOPRIGHT'
    position_offset_x = 18
    position_offset_y = 6
    viewgroup_items   = @(
        [ordered]@{
            internal_type   = 'TextModule'
            internal_title  = 'Live Status'
            position_anchor = 'TOPRIGHT'
            text_expression = '$bi(level)$%$if(nc(bt)>0, "  BT", "")$$if(nc(wifi)=CONNECTED, "  WIFI", "")$'
            text_size       = 15
            paint_color     = '#B8B8E4F7'
        }
    )
}
[void]$viewItems.Add($statusGroup)

# 3. Empty Right-Hand Window Frame Tap Target (clears active highlight when tapped directly)
$clearFrameHit = [ordered]@{
    internal_type     = 'ShapeModule'
    internal_title    = 'Right Frame Reset Highlight'
    position_anchor   = 'TOPLEFT'
    position_offset_x = 195
    position_offset_y = 264
    shape_type        = 'RECT'
    shape_width       = 495
    shape_height      = 910
    paint_color       = '#00000000'
    internal_toggles  = [ordered]@{
        position_offset_y = 10
        shape_height      = 10
    }
    internal_formulas = [ordered]@{
        position_offset_y = '$si(rheight)*0.205$'
        shape_height      = '$si(rheight)*0.705$'
    }
    internal_events   = @(
        [ordered]@{
            type        = 'SINGLE_TAP'
            action      = 'SWITCH_GLOBAL'
            switch      = 'active_app'
            switch_text = 'none'
        }
    )
}
[void]$viewItems.Add($clearFrameHit)

# 4. Six Interactive Left Sidebar Buttons + Paired Active Cyan Rail Pointers
foreach ($btn in $buttons) {
    $id = $btn.id
    $label = $btn.label
    $icon = $btn.icon
    $act = $btn.activity
    $yPos = $btn.y
    $yFrac = $btn.yFrac
    $twoLine = $btn.twoLine

    $iconWidth = if ($twoLine) { 74 } else { 80 }
    $iconTopPad = if ($twoLine) { 22 } else { 28 }
    $lblBotPad = if ($twoLine) { 18 } else { 24 }
    $lblSize = if ($twoLine) { 17 } else { 20 }

    $btnGroup = [ordered]@{
        internal_type     = 'OverlapLayerModule'
        internal_title    = "Button: $($label -replace "`n",' ')"
        position_anchor   = 'TOPLEFT'
        position_offset_x = 0
        position_offset_y = $yPos
        internal_toggles  = [ordered]@{
            position_offset_y = 10
        }
        internal_formulas = [ordered]@{
            position_offset_y = "`$si(rheight)*$yFrac`$"
        }
        internal_events   = @(
            [ordered]@{
                type        = 'SINGLE_TAP'
                action      = 'SWITCH_GLOBAL'
                switch      = 'active_app'
                switch_text = $id
            },
            [ordered]@{
                type   = 'SINGLE_TAP'
                action = 'LAUNCH_ACTIVITY'
                intent = "intent:#Intent;component=$act;end"
            }
        )
        viewgroup_items   = @(
            [ordered]@{
                internal_type     = 'BitmapModule'
                internal_title    = 'Inactive Card'
                position_anchor   = 'CENTER'
                bitmap_bitmap     = 'kfile://org.kustom.provider/bitmaps/btn_inactive.png'
                bitmap_width      = 148
                bitmap_alpha      = 100
                internal_toggles  = [ordered]@{
                    bitmap_width = 10
                    bitmap_alpha = 10
                }
                internal_formulas = [ordered]@{
                    bitmap_width = "`$148*$S`$"
                    bitmap_alpha = "`$if(gv(active_app)=$id, 0, 100)`$"
                }
            },
            [ordered]@{
                internal_type     = 'BitmapModule'
                internal_title    = 'Active Glow Card'
                position_anchor   = 'CENTER'
                bitmap_bitmap     = 'kfile://org.kustom.provider/bitmaps/btn_active_glow.png'
                bitmap_width      = 148
                bitmap_alpha      = 0
                internal_toggles  = [ordered]@{
                    bitmap_width = 10
                    bitmap_alpha = 10
                }
                internal_formulas = [ordered]@{
                    bitmap_width = "`$148*$S`$"
                    bitmap_alpha = "`$if(gv(active_app)=$id, 100, 0)`$"
                }
            },
            [ordered]@{
                internal_type        = 'BitmapModule'
                internal_title       = 'App Icon'
                position_anchor      = 'TOP'
                position_padding_top = $iconTopPad
                bitmap_bitmap        = "kfile://org.kustom.provider/bitmaps/$icon"
                bitmap_width         = $iconWidth
                bitmap_alpha         = 100
                internal_toggles     = [ordered]@{
                    bitmap_width         = 10
                    position_padding_top = 10
                }
                internal_formulas    = [ordered]@{
                    bitmap_width         = "`$$iconWidth*$S`$"
                    position_padding_top = "`$$iconTopPad*$S`$"
                }
            },
            [ordered]@{
                internal_type           = 'TextModule'
                internal_title          = 'App Label'
                position_anchor         = 'BOTTOM'
                position_padding_bottom = $lblBotPad
                text_expression         = $label
                text_size               = $lblSize
                text_align              = 'CENTER'
                paint_color             = '#FFEAF7FF'
                internal_toggles        = [ordered]@{
                    paint_color             = 100
                    text_size               = 10
                    position_padding_bottom = 10
                }
                internal_globals        = [ordered]@{
                    paint_color = 'accent_color'
                }
                internal_formulas       = [ordered]@{
                    text_size               = "`$$lblSize*$S`$"
                    position_padding_bottom = "`$$lblBotPad*$S`$"
                }
            }
        )
    }
    [void]$viewItems.Add($btnGroup)

    # Active cyan rail flare + right-pointing chevron on the vertical divider line (x=150)
    $arrowLayer = [ordered]@{
        internal_type     = 'OverlapLayerModule'
        internal_title    = "Active Arrow: $($label -replace "`n",' ')"
        position_anchor   = 'TOPLEFT'
        position_offset_x = 136
        position_offset_y = $yPos
        internal_toggles  = [ordered]@{
            position_offset_y = 10
            config_visible    = 10
        }
        internal_formulas = [ordered]@{
            position_offset_y = "`$si(rheight)*$yFrac`$"
            config_visible    = "`$if(gv(active_app)=$id, ALWAYS, REMOVE)`$"
        }
        viewgroup_items   = @(
            [ordered]@{
                internal_type     = 'BitmapModule'
                internal_title    = 'Cyan Rail Arrow'
                position_anchor   = 'CENTER'
                bitmap_bitmap     = 'kfile://org.kustom.provider/bitmaps/btn_active_arrow.png'
                bitmap_width      = 40
                bitmap_alpha      = 100
                internal_toggles  = [ordered]@{
                    bitmap_width = 10
                }
                internal_formulas = [ordered]@{
                    bitmap_width = "`$40*$S`$"
                }
            }
        )
    }
    [void]$viewItems.Add($arrowLayer)
}

$presetObj = [ordered]@{
    preset_info = [ordered]@{
        title       = 'Jeep HUD Dashboard'
        description = 'Jeep portrait stereo KLWP dashboard with Gemini Voice, Torque, Jellyfin, Waze, YouTube, and YouTube Music + Audio Glow via Floating Apps.'
        author      = 'Jeep HUD KLWP'
        email       = ''
        version     = 11
        release     = 376000000
        locked      = $false
        width       = 720
        height      = 1290
        xscreens    = 1
        yscreens    = 1
        features    = ''
    }
    preset_root = [ordered]@{
        internal_type     = 'RootLayerModule'
        background_type   = 'IMAGE'
        background_color  = '#FF06101B'
        background_scroll = 'NONE'
        background_bitmap = 'kfile://org.kustom.provider/bitmaps/bg_jeep_hud.jpg'
        globals_list      = [ordered]@{
            active_app   = [ordered]@{
                index          = 1
                type           = 'TEXT'
                title          = 'Active App'
                description    = 'Currently selected sidebar app button (none, gemini, torque, jellyfin, waze, youtube, ytmusic)'
                global_formula = ''
                value          = 'none'
            }
            clock_24h    = [ordered]@{
                index          = 2
                type           = 'SWITCH'
                title          = '24-Hour Clock'
                description    = 'ON = 24-hour clock (HH:mm), OFF = 12-hour clock (hh:mm)'
                global_formula = ''
                value          = 1
            }
            accent_color = [ordered]@{
                index          = 3
                type           = 'COLOR'
                title          = 'HUD Text Color'
                description    = 'Primary HUD text color'
                global_formula = ''
                value          = '#FFEAF7FF'
            }
        }
        viewgroup_items   = $viewItems
    }
}

$presetJsonPath = Join-Path $presetWorkDir 'preset.json'
$jsonStr = $presetObj | ConvertTo-Json -Depth 15
[System.IO.File]::WriteAllText($presetJsonPath, $jsonStr, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "Wrote $presetJsonPath ($($jsonStr.Length) chars)"

# Re-package Jeep_HUD_Dashboard.klwp ZIP using strict forward slashes
$thumbPortrait = Join-Path $presetWorkDir 'preset_thumb_portrait.jpg'
$thumbLandscape = Join-Path $presetWorkDir 'preset_thumb_landscape.jpg'

if (Test-Path $klwpOutPath) { Remove-Item -Force $klwpOutPath }
$zip = [System.IO.Compression.ZipFile]::Open($klwpOutPath, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $presetJsonPath, 'preset.json', [System.IO.Compression.CompressionLevel]::Optimal)
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $thumbPortrait, 'preset_thumb_portrait.jpg', [System.IO.Compression.CompressionLevel]::Optimal)
    [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $thumbLandscape, 'preset_thumb_landscape.jpg', [System.IO.Compression.CompressionLevel]::Optimal)
    foreach ($f in Get-ChildItem $bitmapsDir -File) {
        $entryName = "bitmaps/$($f.Name)"
        [void][System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $f.FullName, $entryName, [System.IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $zip.Dispose()
}

# Validate ZIP integrity, forward slashes only, and verify every kfile://org.kustom.provider/... asset exists!
$checkZip = [System.IO.Compression.ZipFile]::OpenRead($klwpOutPath)
try {
    $entries = @($checkZip.Entries | ForEach-Object { $_.FullName })
    foreach ($e in $entries) {
        if ($e -match '\\') { throw "BACKSLASH IN ZIP ENTRY: $e" }
    }
    $refs = [regex]::Matches($jsonStr, 'kfile://org\.kustom\.provider/([^"\\\s]+)') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
    foreach ($ref in $refs) {
        if ($entries -notcontains $ref) { throw "MISSING KLWP ASSET REFERENCE: $ref" }
    }
    Write-Host "VALIDATED Jeep_HUD_Dashboard.klwp ($( (Get-Item $klwpOutPath).Length ) bytes, $($entries.Count) entries, $($refs.Count) kfile refs OK)"
} finally {
    $checkZip.Dispose()
}
