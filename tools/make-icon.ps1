<#
.SYNOPSIS
    生成应用图标：**九宫格 + 数字**的纸墨风格，按尺寸自适应细节，并导出各平台规格与源文件。

.DESCRIPTION
    设计要点（详见 docs/06-应用图标设计.md）：
      · 主体是**一个九宫格（3×3 宫）**，格子里的**数字**表达"这是数独"——不是棋子，也不是纯网格；
      · 部分已填、部分留空，且数字数量随尺寸递减（256px 五个、64px 四个、32px 两个、16px 一个），
        保证任何尺寸都读得出"数字格子"而不是一团墨；
      · 最后一位（中心格）用中间灰，暗示"玩家刚填进去的"，营造解题氛围；
      · 只有纸与墨两色（外加一档中间灰），圆角纸面 + 透明边，风格简洁现代；
      · 抖动/倾斜一概不用，几何干净，避免"棋盘落子"的联想。

    产物：
      composeApp/icons/            运行时 / 打包用（进 jar 与 jpackage）
        sudoku.ico                 多尺寸（16/20/24/32/40/48/64/96/128/256），Windows
        sudoku.png                 256，Linux deb
        sudoku-NN.png              16/24/32/48/64/128，运行时窗口 / 任务栏图标
        SudokuInk.icns             macOS（11 个 PNG 块；同时给出 iconutil 用的 .iconset）
      assets/icon/                 交付用（各平台规格 + 源文件）
        desktop/  android/（含 adaptive/ 自适应分层）  ios/  macos/  web/  source/

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/make-icon.ps1
#>
param(
    [string]$RuntimeDir = (Join-Path $PSScriptRoot '..\composeApp\icons'),
    [string]$ExportDir = (Join-Path $PSScriptRoot '..\assets\icon')
)

Add-Type -AssemblyName System.Drawing

$paper = [System.Drawing.Color]::FromArgb(255, 247, 245, 240)   # Ink.Paper
$ink = [System.Drawing.Color]::FromArgb(255, 27, 26, 23)        # Ink.Black
$inkSoft = [System.Drawing.Color]::FromArgb(150, 27, 26, 23)    # 纸面描边
$filled = [System.Drawing.Color]::FromArgb(170, 75, 72, 67)     # Ink.Grey：玩家填入的数字

# 数字布局：四角为"题目给定"（实墨），中心为"玩家填入"（中灰，仅大尺寸画）
$cornerDigits = @(
    @{ row = 0; col = 0; text = '5' },
    @{ row = 0; col = 2; text = '3' },
    @{ row = 2; col = 0; text = '9' },
    @{ row = 2; col = 2; text = '7' }
)
$centerDigit = @{ row = 1; col = 1; text = '4' }

