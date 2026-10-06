# Requires -Version 5.1
# Prepares the bundled typography assets used by thedesktop build.
#
# Why this script exists: the app must render identically on every machine, so it can
# no longer depend on "some kai-serif the user happens to have installed". The assets
# below are GENERATED - never hand-edit them, regenerate with this script.
#
# Output (composeApp/src/jvmMain/resources/fonts/):
#   LXGWWenKaiSubset.ttf      CJK  - LXGW WenKai (霞鹜文楷), OFL 1.1, subset to the glyphs the UI can render
#   SourceSerifPro-Regular.ttf Latin + digits - Source Serif Pro Regular 400, OFL 1.1
#   （只需要 Regular：排版层级靠字号 / 字距 / 墨色，不用字重，见 docs/02 §2.3）
#   OFL-LXGWWenKai.txt         licence that must ship with the CJK binary
#   OFL-SourceSerifPro.txt     licence that must ship with the Latin binary
#   wenkai-chars.txt           the character inventory the subset was built from
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/prepare-fonts.ps1
#   powershell ... -File tools/prepare-fonts.ps1 -Force            # re-download everything
#   powershell ... -File tools/prepare-fonts.ps1 -NoSubset         # keep the full CJK font (debugging only)
#
# IMPORTANT this file must stay ASCII-only: PS 5.1 reads a script without BOM as ANSI,
# and multibyte characters break the parser.

param([switch]$Force, [switch]$NoSubset)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root 'composeApp\src\jvmMain\resources\fonts'
$tmp = Join-Path $env:TEMP 'sudoku-fonts'
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

function Valid-Font([string]$path) {
    if (-not (Test-Path $path)) { return $false }
    $b = [System.IO.File]::ReadAllBytes($path)
    if ($b.Length -lt 4) { return $false }
    $sig = [int]$b[0] * 16777216 + [int]$b[1] * 65536 + [int]$b[2] * 256 + [int]$b[3]
    return ($sig -eq 65536 -or $sig -eq 1330926671 -or $sig -eq 1953658214 -or $sig -eq 1953784678)
}

function Fetch([string]$url, [string]$dest) {
    try {
        Invoke-WebRequest -Uri $url -OutFile $dest -TimeoutSec 180
        return (Valid-Font $dest)
    } catch {
        Write-Output ('  skip    ' + $url + '  (' + $_.Exception.Message.Substring(0, [Math]::Min(60, $_.Exception.Message.Length)) + ')')
        return $false
    }
}

Write-Output '=== 1/4 CJK source: LXGW WenKai (霞鹜文楷) ==='
$cjkSrc = Join-Path $tmp 'LXGWWenKai-full.ttf'
$gotCjk = $false
foreach ($candidate in @(
    'C:\Windows\Fonts\LXGWWenKaiScreen.ttf',
    (Join-Path $env:LOCALAPPDATA 'Microsoft\Windows\Fonts\LXGWWenKaiScreen.ttf'),
    'C:\Windows\Fonts\LXGWWenKai-Regular.ttf',
    'C:\Windows\Fonts\LXGWWenKaiGBFusion-Regular.ttf')) {
    if ((Test-Path $candidate) -and (Valid-Font $candidate)) {
        Copy-Item $candidate $cjkSrc -Force
        Write-Output ('  copied  ' + $candidate + '  (' + [Math]::Round((Get-Item $candidate).Length / 1MB, 1) + ' MB)')
        $gotCjk = $true
        break
    }
}
if (-not $gotCjk) {
    Write-Output '  not installed locally, downloading the official release'
    $gotCjk = Fetch 'https://github.com/lxgw/LxgwWenKai/releases/download/v1.520/LXGWWenKai-Regular.ttf' $cjkSrc
}
if (-not $gotCjk) { throw 'LXGW WenKai unavailable - install it or check the network' }

