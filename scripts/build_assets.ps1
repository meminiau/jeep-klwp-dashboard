$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$repoRoot = 'c:\Users\WIN11\Desktop\Antigravity\klwpapp'
$brainDir = 'C:\Users\WIN11\.gemini\antigravity\brain\5268d64f-7df0-4163-95f9-e42b0eed347b'
$presetWorkDir = Join-Path $repoRoot 'klwp-preset'
$bitmapsDir = Join-Path $presetWorkDir 'bitmaps'
$assetsWallDir = Join-Path $repoRoot 'app\src\main\assets\wallpapers'

New-Item -ItemType Directory -Force -Path $bitmapsDir | Out-Null
New-Item -ItemType Directory -Force -Path $assetsWallDir | Out-Null

# Helper: High-quality rounded rectangle GraphicsPath
function New-RoundedRectPath {
    param([float]$x, [float]$y, [float]$w, [float]$h, [float]$r)
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2
    $path.AddArc($x, $y, $d, $d, 180, 90)
    $path.AddArc($x + $w - $d, $y, $d, $d, 270, 90)
    $path.AddArc($x + $w - $d, $y + $h - $d, $d, $d, 0, 90)
    $path.AddArc($x, $y + $h - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}

# Helper: Convert solid dark-navy background icon JPG to crisp transparent 256x256 PNG
# Uses BFS flood-fill from edges so interior black/dark details (Waze outline, Torque dark card) are 100% preserved.
function Convert-IconToTransparentPng {
    param(
        [string]$SrcPath,
        [string]$DstPath,
        [float]$CropMarginPct = 0.0,
        [bool]$PreserveSoftGlow = $false,
        [int]$ColorTolerance = 26,
        [bool]$RequireNavyTint = $true
    )
    $raw = [System.Drawing.Bitmap]::FromFile($SrcPath)
    $cropPx = [int]([math]::Min($raw.Width, $raw.Height) * $CropMarginPct)
    $cw = $raw.Width - 2 * $cropPx
    $ch = $raw.Height - 2 * $cropPx

    $workSize = 256
    $bmp = New-Object System.Drawing.Bitmap($workSize, $workSize, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $g.DrawImage($raw, (New-Object System.Drawing.Rectangle(0, 0, $workSize, $workSize)),
        (New-Object System.Drawing.Rectangle($cropPx, $cropPx, $cw, $ch)), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $raw.Dispose()

    # Sample background color from 4 corners
    $corners = @(
        $bmp.GetPixel(2, 2),
        $bmp.GetPixel($workSize - 3, 2),
        $bmp.GetPixel(2, $workSize - 3),
        $bmp.GetPixel($workSize - 3, $workSize - 3)
    )
    $bgR = [int](($corners | Measure-Object -Property R -Average).Average)
    $bgG = [int](($corners | Measure-Object -Property G -Average).Average)
    $bgB = [int](($corners | Measure-Object -Property B -Average).Average)

    if ($PreserveSoftGlow) {
        # For glowing icons (Gemini), compute smooth alpha from color distance to dark navy bg
        for ($y = 0; $y -lt $workSize; $y++) {
            for ($x = 0; $x -lt $workSize; $x++) {
                $p = $bmp.GetPixel($x, $y)
                $dr = [math]::Max(0, $p.R - $bgR)
                $dg = [math]::Max(0, $p.G - $bgG)
                $db = [math]::Max(0, $p.B - $bgB)
                $dist = [math]::Sqrt($dr*$dr + $dg*$dg + $db*$db)
                if ($dist -lt 12) {
                    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                } elseif ($dist -lt 85) {
                    $a = [int]([math]::Min(255, [math]::Max(0, ($dist - 12) / 73.0 * 255)))
                    # Boost luminance slightly so glow stays vivid when premultiplied/composited
                    $rOut = [int][math]::Min(255, $p.R + $dr * 0.25)
                    $gOut = [int][math]::Min(255, $p.G + $dg * 0.25)
                    $bOut = [int][math]::Min(255, $p.B + $db * 0.25)
                    $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $rOut, $gOut, $bOut))
                }
            }
        }
    } else {
        # BFS flood-fill from border pixels to strip only outer dark navy background
        $visited = New-Object 'bool[,]' $workSize, $workSize
        $isBg = New-Object 'bool[,]' $workSize, $workSize
        $queue = New-Object 'System.Collections.Generic.Queue[int]'

        for ($x = 0; $x -lt $workSize; $x++) {
            $queue.Enqueue($x)
            $visited[$x, 0] = $true
            $queue.Enqueue(($workSize - 1) * $workSize + $x)
            $visited[$x, ($workSize - 1)] = $true
        }
        for ($y = 1; $y -lt $workSize - 1; $y++) {
            $queue.Enqueue($y * $workSize)
            $visited[0, $y] = $true
            $queue.Enqueue($y * $workSize + ($workSize - 1))
            $visited[($workSize - 1), $y] = $true
        }

        while ($queue.Count -gt 0) {
            $idx = $queue.Dequeue()
            $cx = $idx % $workSize
            $cy = [int][math]::Floor($idx / $workSize)
            $p = $bmp.GetPixel($cx, $cy)
            $dist = [math]::Abs($p.R - $bgR) + [math]::Abs($p.G - $bgG) + [math]::Abs($p.B - $bgB)
            $navyOk = (-not $RequireNavyTint) -or (($p.B - $p.R) -ge 8)
            if ($dist -le $ColorTolerance -and $navyOk) {
                $isBg[$cx, $cy] = $true
                $bmp.SetPixel($cx, $cy, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                if ($cx -gt 0 -and -not $visited[($cx - 1), $cy]) { $visited[($cx - 1), $cy] = $true; $queue.Enqueue($cy * $workSize + ($cx - 1)) }
                if ($cx -lt $workSize - 1 -and -not $visited[($cx + 1), $cy]) { $visited[($cx + 1), $cy] = $true; $queue.Enqueue($cy * $workSize + ($cx + 1)) }
                if ($cy -gt 0 -and -not $visited[$cx, ($cy - 1)]) { $visited[$cx, ($cy - 1)] = $true; $queue.Enqueue(($cy - 1) * $workSize + $cx) }
                if ($cy -lt $workSize - 1 -and -not $visited[$cx, ($cy + 1)]) { $visited[$cx, ($cy + 1)] = $true; $queue.Enqueue(($cy + 1) * $workSize + $cx) }
            }
        }

        # Soft 1px edge feathering on boundary pixels touching transparent bg
        for ($y = 1; $y -lt $workSize - 1; $y++) {
            for ($x = 1; $x -lt $workSize - 1; $x++) {
                if (-not $isBg[$x, $y]) {
                    $touchesBg = $isBg[($x-1),$y] -or $isBg[($x+1),$y] -or $isBg[$x,($y-1)] -or $isBg[$x,($y+1)]
                    if ($touchesBg) {
                        $p = $bmp.GetPixel($x, $y)
                        $dist = [math]::Abs($p.R - $bgR) + [math]::Abs($p.G - $bgG) + [math]::Abs($p.B - $bgB)
                        if ($dist -lt ($ColorTolerance * 2)) {
                            $a = [int]([math]::Min(255, [math]::Max(40, ($dist / [float]($ColorTolerance * 2)) * 255)))
                            $bmp.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($a, $p.R, $p.G, $p.B))
                        }
                    }
                }
            }
        }
    }

    $bmp.Save($DstPath, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host "Saved transparent icon: $DstPath"
}

# 1. Copy clean Jeep HUD background into klwp-preset/bitmaps/bg_jeep_hud.jpg
$bgSrc = Join-Path $brainDir 'jeep_bg_clean_1795471448214.jpg'
$bgDst = Join-Path $bitmapsDir 'bg_jeep_hud.jpg'
Copy-Item -Force $bgSrc $bgDst
Write-Host "Copied clean HUD background to $bgDst"

# 2. Convert Gemini, Torque, Jellyfin, Waze icons to crisp 256x256 transparent PNGs
Convert-IconToTransparentPng `
    -SrcPath (Join-Path $brainDir 'icon_gemini_v2_1795471457809.jpg') `
    -DstPath (Join-Path $bitmapsDir 'icon_gemini.png') `
    -CropMarginPct 0.16 `
    -PreserveSoftGlow $true

Convert-IconToTransparentPng `
    -SrcPath (Join-Path $brainDir 'torque_icon_512_1795471584804.jpg') `
    -DstPath (Join-Path $bitmapsDir 'icon_torque.png') `
    -CropMarginPct 0.08 `
    -PreserveSoftGlow $false `
    -ColorTolerance 32 `
    -RequireNavyTint $true

Convert-IconToTransparentPng `
    -SrcPath (Join-Path $brainDir 'jellyfin_icon_v2_1795471764427.jpg') `
    -DstPath (Join-Path $bitmapsDir 'icon_jellyfin.png') `
    -CropMarginPct 0.10 `
    -PreserveSoftGlow $false `
    -ColorTolerance 34 `
    -RequireNavyTint $true

Convert-IconToTransparentPng `
    -SrcPath (Join-Path $brainDir 'waze_icon_final_1795471602078.jpg') `
    -DstPath (Join-Path $bitmapsDir 'icon_waze.png') `
    -CropMarginPct 0.10 `
    -PreserveSoftGlow $false `
    -ColorTolerance 28 `
    -RequireNavyTint $true

# 3. Render vector-grade YouTube icon (256x256 transparent PNG)
$ytBmp = New-Object System.Drawing.Bitmap(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($ytBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
$ytPath = New-RoundedRectPath 18 52 220 152 36
$ytBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 235, 16, 16))
$g.FillPath($ytBrush, $ytPath)
$tri = [System.Drawing.PointF[]]@(
    (New-Object System.Drawing.PointF(103, 94)),
    (New-Object System.Drawing.PointF(103, 162)),
    (New-Object System.Drawing.PointF(165, 128))
)
$g.FillPolygon([System.Drawing.Brushes]::White, $tri)
$ytBrush.Dispose(); $ytPath.Dispose(); $g.Dispose()
$ytBmp.Save((Join-Path $bitmapsDir 'icon_youtube.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$ytBmp.Dispose()
Write-Host "Rendered vector icon_youtube.png"

# 4. Render vector-grade YouTube Music icon (256x256 transparent PNG)
$ytmBmp = New-Object System.Drawing.Bitmap(256, 256, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($ytmBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
$redBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 235, 16, 16))
$g.FillEllipse($redBrush, 24, 24, 208, 208)
$ringPen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 10)
$g.DrawEllipse($ringPen, 64, 64, 128, 128)
$tri2 = [System.Drawing.PointF[]]@(
    (New-Object System.Drawing.PointF(108, 92)),
    (New-Object System.Drawing.PointF(108, 164)),
    (New-Object System.Drawing.PointF(168, 128))
)
$g.FillPolygon([System.Drawing.Brushes]::White, $tri2)
$redBrush.Dispose(); $ringPen.Dispose(); $g.Dispose()
$ytmBmp.Save((Join-Path $bitmapsDir 'icon_ytmusic.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$ytmBmp.Dispose()
Write-Host "Rendered vector icon_ytmusic.png"

# 5. Render HUD button frames (inactive dark glass + active glowing cyan + active right chevron rail)
# Button canvas: 296x340 (maps to 148x170 in KLWP, with 10px outer glow padding so core card is 128x150!)
$cardW = 296; $cardH = 340
$pad = 24
$innerX = $pad; $innerY = $pad; $innerW = $cardW - 2*$pad; $innerH = $cardH - 2*$pad

# 5a. Inactive button card (btn_inactive.png)
$inBmp = New-Object System.Drawing.Bitmap($cardW, $cardH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($inBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
$pCard = New-RoundedRectPath $innerX $innerY $innerW $innerH 24
$bgGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point($innerX, $innerY)),
    (New-Object System.Drawing.Point($innerX, ($innerY + $innerH))),
    [System.Drawing.Color]::FromArgb(215, 11, 27, 44),
    [System.Drawing.Color]::FromArgb(230, 6, 17, 30)
)
$g.FillPath($bgGrad, $pCard)
$borderPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(210, 74, 134, 164), 3.5)
$g.DrawPath($borderPen, $pCard)
# Subtle inner top highlight
$pInner = New-RoundedRectPath ($innerX + 3) ($innerY + 3) ($innerW - 6) ($innerH - 6) 21
$innerPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(45, 120, 210, 240), 1.5)
$g.DrawPath($innerPen, $pInner)
$bgGrad.Dispose(); $borderPen.Dispose(); $innerPen.Dispose(); $pCard.Dispose(); $pInner.Dispose(); $g.Dispose()
$inBmp.Save((Join-Path $bitmapsDir 'btn_inactive.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$inBmp.Dispose()

# 5b. Active glowing cyan button card (btn_active_glow.png) matching Waze tapped look in Image 1
$actBmp = New-Object System.Drawing.Bitmap($cardW, $cardH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($actBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
# Multi-pass soft neon cyan outer glow
for ($i = 20; $i -ge 2; $i -= 2) {
    $alpha = [int]([math]::Max(4, 34 - $i * 1.4))
    $pGlow = New-RoundedRectPath ($innerX - $i) ($innerY - $i) ($innerW + 2*$i) ($innerH + 2*$i) (24 + $i * 0.6)
    $gPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb($alpha, 0, 232, 255), 5)
    $g.DrawPath($gPen, $pGlow)
    $gPen.Dispose(); $pGlow.Dispose()
}
$pCard = New-RoundedRectPath $innerX $innerY $innerW $innerH 24
$actGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    (New-Object System.Drawing.Point($innerX, $innerY)),
    (New-Object System.Drawing.Point($innerX, ($innerY + $innerH))),
    [System.Drawing.Color]::FromArgb(225, 14, 118, 146),
    [System.Drawing.Color]::FromArgb(235, 7, 68, 92)
)
$g.FillPath($actGrad, $pCard)
# Inner cyan sheen
$sheenRect = New-Object System.Drawing.Rectangle($innerX, $innerY, $innerW, [int]($innerH * 0.45))
$sheenGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $sheenRect,
    [System.Drawing.Color]::FromArgb(70, 120, 248, 255),
    [System.Drawing.Color]::FromArgb(0, 0, 229, 255),
    90
)
$g.SetClip($pCard)
$g.FillRectangle($sheenGrad, $sheenRect)
$g.ResetClip()
# Bright crisp glowing neon cyan border
$neonOuter = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(245, 70, 245, 255), 6)
$g.DrawPath($neonOuter, $pCard)
$neonCore = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(240, 210, 255, 255), 2)
$g.DrawPath($neonCore, $pCard)
$actGrad.Dispose(); $sheenGrad.Dispose(); $neonOuter.Dispose(); $neonCore.Dispose(); $pCard.Dispose(); $g.Dispose()
$actBmp.Save((Join-Path $bitmapsDir 'btn_active_glow.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$actBmp.Dispose()

# 5c. Active cyan rail glow + right-pointing chevron pointer (btn_active_arrow.png, 80x340 -> 40x170 in KLWP)
$arrW = 80; $arrH = 340
$arrBmp = New-Object System.Drawing.Bitmap($arrW, $arrH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($arrBmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
$g.Clear([System.Drawing.Color]::Transparent)
# Vertical glowing rail segment centered at x=28
for ($i = 18; $i -ge 2; $i -= 2) {
    $a = [int]([math]::Max(4, 32 - $i * 1.4))
    $railGrad = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Point(0, 10)),
        (New-Object System.Drawing.Point(0, ($arrH - 10))),
        [System.Drawing.Color]::FromArgb(0, 0, 232, 255),
        [System.Drawing.Color]::FromArgb(0, 0, 232, 255)
    )
    $cb = New-Object System.Drawing.Drawing2D.ColorBlend
    $cb.Colors = @(
        [System.Drawing.Color]::FromArgb(0, 0, 232, 255),
        [System.Drawing.Color]::FromArgb($a, 0, 232, 255),
        [System.Drawing.Color]::FromArgb($a, 120, 250, 255),
        [System.Drawing.Color]::FromArgb($a, 0, 232, 255),
        [System.Drawing.Color]::FromArgb(0, 0, 232, 255)
    )
    $cb.Positions = @(0.0, 0.25, 0.5, 0.75, 1.0)
    $railGrad.InterpolationColors = $cb
    $pen = New-Object System.Drawing.Pen($railGrad, $i)
    $g.DrawLine($pen, 28, 12, 28, $arrH - 12)
    $pen.Dispose(); $railGrad.Dispose()
}
# Crisp bright chevron triangle pointing right (centered at y = 170)
$cy = $arrH / 2.0
$chevPath = New-Object System.Drawing.Drawing2D.GraphicsPath
$chevPath.AddLines([System.Drawing.PointF[]]@(
    (New-Object System.Drawing.PointF(28, ($cy - 26))),
    (New-Object System.Drawing.PointF(54, $cy)),
    (New-Object System.Drawing.PointF(28, ($cy + 26)))
))
$chevPath.CloseFigure()
$chevBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(245, 120, 250, 255))
$g.FillPath($chevBrush, $chevPath)
$chevPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 225, 255, 255), 2.5)
$g.DrawPath($chevPen, $chevPath)
$chevBrush.Dispose(); $chevPen.Dispose(); $chevPath.Dispose(); $g.Dispose()
$arrBmp.Save((Join-Path $bitmapsDir 'btn_active_arrow.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$arrBmp.Dispose()
Write-Host "Rendered HUD button cards and active pointer arrow"

# 6. Generate Android launcher mipmap icons from jeep_hud_dash_icon_final_1795471620309.jpg
$launcherSrc = [System.Drawing.Bitmap]::FromFile((Join-Path $brainDir 'jeep_hud_dash_icon_final_1795471620309.jpg'))
# Crop 6% border to eliminate outer white corners cleanly
$lCrop = [int]($launcherSrc.Width * 0.06)
$lSize = $launcherSrc.Width - 2 * $lCrop
$mipmaps = @{
    'mipmap-mdpi'    = 48
    'mipmap-hdpi'    = 72
    'mipmap-xhdpi'   = 96
    'mipmap-xxhdpi'  = 144
    'mipmap-xxxhdpi' = 192
}
foreach ($kv in $mipmaps.GetEnumerator()) {
    $dir = Join-Path $repoRoot ("app\src\main\res\" + $kv.Key)
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    $sz = $kv.Value
    $mBmp = New-Object System.Drawing.Bitmap($sz, $sz, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($mBmp)
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
    $g.DrawImage($launcherSrc, (New-Object System.Drawing.Rectangle(0, 0, $sz, $sz)),
        (New-Object System.Drawing.Rectangle($lCrop, $lCrop, $lSize, $lSize)), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $mBmp.Save((Join-Path $dir 'ic_launcher.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $mBmp.Dispose()
}
$launcherSrc.Dispose()
Write-Host "Generated Android launcher mipmap icons"