function New-RoundedPath([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $path = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = $r * 2.0
    $path.AddArc([single]$x, [single]$y, [single]$d, [single]$d, 180, 90)
    $path.AddArc([single]($x + $w - $d), [single]$y, [single]$d, [single]$d, 270, 90)
    $path.AddArc([single]($x + $w - $d), [single]($y + $h - $d), [single]$d, [single]$d, 0, 90)
    $path.AddArc([single]$x, [single]($y + $h - $d), [single]$d, [single]$d, 90, 90)
    $path.CloseFigure()
    return $path
}

function New-InkTextureFont([single]$sizePx, [System.Drawing.FontStyle]$style) {
    foreach ($name in @('Segoe UI', 'Microsoft YaHei UI', 'Arial')) {
        foreach ($candidate in [System.Drawing.FontFamily]::Families) {
            if ($candidate.Name -eq $name) {
                return New-Object System.Drawing.Font($candidate, $sizePx, $style, [System.Drawing.GraphicsUnit]::Pixel)
            }
        }
    }
    return New-Object System.Drawing.Font([System.Drawing.FontFamily]::GenericSansSerif, $sizePx, $style, [System.Drawing.GraphicsUnit]::Pixel)
}

<#
    画九宫格 + 数字——**独立图标与 Android 自适应前景层共用这一处绘制逻辑**（改视觉只改这里）。
      x / y / side —— 九宫格方框的左上角与边长（px）
      lineRef     —— 线宽参照长度（独立图标取画布边长；自适应前景取"可见视口边长"）
      viewSize    —— 决定细节层级的**可见尺寸**（px，与 docs/06 §3 的表一致）
      mono        —— true 时中心数字也用实墨（单色主题图标不允许灰度差异）
    几何：外框线 max(1.2, 0.030·ref)，内线 max(0.9, 0.020·ref)；
          数字字号 0.62 × 格宽、上限 0.75 × 格宽、下限 3px。
#>
function Draw-InkGrid([System.Drawing.Graphics]$g, [single]$x, [single]$y, [single]$side, [double]$lineRef, [int]$viewSize, [bool]$mono = $false) {
    $cell = [double]$side / 3.0
    $inkBrush = New-Object System.Drawing.SolidBrush($ink)
    $filledBrush = New-Object System.Drawing.SolidBrush($filled)

    $framePen = New-Object System.Drawing.Pen($ink, [single]([Math]::Max(1.2, $lineRef * 0.030)))
    $framePen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
    $innerPen = New-Object System.Drawing.Pen($ink, [single]([Math]::Max(0.9, $lineRef * 0.020)))
    for ($i = 1; $i -le 2; $i++) {
        $p = [single]($x + $i * $cell)
        $g.DrawLine($innerPen, $p, $y, $p, [single]($y + $side))
        $g.DrawLine($innerPen, $x, $p, [single]($x + $side), $p)
    }
    $g.DrawRectangle($framePen, [float]$x, [float]$y, [float]$side, [float]$side)

    # 数字：字号 = 0.62 × 格宽（图标里数字要"顶格"才清楚），且**永不超过格子的 75%**（否则会溢出格线）
    $fontSize = [single]([Math]::Min([Math]::Max(3.0, $cell * 0.62), $cell * 0.75))
    $font = New-InkTextureFont $fontSize ([System.Drawing.FontStyle]::Bold)
    # 用 GenericTypographic 量字（含 padding 更小），再手工居中——比 StringFormat 居中更贴合视觉中心
    $format = [System.Drawing.StringFormat]::GenericTypographic

    $drawDigit = {
        param($digit, $brush)
        $measure = $g.MeasureString($digit.text, $font, 1000, $format)
        $dx = [single]($x + $digit.col * $cell + ($cell - $measure.Width) / 2.0)
        $dy = [single]($y + $digit.row * $cell + ($cell - $measure.Height) / 2.0)
        $g.DrawString($digit.text, $font, $brush, $dx, $dy, $format)
    }

    # 细节随尺寸递减：见 docs/06 §3
    if ($viewSize -ge 64) {
        foreach ($d in $cornerDigits) { & $drawDigit $d $inkBrush }
    } elseif ($viewSize -ge 32) {
        & $drawDigit $cornerDigits[0] $inkBrush   # 左上 5
        & $drawDigit $cornerDigits[3] $inkBrush   # 右下 7
    } else {
        & $drawDigit $cornerDigits[0] $inkBrush   # 16px：只留左上一位，避免糊成一团
    }
    if ($viewSize -ge 128) { & $drawDigit $centerDigit $(if ($mono) { $inkBrush } else { $filledBrush }) }

    $font.Dispose(); $inkBrush.Dispose(); $filledBrush.Dispose()
    $framePen.Dispose(); $innerPen.Dispose()
}

<#
    独立图标：圆角纸面 + 九宫格 + 数字。
    比例（相对画布边长 S）：纸面内缩 0.055S、圆角 0.185S；
    九宫格内缩 0.19S（<48px 收窄到 0.15S，给小尺寸数字留落脚处）。
#>
function New-SudokuIcon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    $s = [double]$size
    $tilePad = [single]($s * 0.055)
    $tileSide = [single]($s - 2.0 * $tilePad)
    $tilePath = New-RoundedPath $tilePad $tilePad $tileSide $tileSide ([single]($s * 0.185))
    $paperBrush = New-Object System.Drawing.SolidBrush($paper)
    $g.FillPath($paperBrush, $tilePath)
    $edgePen = New-Object System.Drawing.Pen($inkSoft, [single]([Math]::Max(0.6, $s / 70.0)))
    $g.DrawPath($edgePen, $tilePath)

    $gridPad = [single]($s * $(if ($size -lt 48) { 0.15 } else { 0.19 }))
    $gridSide = [single]($s - 2.0 * $gridPad)
    Draw-InkGrid $g $gridPad $gridPad $gridSide $s $size

    $edgePen.Dispose(); $paperBrush.Dispose(); $tilePath.Dispose(); $g.Dispose()
    return $bmp
}

<#
    传统圆形图标（Android < 8.0 启动器请求 round 图标时用）：
    整圆纸面 + 细环 + 九宫格；九宫格边长 0.62S 时对角半径 0.438S < 0.5S，四角不会越出圆外。
