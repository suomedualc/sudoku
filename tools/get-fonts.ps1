# get-fonts.ps1 -- download and install the bundled fonts (LXGW WenKai + Nunito).
#
# Why this exists: the fonts are third-party binaries, not generated here, but their
# provenance (source + version + license) must be reproducible. This pins the exact
# release and drops the files next to the app so the bundling is audit-able.
#
# Sources (both SIL OFL 1.1):
#   LXGW WenKai v1.522  https://github.com/lxgw/LxgwWenKai/releases/tag/v1.522
#   Nunito              https://github.com/google/fonts/tree/main/ofl/nunito
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\get-fonts.ps1
#
# NOTE: keep this file ASCII-only (Windows PowerShell 5.1 reads BOM-less files as ANSI).
[CmdletBinding()]
param(
    [string]$Root = (Join-Path $PSScriptRoot '..\composeApp\src\jvmMain\resources\fonts')
)

$ErrorActionPreference = 'Stop'
$Root = [System.IO.Path]::GetFullPath($Root)
if (-not [System.IO.Directory]::Exists($Root)) { throw ("fonts dir not found: " + $Root) }

$targets = @(
    @{
        Url   = 'https://github.com/lxgw/LxgwWenKai/releases/download/v1.522/LXGWWenKai-Regular.ttf'
        Out   = 'LXGWWenKai-Regular.ttf'
    },
    @{
        Url   = 'https://raw.githubusercontent.com/google/fonts/main/ofl/nunito/Nunito%5Bwght%5D.ttf'
        Out   = 'Nunito.ttf'
    },
    @{
        Url   = 'https://raw.githubusercontent.com/lxgw/LxgwWenKai/main/OFL.txt'
        Out   = 'OFL-LXGWWenKai.txt'
    },
    @{
        Url   = 'https://raw.githubusercontent.com/google/fonts/main/ofl/nunito/OFL.txt'
        Out   = 'OFL-Nunito.txt'
    }
)

foreach ($t in $targets) {
    $dest = Join-Path $Root $t.Out
    Write-Output ("downloading " + $t.Out)
    curl.exe -L --fail --silent --show-error -o $dest $t.Url
    if ($LASTEXITCODE -ne 0) { throw ("download failed: " + $t.Url) }
    $kb = [Math]::Round((Get-Item $dest).Length / 1KB, 1)
    Write-Output ("  -> " + $dest + "  (" + $kb + " KB)")
}

Write-Output 'done. commit the new/updated files under resources/fonts/ (keep the OFL files next to the fonts).'