Write-Output '=== 2/4 character inventory (only what the UI can actually draw) ==='
$chars = [System.Collections.Generic.HashSet[int]]::new()
foreach ($d in @('commonMain', 'jvmMain')) {
    $base = Join-Path $root ('composeApp\src\' + $d)
    if (-not (Test-Path $base)) { continue }
    foreach ($f in (Get-ChildItem -Path $base -Recurse -Filter '*.kt' -File)) {
        foreach ($ch in ([System.IO.File]::ReadAllText($f.FullName)).ToCharArray()) {
            $code = [int]$ch
            $keep = ($code -ge 0x3400 -and $code -le 0x9FFF) -or
                    ($code -ge 0xF900 -and $code -le 0xFAFF) -or
                    ($code -ge 0x3000 -and $code -le 0x303F) -or
                    ($code -ge 0xFF00 -and $code -le 0xFFEF) -or
                    ($code -ge 0x2010 -and $code -le 0x203B) -or
                    ($code -in @(0x00D7, 0x00B7, 0x2191, 0x2193, 0x2190, 0x2192))
            if ($keep) { [void]$chars.Add($code) }
        }
    }
}
foreach ($extra in @('…', '·', '×', '↑', '↓', '←', '→', '、', '。', '（', '）', '：', '！', '？', '《', '》')) {
    [void]$chars.Add([int][char]$extra)
}
$charText = ($chars | Sort-Object | ForEach-Object { [char]$_ }) -join ''
$charFile = Join-Path $tmp 'chars.txt'
[System.IO.File]::WriteAllText($charFile, $charText, (New-Object System.Text.UTF8Encoding($false)))
Write-Output ('  unique characters kept = ' + $chars.Count)

Write-Output '=== 3/4 subset the CJK font (18.6 MB -> a few hundred KB) ==='
$cjkOut = Join-Path $outDir 'LXGWWenKaiSubset.ttf'
if ($NoSubset) {
    Copy-Item $cjkSrc $cjkOut -Force
    Write-Output '  -NoSubset: copied the full font'
} else {
    # fonttools 4.6x 的 `-m fontTools.subset` 入口在这个环境下会把选项当字形名，
    # 走官方安装的 pyftsubset 本体最稳（用 sysconfig 定位，不写死 Python 版本）
    $scripts = ''
    try { $scripts = (& python -c "import sysconfig; print(sysconfig.get_path('scripts'))" 2>$null | Select-Object -Last 1).Trim() } catch { }
    $candidates = [System.Collections.ArrayList]::new()
    if ($scripts) { [void]$candidates.Add((Join-Path $scripts 'pyftsubset.exe')); [void]$candidates.Add((Join-Path $scripts 'pyftsubset')) }
    foreach ($d in @(Get-ChildItem -Path (Join-Path $env:APPDATA 'Python') -Directory -ErrorAction SilentlyContinue)) {
        [void]$candidates.Add((Join-Path $d.FullName 'Scripts\pyftsubset.exe'))
    }
    $toolOk = $false
    foreach ($tool in ($candidates | Where-Object { $_ -and (Test-Path $_) })) {
        Remove-Item $cjkOut -Force -ErrorAction SilentlyContinue
        & $tool $cjkSrc "--text-file=$charFile" "--output-file=$cjkOut" `
            '--layout-features=' '--name-IDs=*' '--drop-tables=DSIG' '--notdef-outline' 2>&1 |
            ForEach-Object { Write-Output ('    ' + $_) }
        if (Valid-Font $cjkOut) {
            Write-Output ('  tool    ' + $tool)
            $toolOk = $true
            break
        }
    }
    if (-not $toolOk) { throw 'subsetting failed - run: python -m pip install fonttools' }
    $before = [Math]::Round((Get-Item $cjkSrc).Length / 1MB, 1)
    $after = [Math]::Round((Get-Item $cjkOut).Length / 1MB, 2)
    Write-Output ('  subset ' + $before + ' MB -> ' + $after + ' MB')
}
Copy-Item $charFile (Join-Path $outDir 'wenkai-chars.txt') -Force

Write-Output '=== 4/4 Latin + digits: Source Serif Pro (OFL 1.1) ==='
$ssZip = Join-Path $tmp 'source-serif.zip'
$needZip = $Force -or -not (Test-Path (Join-Path $outDir 'SourceSerifPro-Regular.ttf'))
if ($needZip) {
    try {
        Invoke-WebRequest -Uri 'https://github.com/adobe-fonts/source-serif/releases/download/2.010R-ro%2F1.010R-it/source-serif-pro-2.010R-ro-1.010R-it.zip' -OutFile $ssZip -TimeoutSec 180
    } catch { Write-Output ('  download failed: ' + $_.Exception.Message) }
}
if (Test-Path $ssZip) {
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $arc = [System.IO.Compression.ZipFile]::OpenRead($ssZip)
    $prefix = 'source-serif-pro-2.010R-ro-1.010R-it/'
    foreach ($map in @(
        @{ from = $prefix + 'TTF/SourceSerifPro-Regular.ttf'; to = 'SourceSerifPro-Regular.ttf' })) {
        $entry = $arc.Entries | Where-Object { $_.FullName -eq $map.from }
        if ($entry) {
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, (Join-Path $outDir $map.to), $true)
            Write-Output ('  extracted ' + $map.to)
        }
    }
    $lic = $arc.Entries | Where-Object { $_.FullName -eq ($prefix + 'LICENSE.md') }
    if ($lic) {
        $ms = New-Object System.IO.MemoryStream
        $lic.Open().CopyTo($ms)
        [System.IO.File]::WriteAllBytes((Join-Path $outDir 'OFL-SourceSerifPro.txt'), $ms.ToArray())
        Write-Output '  extracted OFL-SourceSerifPro.txt'
    }
    $arc.Dispose()
}
foreach ($u in @(
    'https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/OFL.txt',
    'https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/LICENSE')) {
    try {
        Invoke-WebRequest -Uri $u -OutFile (Join-Path $outDir 'OFL-LXGWWenKai.txt') -TimeoutSec 40
        if ((Get-Item (Join-Path $outDir 'OFL-LXGWWenKai.txt')).Length -gt 500) { Write-Output '  fetched OFL-LXGWWenKai.txt'; break }
    } catch { }
}

Write-Output '=== result ==='
Get-ChildItem $outDir -File | Sort-Object Name |
    ForEach-Object { Write-Output ('  ' + $_.Name.PadRight(30) + ([Math]::Round($_.Length / 1KB, 1)).ToString().PadLeft(9) + ' KB') }
if (Test-Path $tmp) { Remove-Item $tmp -Recurse -Force }
Write-Output 'next: gradlew.bat :composeApp:createDistributable --offline'