#>
function New-RoundIcon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::Transparent)

    $s = [double]$size
    $circle = New-Object System.Drawing.Drawing2D.GraphicsPath
    $circle.AddEllipse(0, 0, [single]$size, [single]$size)
    $paperBrush = New-Object System.Drawing.SolidBrush($paper)
    $g.FillPath($paperBrush, $circle)
    $ringPen = New-Object System.Drawing.Pen($inkSoft, [single]([Math]::Max(0.6, $s / 70.0)))
    $g.DrawPath($ringPen, $circle)

    $gridPad = [single]($s * 0.19)
    $gridSide = [single]($s * 0.62)
    Draw-InkGrid $g $gridPad $gridPad $gridSide $s $size

    $ringPen.Dispose(); $paperBrush.Dispose(); $circle.Dispose(); $g.Dispose()
    return $bmp
}

function Save-Png([System.Drawing.Bitmap]$bmp, [string]$path) {
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
}

function Ensure-Dir([string]$path) {
    if (-not (Test-Path $path)) { New-Item -ItemType Directory -Path $path -Force | Out-Null }
}

function Export-Size([int]$size, [string]$dir, [string]$name) {
    $path = Join-Path $dir $name
    Ensure-Dir (Split-Path $path -Parent)   # name 可能带子目录（如 android/mipmap-mdpi/）
    $bmp = New-SudokuIcon $size
    Save-Png $bmp $path
    $bmp.Dispose()
}

# ---------------------------------------------------------------- 运行时 / 打包用
Ensure-Dir $RuntimeDir
$icoSizes = @(16, 20, 24, 32, 40, 48, 64, 96, 128, 256)
$pngs = New-Object System.Collections.ArrayList
foreach ($s in $icoSizes) {
    $bmp = New-SudokuIcon $s
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    [void]$pngs.Add($ms.ToArray())
    $ms.Dispose()
    if ($s -eq 256) { Save-Png $bmp (Join-Path $RuntimeDir 'sudoku.png') }          # Linux deb
    if (@(16, 24, 32, 48, 64, 128) -contains $s) { Save-Png $bmp (Join-Path $RuntimeDir "sudoku-$s.png") }  # 运行时窗口图标
    $bmp.Dispose()
}

$icoPath = Join-Path $RuntimeDir 'sudoku.ico'
$stream = [System.IO.File]::Create($icoPath)
$writer = New-Object System.IO.BinaryWriter($stream)
$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$icoSizes.Count)
$offset = 6 + 16 * $icoSizes.Count
for ($i = 0; $i -lt $icoSizes.Count; $i++) {
    $dim = if ($icoSizes[$i] -ge 256) { 0 } else { $icoSizes[$i] }   # 目录项里 256 记作 0
    $data = $pngs[$i]
    $writer.Write([Byte]$dim); $writer.Write([Byte]$dim)
    $writer.Write([Byte]0); $writer.Write([Byte]0)
    $writer.Write([UInt16]1); $writer.Write([UInt16]32)
    $writer.Write([UInt32]$data.Length); $writer.Write([UInt32]$offset)
    $offset += $data.Length
}
foreach ($data in $pngs) { $writer.Write($data) }
$writer.Flush(); $writer.Dispose(); $stream.Dispose()
Write-Output ("runtime  " + $icoPath + "  " + (Get-Item $icoPath).Length + " B  (" + ($icoSizes -join '/') + ")")

# ---------------------------------------------------------------- 交付：各平台规格
$desktopDir = Join-Path $ExportDir 'desktop'
foreach ($s in @(16, 24, 32, 48, 64, 128, 256, 512)) { Export-Size $s $desktopDir "icon-$s.png" }

$androidDir = Join-Path $ExportDir 'android'
$android = @(
    @{ size = 48; name = 'mipmap-mdpi/ic_launcher.png' },
    @{ size = 72; name = 'mipmap-hdpi/ic_launcher.png' },
    @{ size = 96; name = 'mipmap-xhdpi/ic_launcher.png' },
    @{ size = 144; name = 'mipmap-xxhdpi/ic_launcher.png' },
    @{ size = 192; name = 'mipmap-xxxhdpi/ic_launcher.png' },
    @{ size = 512; name = 'play-store-512.png' }
)
foreach ($a in $android) {
    Export-Size $a.size $androidDir $a.name
    if ($a.name -like 'mipmap-*') {
        # 传统圆形图标：Android < 8.0 的启动器请求 round 图标时使用（8.0+ 一律走自适应图标）
        $roundPath = Join-Path $androidDir ($a.name -replace 'ic_launcher\.png$', 'ic_launcher_round.png')
        Ensure-Dir (Split-Path $roundPath -Parent)
        $bmp = New-RoundIcon $a.size
        Save-Png $bmp $roundPath
        $bmp.Dispose()
    }
}

