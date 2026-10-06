<#
.SYNOPSIS
    生成应用图标（纸墨风格，按尺寸自适应），供 jpackage 打包使用。

.DESCRIPTION
    构图：透明的圆角纸面 + 墨线九宫格 + 主对角线上的实心墨格。
    **按尺寸自适应**是这版的关键——应用图标要在 16px 任务栏和 256px 安装界面里都说得清：
      · ≥ 40px：9×9 细线 + 每三格加粗的宫线 + 主对角线 5 枚实心墨格（有"数独盘子"的细节）；
      · < 40px：只留 1/3 与 2/3 两条宫线（相当于 3×3），实心墨格放大成中心一格，避免糊成一团；
      · ≥ 128px：网格线带确定性微弯（与 app 的 inkLine 同一思路，纯函数，不会"抖"）。
    圆角与四周透明边让它在深色/浅色任务栏、开始菜单、Alt+Tab 里都不糊边。

    产物：
      composeApp/icons/sudoku.ico      多尺寸（16/24/32/48/64/128/256），Windows MSI / app-image 用
      composeApp/icons/sudoku.png      256×256，Linux deb 用
      composeApp/icons/sudoku-NN.png   16/24/32/48/64/128 各一份 —— **运行时窗口 / 任务栏图标**用。
        为什么要单独存各尺寸：jpackage 只把图标嵌进 exe，AWT 窗口仍会用 JDK 的 Java 图标，
        必须在代码里显式设置（`main.kt` 的 `window.iconImages`）；按尺寸各取一份比缩放一份更清晰。
        这些 PNG 通过 `build.gradle.kts` 的 `jvmMain { resources.srcDir("icons") }` 打进 jar。
      预览图默认写到 %TEMP%（用于人工校验，不入库）
    macOS 的 .icns 需要 iconutil（仅 macOS 提供），未纳入本脚本。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/make-icon.ps1
#>
param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\composeApp\icons'),
    [string]$PreviewPath = (Join-Path $env:TEMP 'sudoku_icon_preview.png')
)

Add-Type -AssemblyName System.Drawing

$paper = [System.Drawing.Color]::FromArgb(255, 247, 245, 240)
$ink = [System.Drawing.Color]::FromArgb(255, 27, 26, 23)

# 圆角矩形路径（System.Drawing 没有现成的）
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

# 确定性抖动（纯函数）：同一个 seed 永远得到同一个偏移，重绘结果不变
function Get-Wobble([int]$seed, [double]$scale) {
    if ($scale -le 0) { return 0.0 }
    $raw = ($seed * 7919 + 104729) % 1000
    return (($raw / 1000.0) - 0.5) * 2.0 * $scale
}

function New-InkIcon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear([System.Drawing.Color]::Transparent)

    # --- 纸面（圆角 + 淡墨描边） ---
    $tilePad = [single]([Math]::Max(0.5, [double]$size * 0.055))
    $tileSide = [single]([double]$size - 2.0 * $tilePad)
    $radius = [single]([Math]::Max(1.0, [double]$size * 0.17))
    $tile = New-RoundedPath $tilePad $tilePad $tileSide $tileSide $radius
    $paperBrush = New-Object System.Drawing.SolidBrush($paper)
    $g.FillPath($paperBrush, $tile)
    $borderPen = New-Object System.Drawing.Pen(([System.Drawing.Color]::FromArgb(60, $ink)), [single]([Math]::Max(0.6, [double]$size / 60.0)))
    $g.DrawPath($borderPen, $tile)

    # --- 网格区域 ---
    $gridPad = [single]([double]$size * 0.20)
    $side = [single]([double]$size - 2.0 * [double]$gridPad)
    $cell = [double]$side / 9.0
    $detail = $size -ge 40
    $wobble = if ($size -ge 128) { [double]$size / 420.0 } else { 0.0 }

    $thinPen = New-Object System.Drawing.Pen(([System.Drawing.Color]::FromArgb(105, $ink)), [single]([Math]::Max(0.6, [double]$size / 150.0)))
    $boldPen = New-Object System.Drawing.Pen($ink, [single]([Math]::Max(0.9, [double]$size / 85.0)))

    for ($i = 1; $i -le 8; $i++) {
        if ($detail) {
            $pen = if (($i % 3) -eq 0) { $boldPen } else { $thinPen }
        } else {
            # 小尺寸只保留宫线（视觉上等于 3×3），否则 9 条线会糊成一团
            if (($i % 3) -ne 0) { continue }
            $pen = $boldPen
        }
        $p = [single]([double]$gridPad + $i * $cell)
        $o1 = [single](Get-Wobble ($i * 2) $wobble)
        $o2 = [single](Get-Wobble ($i * 2 + 1) $wobble)
        $g.DrawLine($pen, [single]($p + $o1), [single]([double]$gridPad + $o1), [single]($p + $o2), [single]([double]$gridPad + [double]$side + $o2))
        $g.DrawLine($pen, [single]([double]$gridPad + $o1), [single]($p + $o2), [single]([double]$gridPad + [double]$side + $o1), [single]($p + $o2))
    }

    # --- 实心墨格：大尺寸画主对角线 5 格，小尺寸画正中 1 格 ---
    $inkBrush = New-Object System.Drawing.SolidBrush($ink)
    if ($detail) {
        for ($k = 0; $k -le 8; $k += 2) {
            $box = New-RoundedPath `
                ([single]([double]$gridPad + $k * $cell + $cell * 0.10)) `
                ([single]([double]$gridPad + $k * $cell + $cell * 0.10)) `
                ([single]($cell * 0.80)) ([single]($cell * 0.80)) ([single]($cell * 0.12))
            $g.FillPath($inkBrush, $box)
            $box.Dispose()
        }
    } else {
        $box = New-RoundedPath `
            ([single]([double]$gridPad + $cell * 3.12)) `
            ([single]([double]$gridPad + $cell * 3.12)) `
            ([single]($cell * 2.76)) ([single]($cell * 2.76)) ([single]($cell * 0.45))
        $g.FillPath($inkBrush, $box)
        $box.Dispose()
    }

    # --- 外框（重墨，最后画，压住线头） ---
    $framePen = New-Object System.Drawing.Pen($ink, [single]([Math]::Max(1.1, [double]$size / 50.0)))
    $framePen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
    $g.DrawRectangle($framePen, [float]$gridPad, [float]$gridPad, [float]$side, [float]$side)

    $paperBrush.Dispose(); $borderPen.Dispose(); $thinPen.Dispose(); $boldPen.Dispose()
    $framePen.Dispose(); $inkBrush.Dispose()
    $tile.Dispose()
    $g.Dispose()
    return $bmp
}

