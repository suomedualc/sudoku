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
      assets/icon/                 交付用（各平台规格 + 源文件）
        desktop/  android/  ios/  web/  source/

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
    按尺寸画出图标。几何比例（相对画布边长 S）：
      纸面内缩 0.055S，圆角 0.185S；九宫格外框内缩 0.19S（占 0.62S）；
      外框线宽 max(1.2, 0.030S)，内线 max(0.9, 0.020S)；
      数字字号 = 0.62 × 格宽（格宽 = 0.62S / 3 ≈ 0.207S），随尺寸保底 4px。
#>
function New-SudokuIcon([int]$size, [bool]$withCenter = $true) {
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

    # 九宫格：小尺寸把网格放大一点（0.19S → 0.15S），让数字有落脚处
    $gridPad = [single]($s * $(if ($size -lt 48) { 0.15 } else { 0.19 }))
    $gridSide = [single]($s - 2.0 * $gridPad)
    $cell = [double]$gridSide / 3.0
    $inkBrush = New-Object System.Drawing.SolidBrush($ink)
    $filledBrush = New-Object System.Drawing.SolidBrush($filled)

    $frameWidth = [single]([Math]::Max(1.2, $s * 0.030))
    $innerWidth = [single]([Math]::Max(0.9, $s * 0.020))
    $framePen = New-Object System.Drawing.Pen($ink, $frameWidth)
    $framePen.Alignment = [System.Drawing.Drawing2D.PenAlignment]::Inset
    $innerPen = New-Object System.Drawing.Pen($ink, $innerWidth)

    for ($i = 1; $i -le 2; $i++) {
        $p = [single]($gridPad + $i * $cell)
        $g.DrawLine($innerPen, $p, $gridPad, $p, [single]($gridPad + $gridSide))
        $g.DrawLine($innerPen, $gridPad, $p, [single]($gridPad + $gridSide), $p)
    }
    $g.DrawRectangle($framePen, [float]$gridPad, [float]$gridPad, [float]$gridSide, [float]$gridSide)

    # 数字：字号 = 0.62 × 格宽（图标里数字要"顶格"才清楚），且**永不超过格子的 75%**（否则会溢出到格线外）
    $fontSize = [single]([Math]::Min([Math]::Max(3.0, $cell * 0.62), $cell * 0.75))
    $font = New-InkTextureFont $fontSize ([System.Drawing.FontStyle]::Bold)
    # 用 GenericTypographic 量字（含 padding 更小），再手工居中——比 StringFormat 居中更贴合视觉中心
    $format = [System.Drawing.StringFormat]::GenericTypographic

    $drawDigit = {
        param($digit, $brush)
        $measure = $g.MeasureString($digit.text, $font, 1000, $format)
        $x = [single]($gridPad + $digit.col * $cell + ($cell - $measure.Width) / 2.0)
        $y = [single]($gridPad + $digit.row * $cell + ($cell - $measure.Height) / 2.0)
        $g.DrawString($digit.text, $font, $brush, $x, $y, $format)
    }

    # 细节随尺寸递减：见 docs/06 的"细节层级"表
    $showCenter = $withCenter -and $size -ge 128
    $showAllCorners = $size -ge 64
    $showTwo = $size -ge 32

    if ($showAllCorners) {
        foreach ($d in $cornerDigits) { & $drawDigit $d $inkBrush }
    } elseif ($showTwo) {
        & $drawDigit $cornerDigits[0] $inkBrush   # 左上 5
        & $drawDigit $cornerDigits[3] $inkBrush   # 右下 7
    } else {
        & $drawDigit $cornerDigits[0] $inkBrush   # 16px：只留左上一位，避免糊成一团
    }
    if ($showCenter) { & $drawDigit $centerDigit $filledBrush }

    $font.Dispose(); $inkBrush.Dispose(); $filledBrush.Dispose()
    $framePen.Dispose(); $innerPen.Dispose(); $edgePen.Dispose(); $paperBrush.Dispose()
    $tilePath.Dispose(); $g.Dispose()
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
foreach ($a in $android) { Export-Size $a.size $androidDir $a.name }

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
Set-Content -Path (Join-Path $srcDir 'sudoku.svg') -Value $svg.ToString() -Encoding UTF8

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
Write-Output ("export   " + $ExportDir + "  " + $exportCount + " 个文件（desktop / android / ios / web / source + preview.png）")
Write-Output ("preview  " + $previewPath + "（上排 16→256 原始尺寸，下排 16/24/32 放大 6 倍）")