$iosDir = Join-Path $ExportDir 'ios'
$ios = @(
    @{ size = 40; name = 'Icon-20@2x.png' }, @{ size = 60; name = 'Icon-20@3x.png' },
    @{ size = 58; name = 'Icon-29@2x.png' }, @{ size = 87; name = 'Icon-29@3x.png' },
    @{ size = 80; name = 'Icon-40@2x.png' }, @{ size = 120; name = 'Icon-40@3x.png' },
    @{ size = 120; name = 'Icon-60@2x.png' }, @{ size = 180; name = 'Icon-60@3x.png' },
    @{ size = 76; name = 'Icon-76.png' }, @{ size = 152; name = 'Icon-76@2x.png' },
    @{ size = 167; name = 'Icon-83.5@2x.png' }, @{ size = 1024; name = 'Icon-1024.png' }
)
foreach ($i in $ios) { Export-Size $i.size $iosDir $i.name }

$webDir = Join-Path $ExportDir 'web'
$web = @(
    @{ size = 16; name = 'favicon-16.png' }, @{ size = 32; name = 'favicon-32.png' },
    @{ size = 48; name = 'favicon-48.png' }, @{ size = 180; name = 'apple-touch-icon.png' },
    @{ size = 192; name = 'icon-192.png' }, @{ size = 512; name = 'icon-512.png' }
)
foreach ($w in $web) { Export-Size $w.size $webDir $w.name }

# --------------------------------------------- 交付：Android 自适应图标（108dp 分层 + 66dp 安全区）
# Google Adaptive Icon 规范：画布 108dp；系统掩膜后**可见 72dp**；关键内容必须落在**直径 66dp 的圆**内
#   （掩膜形状各不相同，且系统会施加视差位移，所以内容比可见区再收一圈）。
# 分层：background（纸，full-bleed，不画任何边距）/ foreground（九宫格 + 数字，透明底）/
#       monochrome（单色剪影，Android 13+ 主题图标，系统统一着色）。
$adaptiveRes = Join-Path $ExportDir 'android\adaptive\res'
$vpRatio = 72.0 / 108.0
$safeRatio = 66.0 / 108.0
$densities = @(
    @{ name = 'mdpi'; scale = 1.0 }, @{ name = 'hdpi'; scale = 1.5 }, @{ name = 'xhdpi'; scale = 2.0 },
    @{ name = 'xxhdpi'; scale = 3.0 }, @{ name = 'xxxhdpi'; scale = 4.0 }
)