if (-not (Test-Path $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}

$sizes = @(16, 24, 32, 48, 64, 128, 256)
$pngs = New-Object System.Collections.ArrayList
foreach ($s in $sizes) {
    $bmp = New-InkIcon $s
    $ms = New-Object System.IO.MemoryStream
    $bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
    [void]$pngs.Add($ms.ToArray())
    if ($s -eq 256) {
        # Linux deb 的图标（jpackage 需要一张 png）
        $bmp.Save((Join-Path $OutDir 'sudoku.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    } else {
        # 运行时窗口 / 任务栏图标：各尺寸各存一份，代码里按名单加载，避免缩放发虚
        $bmp.Save((Join-Path $OutDir ('sudoku-' + $s + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
    }
    $ms.Dispose()
    $bmp.Dispose()
}

# 组装 ICO：6 字节头 + 每张图 16 字节目录项 + 各尺寸 PNG 数据（Vista 起支持 PNG 条目）
$icoPath = Join-Path $OutDir 'sudoku.ico'
$stream = [System.IO.File]::Create($icoPath)
$writer = New-Object System.IO.BinaryWriter($stream)
$writer.Write([UInt16]0)
$writer.Write([UInt16]1)
$writer.Write([UInt16]$sizes.Count)
$offset = 6 + 16 * $sizes.Count
for ($i = 0; $i -lt $sizes.Count; $i++) {
    $dim = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }  # 目录项里 256 用 0 表示
    $data = $pngs[$i]
    $writer.Write([Byte]$dim)
    $writer.Write([Byte]$dim)
    $writer.Write([Byte]0)
    $writer.Write([Byte]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]32)
    $writer.Write([UInt32]$data.Length)
    $writer.Write([UInt32]$offset)
    $offset += $data.Length
}
foreach ($data in $pngs) { $writer.Write($data) }
$writer.Flush()
$writer.Dispose()
$stream.Dispose()
Write-Output ("生成 " + $icoPath + "  (" + (Get-Item $icoPath).Length + " 字节)")

$pngPath = Join-Path $OutDir 'sudoku.png'
Write-Output ("生成 " + $pngPath + "  (" + (Get-Item $pngPath).Length + " 字节)")
foreach ($s in $sizes) {
    if ($s -eq 256) { continue }
    $p = Join-Path $OutDir ('sudoku-' + $s + '.png')
    Write-Output ("生成 " + $p + "  (" + (Get-Item $p).Length + " 字节)")
}

# --- 预览图：上排原始尺寸、下排把小尺寸放大 6 倍，便于人工判断清晰度 ---
$gap = 16
$row1 = 256
$magnified = @(16, 24, 32)
$row2 = 32 * 6
$sheetW = $gap + ($sizes | ForEach-Object { $_ + $gap } | Measure-Object -Sum).Sum
$sheetH = $gap + $row1 + $gap + $row2 + $gap
$sheet = New-Object System.Drawing.Bitmap($sheetW, $sheetH, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$sg = [System.Drawing.Graphics]::FromImage($sheet)
$sg.Clear([System.Drawing.Color]::FromArgb(255, 176, 176, 176))

$x = $gap
$sheetIcons = New-Object System.Collections.ArrayList
foreach ($s in $sizes) {
    $bmp = New-InkIcon $s
    [void]$sheetIcons.Add($bmp)
    $sg.DrawImage($bmp, $x, ($gap + $row1 - $s), $s, $s)
    $x += $s + $gap
}

$sg.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$sg.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
$x = $gap
foreach ($s in $magnified) {
    $src = $sheetIcons[$sizes.IndexOf($s)]
    $scaled = $s * 6
    $sg.DrawImage($src, $x, ($gap + $row1 + $gap + $row2 - $scaled), $scaled, $scaled)
    $x += $scaled + $gap
}
$sg.Dispose()
$sheet.Save($PreviewPath, [System.Drawing.Imaging.ImageFormat]::Png)
foreach ($b in $sheetIcons) { $b.Dispose() }
$sheet.Dispose()
Write-Output ("生成预览图 " + $PreviewPath + "（上排 16→256 原始尺寸，下排 16/24/32 放大 6 倍）")
