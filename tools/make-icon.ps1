<#
.SYNOPSIS
    生成应用图标（纸面 + 墨线九宫格），供 jpackage 打包使用。

.DESCRIPTION
    图标与界面同一套视觉语言：纸色底 + 墨线网格（宫线加粗）+ 几枚墨点。
    产物：
      composeApp/icons/sudoku.ico  多尺寸（16/24/32/48/64/128/256），Windows MSI / app-image 用
      composeApp/icons/sudoku.png  256×256，Linux deb 用

    图标属于"生成的资产"：只在视觉需要调整时重新运行本脚本即可，无需每次构建。
    macOS 的 .icns 需要 iconutil（仅 macOS 提供），未纳入本脚本。

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File tools/make-icon.ps1
#>
param(
    [string]$OutDir = (Join-Path $PSScriptRoot '..\composeApp\icons')
)

Add-Type -AssemblyName System.Drawing

$paper = [System.Drawing.Color]::FromArgb(255, 247, 245, 240)
$ink = [System.Drawing.Color]::FromArgb(255, 27, 26, 23)

function New-InkIcon([int]$size) {
    $bmp = New-Object System.Drawing.Bitmap($size, $size)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.Clear($paper)

    $pad = [Math]::Max(1.0, [double]$size * 0.085)
    $side = [double]$size - 2.0 * $pad
    $cell = $side / 9.0
    $thinWidth = [Math]::Max(1.0, [double]$size / 64.0)
    $boldWidth = [Math]::Max(1.4, [double]$size / 26.0)

    $thinPen = New-Object System.Drawing.Pen($ink, [float]$thinWidth)
    $boldPen = New-Object System.Drawing.Pen($ink, [float]$boldWidth)

    for ($i = 1; $i -le 8; $i++) {
        $pen = if (($i % 3) -eq 0) { $boldPen } else { $thinPen }
        $p = [float]($pad + $i * $cell)
        $g.DrawLine($pen, $p, [float]$pad, $p, [float]($pad + $side))
        $g.DrawLine($pen, [float]$pad, $p, [float]($pad + $side), $p)
    }
    $g.DrawRectangle($boldPen, [float]$pad, [float]$pad, [float]$side, [float]$side)

    $brush = New-Object System.Drawing.SolidBrush($ink)
    foreach ($cellPos in @(@(1, 2), @(3, 5), @(5, 1), @(6, 7), @(8, 4))) {
        $radius = [float]([Math]::Max(0.7, $cell * 0.22))
        $cx = [float]($pad + ($cellPos[1] + 0.5) * $cell)
        $cy = [float]($pad + ($cellPos[0] + 0.5) * $cell)
        $g.FillEllipse($brush, [float]($cx - $radius), [float]($cy - $radius), [float]($radius * 2), [float]($radius * 2))
    }

    $brush.Dispose()
    $thinPen.Dispose()
    $boldPen.Dispose()
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
        $bmp.Save((Join-Path $OutDir 'sudoku.png'), [System.Drawing.Imaging.ImageFormat]::Png)
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
    # 目录项里 256 用 0 表示
    $dim = if ($sizes[$i] -ge 256) { 0 } else { $sizes[$i] }
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
Write-Output ("生成 " + (Join-Path $OutDir 'sudoku.png') + "  (" + (Get-Item (Join-Path $OutDir 'sudoku.png')).Length + " 字节)")