function New-AdaptiveLayer([int]$canvasPx, [string]$kind) {
    $bmp = New-Object System.Drawing.Bitmap($canvasPx, $canvasPx, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    if ($kind -eq 'background') {
        $g.Clear($paper)                      # full-bleed：掩膜自己裁形状，背景层不留边距、不描边
    } else {
        $g.Clear([System.Drawing.Color]::Transparent)
        $vp = [double]$canvasPx * $vpRatio    # 可见视口边长（72dp）
        $origin = ([double]$canvasPx - $vp) / 2.0
        # 与独立图标同一比例，但**参照"可见尺寸"**而不是画布：九宫格占可见区 62%，
        # 对角半径 = 0.62 × 72dp / 2 × √2 ≈ 31.6dp < 33dp（安全区半径）⇒ 天生合规。
        $gridPad = [single]($origin + $vp * 0.19)
        $gridSide = [single]($vp * 0.62)
        Draw-InkGrid $g $gridPad $gridPad $gridSide $vp ([int][Math]::Round($vp)) ($kind -eq 'monochrome')
    }
    $g.Dispose()
    return $bmp
}

foreach ($d in $densities) {
    $canvasPx = [int][Math]::Round(108 * $d.scale)
    foreach ($layer in @('background', 'foreground', 'monochrome')) {
        $path = Join-Path $adaptiveRes ("mipmap-" + $d.name + "\ic_launcher_" + $layer + ".png")
        Ensure-Dir (Split-Path $path -Parent)
        $bmp = New-AdaptiveLayer $canvasPx $layer
        Save-Png $bmp $path
        $bmp.Dispose()
    }
}

# 资源侧 XML：@color 背景 + 前后景/单色三层；round 与传统图标共用同一份自适应描述
$anydpiDir = Join-Path $adaptiveRes 'mipmap-anydpi-v26'
$valuesDir = Join-Path $adaptiveRes 'values'
Ensure-Dir $anydpiDir; Ensure-Dir $valuesDir
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
$adaptiveXml = @"
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"@
foreach ($xmlName in @('ic_launcher.xml', 'ic_launcher_round.xml')) {
    [System.IO.File]::WriteAllText((Join-Path $anydpiDir $xmlName), $adaptiveXml, $utf8NoBom)
}
[System.IO.File]::WriteAllText(
    (Join-Path $valuesDir 'ic_launcher_background.xml'),
    "<?xml version=`"1.0`" encoding=`"utf-8`"?>`r`n<resources>`r`n    <color name=`"ic_launcher_background`">#F7F5F0</color>`r`n</resources>`r`n",
    $utf8NoBom
)

# 安全区自检：扫前景层非透明像素，算到画布中心的最大半径，换算成 dp 与 33dp 比较
function Get-MaxRadiusDp([System.Drawing.Bitmap]$bmp, [double]$canvasDp) {
    $rect = New-Object System.Drawing.Rectangle(0, 0, $bmp.Width, $bmp.Height)
    $data = $bmp.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $stride = $data.Stride
    $buf = New-Object byte[] ([Math]::Abs($stride) * $bmp.Height)
    [System.Runtime.InteropServices.Marshal]::Copy($data.Scan0, $buf, 0, $buf.Length)
    $bmp.UnlockBits($data)
    $c = ($bmp.Width - 1) / 2.0
    $max = 0.0
    for ($y = 0; $y -lt $bmp.Height; $y++) {
        $row = $y * $stride
        for ($x = 0; $x -lt $bmp.Width; $x++) {
            if ($buf[$row + $x * 4 + 3] -gt 8) {
                $dx = $x - $c; $dy = $y - $c
                $r = [Math]::Sqrt($dx * $dx + $dy * $dy)
                if ($r -gt $max) { $max = $r }
            }
        }
    }
    return $max / ($bmp.Width / $canvasDp)
}

$checkPx = [int][Math]::Round(108 * 4)      # xxxhdpi
$fgCheck = New-AdaptiveLayer $checkPx 'foreground'
$maxDp = Get-MaxRadiusDp $fgCheck 108.0
$fgCheck.Dispose()
$safeDp = 66.0 / 2.0
$safeOk = $maxDp -le $safeDp

# 自适应图标验收图：左 = 108dp 画布上的三层关系与两个参考圈；右 = 四种常见掩膜下的实际显示
function New-MaskPreview([int]$px, [string]$mask, [System.Drawing.Bitmap]$bg, [System.Drawing.Bitmap]$fg) {
    $bmp = New-Object System.Drawing.Bitmap($px, $px, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)
    switch ($mask) {
        'circle' { $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddEllipse(0, 0, $px, $px) }
        'squircle' { $p = New-RoundedPath 0 0 $px $px ([single]($px * 0.42)) }
        'rounded' { $p = New-RoundedPath 0 0 $px $px ([single]($px * 0.18)) }
        default { $p = New-Object System.Drawing.Drawing2D.GraphicsPath; $p.AddRectangle((New-Object System.Drawing.Rectangle(0, 0, $px, $px))) }
    }
    $g.SetClip($p)
    # 掩膜可见区 = 72dp 视口：把 108dp 画布按 1/vpRatio 放大画上去，并**向左上偏移半个多出的边**，
    # 使画布中心与掩膜中心重合（少这个负偏移，内容会整体偏到右下方并被裁掉）。
    $scale = [single]($px / $vpRatio)
    $off = [single](($scale - $px) / 2.0)
    $g.DrawImage($bg, -$off, -$off, $scale, $scale)
    $g.DrawImage($fg, -$off, -$off, $scale, $scale)
    $p.Dispose(); $g.Dispose()
    return $bmp
}

$canvasPx = 288
$vpPx = [int][Math]::Round($canvasPx * $vpRatio)
$safePx = [int][Math]::Round($canvasPx * $safeRatio)
$bgBmp = New-AdaptiveLayer $canvasPx 'background'
$fgBmp = New-AdaptiveLayer $canvasPx 'foreground'
$stripH = 64                                   # 说明文字放在图下方，避免压住图标
$tile = New-Object System.Drawing.Bitmap($canvasPx, ($canvasPx + $stripH), [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$tg = [System.Drawing.Graphics]::FromImage($tile)
$tg.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$tg.Clear([System.Drawing.Color]::FromArgb(255, 176, 176, 176))
$tg.DrawImage($bgBmp, 0, 0, $canvasPx, $canvasPx)
$tg.DrawImage($fgBmp, 0, 0, $canvasPx, $canvasPx)
$labelFont = New-InkTextureFont 12 ([System.Drawing.FontStyle]::Regular)
$labelBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 40, 40, 40))
$safeBrush = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 150, 48, 42))
$vpPen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(150, 60, 60, 60), 1.5)
$vpPen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
$safePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(220, 150, 48, 42), 2.0)
$safePen.DashStyle = [System.Drawing.Drawing2D.DashStyle]::Dash
$vpOff = [int](($canvasPx - $vpPx) / 2)
$safeOff = [int](($canvasPx - $safePx) / 2)
$tg.DrawRectangle($vpPen, $vpOff, $vpOff, $vpPx, $vpPx)
$tg.DrawEllipse($safePen, $safeOff, $safeOff, $safePx, $safePx)
$tg.DrawString('108dp canvas / 72dp viewport (dashed square)', $labelFont, $labelBrush, 4, ($canvasPx + 4))
$tg.DrawString('66dp safe circle (dashed circle) - content inside', $labelFont, $safeBrush, 4, ($canvasPx + 22))
$tg.DrawString(('foreground max radius = {0:0.0}dp / 33.0dp allowed' -f $maxDp), $labelFont, $labelBrush, 4, ($canvasPx + 40))
$maskNames = @(
    @{ key = 'circle'; label = 'circle' }, @{ key = 'squircle'; label = 'squircle' },
    @{ key = 'rounded'; label = 'rounded square' }, @{ key = 'square'; label = 'square' }
)
$maskPx = 144
$maskBitmaps = New-Object System.Collections.ArrayList
foreach ($m in $maskNames) { [void]$maskBitmaps.Add((New-MaskPreview $maskPx $m.key $bgBmp $fgBmp)) }
$sheetW = 16 + $canvasPx + 16 + ($maskPx * 4 + 12 * 3) + 16
$sheetH = 16 + [Math]::Max(($canvasPx + $stripH), ($maskPx + 20)) + 16
$sheet = New-Object System.Drawing.Bitmap($sheetW, $sheetH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$sg = [System.Drawing.Graphics]::FromImage($sheet)
$sg.Clear([System.Drawing.Color]::FromArgb(255, 176, 176, 176))
$sg.DrawImage($tile, 16, 16, $canvasPx, ($canvasPx + $stripH))
$x = 16 + $canvasPx + 16
for ($i = 0; $i -lt $maskBitmaps.Count; $i++) {
    $sg.DrawImage($maskBitmaps[$i], $x, 16, $maskPx, $maskPx)
    $sg.DrawString($maskNames[$i].label, $labelFont, $labelBrush, $x, ($maskPx + 20))
    $x += $maskPx + 12
}
$sg.Dispose()
$adaptivePreview = Join-Path $ExportDir 'android\adaptive\preview.png'
$sheet.Save($adaptivePreview, [System.Drawing.Imaging.ImageFormat]::Png)
$sheet.Dispose(); $tile.Dispose(); $bgBmp.Dispose(); $fgBmp.Dispose()
foreach ($b in $maskBitmaps) { $b.Dispose() }
$labelFont.Dispose(); $labelBrush.Dispose(); $safeBrush.Dispose(); $vpPen.Dispose(); $safePen.Dispose()

$safeText = if ($safeOk) { 'OK' } else { 'FAIL' }
Write-Output ("android  adaptive: 5 密度 × 3 层 + mipmap-anydpi-v26/*.xml + values; 前景最大半径 {0:0.0}dp / 安全区 33.0dp  [{1}]" -f $maxDp, $safeText)

# --------------------------------------------------------- 交付：macOS .icns（+ iconutil 用的 .iconset）
# .icns 结构：'icns' + 文件总长(大端 4B) + 若干块（类型 4B ASCII + 块长 4B 大端(含 8B 头) + PNG 数据）。
# 块类型与尺寸（Apple 规范）：icp4=16 icp5=32 icp6=64 ic07=128 ic08=256 ic09=512 ic10=1024
#                                ic11=32(16@2x) ic12=64(32@2x) ic13=256(128@2x) ic14=512(256@2x)
function Get-PngBytes([System.Drawing.Bitmap]$bmp) {
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    $bytes = $ms.ToArray()
    $ms.Dispose()
    return $bytes
}
function Get-BigEndian([int]$value) {
    return [byte[]]@([byte](($value -shr 24) -band 0xFF), [byte](($value -shr 16) -band 0xFF), [byte](($value -shr 8) -band 0xFF), [byte]($value -band 0xFF))
}
function Write-Icns([string]$path, $chunks) {
    $parts = New-Object System.Collections.ArrayList
    $total = 8
    foreach ($c in $chunks) {
        $bmp = New-SudokuIcon $c.size
        $data = Get-PngBytes $bmp
        $bmp.Dispose()
        [void]$parts.Add(@{ type = $c.type; data = $data })
        $total += 8 + $data.Length
    }
    $fs = [System.IO.File]::Create($path)
    $fs.Write([byte[]][char[]]'icns', 0, 4)
    $fs.Write((Get-BigEndian $total), 0, 4)
    foreach ($p in $parts) {
        $fs.Write([byte[]][char[]]$p.type, 0, 4)
        $fs.Write((Get-BigEndian (8 + $p.data.Length)), 0, 4)
        $fs.Write($p.data, 0, $p.data.Length)
    }
    $fs.Flush(); $fs.Close(); $fs.Dispose()
}
$icnsChunks = @(
    @{ type = 'icp4'; size = 16 }, @{ type = 'icp5'; size = 32 }, @{ type = 'icp6'; size = 64 },
    @{ type = 'ic07'; size = 128 }, @{ type = 'ic08'; size = 256 }, @{ type = 'ic09'; size = 512 },
    @{ type = 'ic10'; size = 1024 }, @{ type = 'ic11'; size = 32 }, @{ type = 'ic12'; size = 64 },
    @{ type = 'ic13'; size = 256 }, @{ type = 'ic14'; size = 512 }
)
$macosDir = Join-Path $ExportDir 'macos'
Ensure-Dir $macosDir
Write-Icns (Join-Path $macosDir 'SudokuInk.icns') $icnsChunks
Write-Icns (Join-Path $RuntimeDir 'SudokuInk.icns') $icnsChunks

# .iconset：官方 iconutil 的输入目录（macOS 上 `iconutil -c icns SudokuInk.iconset`）
$iconsetDir = Join-Path $macosDir 'SudokuInk.iconset'
Ensure-Dir $iconsetDir
$iconset = @(
    @{ name = 'icon_16x16.png'; size = 16 }, @{ name = 'icon_16x16@2x.png'; size = 32 },
    @{ name = 'icon_32x32.png'; size = 32 }, @{ name = 'icon_32x32@2x.png'; size = 64 },
    @{ name = 'icon_128x128.png'; size = 128 }, @{ name = 'icon_128x128@2x.png'; size = 256 },
    @{ name = 'icon_256x256.png'; size = 256 }, @{ name = 'icon_256x256@2x.png'; size = 512 },
    @{ name = 'icon_512x512.png'; size = 512 }, @{ name = 'icon_512x512@2x.png'; size = 1024 }
)
foreach ($i in $iconset) { Export-Size $i.size $iconsetDir $i.name }
Write-Output ("macos    .icns 11 块 -> assets/icon/macos + composeApp/icons（另出 .iconset 10 档供 iconutil 复核）")

$srcDir = Join-Path $ExportDir 'source'
Export-Size 1024 $srcDir 'sudoku-1024.png'

# 矢量源文件（同一套比例的 SVG，便于放大、改色或交设计师二次编辑）
$v = 1024.0
$tilePadSvg = $v * 0.055; $tileSideSvg = $v - 2 * $tilePadSvg; $radiusSvg = $v * 0.185
$gridPadSvg = $v * 0.19; $gridSideSvg = $v - 2 * $gridPadSvg; $cellSvg = $gridSideSvg / 3.0
$frameSvg = $v * 0.030; $innerSvg = $v * 0.020; $fontSvg = $cellSvg * 0.62
$svg = New-Object System.Text.StringBuilder
[void]$svg.AppendLine('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">')
[void]$svg.AppendLine('  <title>数独 Sudoku · 手写纸（应用图标）</title>')
[void]$svg.AppendLine(('  <rect x="{0:0.##}" y="{0:0.##}" width="{1:0.##}" height="{1:0.##}" rx="{2:0.##}" fill="#F7F5F0" stroke="#1B1A17" stroke-opacity="0.59" stroke-width="{3:0.##}"/>' -f $tilePadSvg, $tileSideSvg, $radiusSvg, ($v / 70.0)))
$gx = $gridPadSvg
[void]$svg.AppendLine('  <g stroke="#1B1A17" fill="none">')
[void]$svg.AppendLine(('    <rect x="{0:0.##}" y="{0:0.##}" width="{1:0.##}" height="{1:0.##}" stroke-width="{2:0.##}"/>' -f $gx, $gx, $gridSideSvg, $frameSvg))
[void]$svg.AppendLine(('    <path d="M {0:0.##} {1:0.##} V {2:0.##} M {3:0.##} {1:0.##} V {2:0.##} M {1:0.##} {0:0.##} H {2:0.##} M {1:0.##} {4:0.##} H {2:0.##}" stroke-width="{5:0.##}"/>' -f ($gx + $cellSvg), $gx, ($gx + $gridSideSvg), ($gx + 2 * $cellSvg), ($gx + 2 * $cellSvg), $innerSvg))
[void]$svg.AppendLine('  </g>')
[void]$svg.AppendLine(('  <g font-family="Segoe UI, Microsoft YaHei UI, Arial, sans-serif" font-weight="700" text-anchor="middle" font-size="{0:0.##}">' -f $fontSvg))
function Get-SvgTextY([double]$row) { return ($gridPadSvg + $row * $cellSvg + $cellSvg / 2.0 + $fontSvg * 0.36) }
foreach ($d in $cornerDigits) {
    [void]$svg.AppendLine(('    <text x="{0:0.##}" y="{1:0.##}" fill="#1B1A17">{2}</text>' -f ($gridPadSvg + $d.col * $cellSvg + $cellSvg / 2.0), (Get-SvgTextY $d.row), $d.text))
}
[void]$svg.AppendLine(('    <text x="{0:0.##}" y="{1:0.##}" fill="#4B4843" fill-opacity="0.67">{2}</text>' -f ($gridPadSvg + $cellSvg * 1.5), (Get-SvgTextY 1), $centerDigit.text))
[void]$svg.AppendLine('  </g>')
[void]$svg.AppendLine('</svg>')
Ensure-Dir $srcDir
[System.IO.File]::WriteAllText((Join-Path $srcDir 'sudoku.svg'), $svg.ToString(), (New-Object System.Text.UTF8Encoding($false)))

# 校验用预览图：上排原始尺寸（16→256），下排把 16/24/32/48 放大到**同一显示尺寸**便于逐档判断
$previewPath = Join-Path $ExportDir 'preview.png'
Ensure-Dir $ExportDir
$sizes = @(16, 24, 32, 48, 64, 128, 256)
$zoomSizes = @(16, 24, 32, 48)
$gap = 16; $row1 = 256; $row2 = 288      # 下排每个都放大到 ~288px 显示
$sheetW = $gap + (($sizes | ForEach-Object { $_ + $gap }) | Measure-Object -Sum).Sum
$sheetH = $gap + $row1 + $gap + $row2 + $gap
$sheet = New-Object System.Drawing.Bitmap($sheetW, $sheetH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$sg = [System.Drawing.Graphics]::FromImage($sheet)
$sg.Clear([System.Drawing.Color]::FromArgb(255, 176, 176, 176))
$icons = New-Object System.Collections.ArrayList
$x = $gap
foreach ($s in $sizes) {
    $b = New-SudokuIcon $s
    [void]$icons.Add($b)
    $sg.DrawImage($b, $x, ($gap + $row1 - $s), $s, $s)
    $x += $s + $gap
}
$sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$sg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$x = $gap
foreach ($s in $zoomSizes) {
    $scaled = [int]($s * [Math]::Floor($row2 / $s))
    $sg.DrawImage($icons[$sizes.IndexOf($s)], $x, ($gap + $row1 + $gap + $row2 - $scaled), $scaled, $scaled)
    $x += $scaled + $gap
}
$sg.Dispose(); $sheet.Save($previewPath, [System.Drawing.Imaging.ImageFormat]::Png)
foreach ($b in $icons) { $b.Dispose() }
$sheet.Dispose()

$exportCount = (Get-ChildItem $ExportDir -Recurse -File | Measure-Object).Count
Write-Output ("export   " + $ExportDir + "  " + $exportCount + " 个文件（desktop / android + adaptive / ios / macos / web / source + 两张预览）")
Write-Output ("preview  " + $previewPath + "（上排 16→256 原始尺寸，下排 16/24/32/48 放大到同一显示尺寸）")
Write-Output ("preview  " + $adaptivePreview + "（108dp 画布 + 72dp 视口 + 66dp 安全圆，右侧四种掩膜下的实际显示）")
