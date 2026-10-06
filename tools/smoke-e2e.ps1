# smoke-e2e.ps1 -- real-app smoke test for the packaged desktop build.
#
# Why this exists: automated verification that injects keys/clicks through the OS
# (SendKeys / keybd_event / PostMessage / SetCursorPos) only works when the app
# really owns the foreground. On a desktop with other windows open the app can
# lose focus mid-run, and the injected input lands on somebody else's window.
# The fix used here:
#   1. pin the app window with HWND_TOPMOST at a fixed rect (default 100,100 1180x900),
#   2. assert GetForegroundWindow() == app window before EVERY injected key/click,
#      abort the run if it is not (never blind-fire input),
#   3. screenshot by window rect, so the capture is always the app.
#
# Covered flows:
#   menu    -- difficulty drawer: Enter opens, Down x2 highlights Hard, Enter starts Hard
#   exit    -- exit drawer: confirm quit really exits the process
#   win     -- win drawer: seeded "one empty cell" save -> Hint fills it -> win drawer -> Esc to menu
#
# The script backs up and restores the player save (~/.sudoku-ink/save.txt) around
# the win flow, and kills any leftover process on exit.
#
# Usage:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools\smoke-e2e.ps1
#   powershell ... -File tools\smoke-e2e.ps1 -Flow menu
#   (build first: gradlew.bat :composeApp:createDistributable --offline)
#
# NOTE: keep this file ASCII-only -- Windows PowerShell 5.1 reads BOM-less files
# as ANSI and non-ASCII characters will break parsing.
[CmdletBinding()]
param(
    [string]$ExePath = 'D:\LESSON\sudoku\composeApp\build\compose\binaries\main\app\SudokuInk\SudokuInk.exe',
    [int]$Left = 100,
    [int]$Top = 100,
    [int]$Width = 1180,
    [int]$Height = 900,
    [string]$OutDir = $env:TEMP,
    [ValidateSet('all', 'menu', 'exit', 'win')]
    [string]$Flow = 'all'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public struct RectE2E { public int L; public int T; public int R; public int B; }
public class WinE2E {
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RectE2E r);
    [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint f);
    [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool BringWindowToTop(IntPtr h);
    [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
    [DllImport("user32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint a, uint b, bool attach);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, IntPtr e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr e);
    [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint c, uint m);
}
'@

$script:HWND_TOPMOST = [IntPtr](-1)
$script:shot = 0
$script:VK = @{
    Enter = 0x0D; Escape = 0x1B; Space = 0x20; Hint = 0x48
    Down = 0x28; Up = 0x26; Left = 0x27; Right = 0x25
    Digit9 = 0x39
}
$script:KeyExtended = 0x0001   # KEYEVENTF_EXTENDEDKEY -- arrow keys need it to read as arrow keys

function Pin($p) {
    [WinE2E]::SetWindowPos($p.MainWindowHandle, $script:HWND_TOPMOST, $Left, $Top, $Width, $Height, 0x0040) | Out-Null
    Start-Sleep -Milliseconds 400
}

function Assert-Foreground($p) {
    $h = $p.MainWindowHandle
    Pin $p
    if ([WinE2E]::GetForegroundWindow() -ne $h) {
        $pid2 = 0
        $wtid = [WinE2E]::GetWindowThreadProcessId($h, [ref]$pid2)
        [WinE2E]::AttachThreadInput([WinE2E]::GetCurrentThreadId(), $wtid, $true) | Out-Null
        [WinE2E]::BringWindowToTop($h) | Out-Null
        [WinE2E]::SetForegroundWindow($h) | Out-Null
        [WinE2E]::AttachThreadInput([WinE2E]::GetCurrentThreadId(), $wtid, $false) | Out-Null
        Start-Sleep -Milliseconds 250
    }
    if ([WinE2E]::GetForegroundWindow() -ne $h) {
        throw ('FOREGROUND LOST - aborting before injecting input (would hit another window)')
    }
}

function Save-Shot($p, [string]$name) {
    $script:shot = $script:shot + 1
    $idx = $script:shot
    $r = New-Object RectE2E
    [WinE2E]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
    $w = $r.R - $r.L
    $h = $r.B - $r.T
    $bmp = New-Object System.Drawing.Bitmap -ArgumentList @($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($r.L, $r.T, 0, 0, (New-Object System.Drawing.Size -ArgumentList @($w, $h)))
    $g.Dispose()
    $file = Join-Path $OutDir ("smoke_$idx" + "_$name.png")
    $bmp.Save($file, [System.Drawing.Imaging.ImageFormat]::Png)
    $bmp.Dispose()
    Write-Host ("shot -> $file")
}

function Send-Key($p, [string]$key, [int]$flags = 0, [int]$waitMs = 550) {
    Assert-Foreground $p
    $vk = $script:VK[$key]
    if ($null -eq $vk) { throw ("unknown key name: " + $key) }
    $scan = [byte][WinE2E]::MapVirtualKey([uint32]$vk, 0)
    [WinE2E]::keybd_event([byte]$vk, $scan, [uint32]$flags, [IntPtr]0)
    Start-Sleep -Milliseconds 70
    [WinE2E]::keybd_event([byte]$vk, $scan, [uint32]($flags -bor 2), [IntPtr]0)
    Start-Sleep -Milliseconds $waitMs
}

function Send-Click($p, [int]$x, [int]$y, [int]$waitMs = 700) {
    Assert-Foreground $p
    $r = New-Object RectE2E
    [WinE2E]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
    [WinE2E]::SetCursorPos(($r.L + $x), ($r.T + $y)) | Out-Null
    Start-Sleep -Milliseconds 200
    [WinE2E]::mouse_event(0x0002, 0, 0, 0, [IntPtr]0)
    [WinE2E]::mouse_event(0x0004, 0, 0, 0, [IntPtr]0)
    Start-Sleep -Milliseconds $waitMs
}

function Start-App {
    if (-not [System.IO.File]::Exists($ExePath)) {
        throw ("exe not found: " + $ExePath + " -- run: gradlew.bat :composeApp:createDistributable --offline")
    }
    Start-Process -FilePath $ExePath
    $p = $null
    for ($n = 0; $n -lt 40; $n++) {
        Start-Sleep -Milliseconds 600
        $p = Get-Process SudokuInk -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle } | Select-Object -First 1
        if ($p) { break }
    }
    if (-not $p) { throw ('window did not appear') }
    Start-Sleep -Milliseconds 1200
    Pin $p
    return $p
}

function Stop-App($p) {
    if ($p -and -not $p.HasExited) { Stop-Process -Id $p.Id -Force }
    Start-Sleep -Milliseconds 900
}

$SIZE = 81
function Write-OneEmptySave([string]$path) {
    # a complete, valid sudoku grid with only the last cell blank
    $sol = '534678912672195348198342567859761423426853791713924856961537284287419635345286179'
    if ($sol.Length -ne $SIZE) { throw ('bad seed grid') }
    $puz = $sol.Substring(0, 80) + '0'
    $notes = (1..$SIZE | ForEach-Object { '0' }) -join ','
    $lines = @('v=2', 'strict=0', 'showNotes=1', 'noteMode=0', 'hintCandidates=0', 'game=1', 'difficulty=Easy')
    $lines += ('puzzle=' + $puz)
    $lines += ('current=' + $puz)
    $lines += ('solution=' + $sol)
    $lines += ('notes=' + $notes)
    $lines += 'elapsed=125'
    [System.IO.File]::WriteAllText($path, (($lines -join "`r`n") + "`r`n"), (New-Object System.Text.UTF8Encoding($false)))
}

$saveFile = Join-Path $env:USERPROFILE '.sudoku-ink\save.txt'
$saveBackup = $saveFile + '.smoke-bak'
if ([System.IO.File]::Exists($saveBackup)) { [System.IO.File]::Delete($saveBackup) }
if ([System.IO.File]::Exists($saveFile)) { [System.IO.File]::Move($saveFile, $saveBackup) }

$exitCode = 0
try {
    if ($Flow -eq 'all' -or $Flow -eq 'menu') {
        Write-Host '== flow: menu / difficulty drawer =='
        $p = Start-App
        Send-Key $p 'Enter'                       # open the difficulty drawer
        Save-Shot $p 'difficulty_drawer'
        Send-Key $p 'Down' $script:KeyExtended    # highlight "Normal"
        Send-Key $p 'Down' $script:KeyExtended    # highlight "Hard"
        Save-Shot $p 'difficulty_hard_highlight'
        Send-Key $p 'Enter'                       # start the Hard game
        Save-Shot $p 'game_hard'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'exit') {
        Write-Host '== flow: exit drawer =='
        if ([System.IO.File]::Exists($saveFile)) { [System.IO.File]::Delete($saveFile) }   # fresh start: resume is greyed out
        $p = Start-App
        Send-Key $p 'Down' $script:KeyExtended    # skips the disabled entry, lands on "Exit game"
        Send-Key $p 'Enter'                       # open the exit drawer
        Save-Shot $p 'exit_drawer'
        Send-Key $p 'Down' $script:KeyExtended    # highlight "Quit"
        Send-Key $p 'Enter'                       # really quit
        Start-Sleep -Milliseconds 1500
        $alive = Get-Process SudokuInk -ErrorAction SilentlyContinue
        Write-Host ('exit flow: process alive after confirm = ' + ($null -ne $alive))
        if ($alive) { Stop-Process -Id $alive.Id -Force }
    }

    if ($Flow -eq 'all' -or $Flow -eq 'win') {
        Write-Host '== flow: win drawer =='
        Write-OneEmptySave $saveFile
        $p = Start-App
        Send-Key $p 'Down' $script:KeyExtended    # "Resume game" (enabled now that a save exists)
        Send-Key $p 'Enter'                       # load it
        Save-Shot $p 'board_one_empty'
        Send-Key $p 'Hint'                        # fills the last empty cell -> win
        Save-Shot $p 'win_drawer'
        Send-Key $p 'Escape'                      # drawer Esc -> back to menu
        Save-Shot $p 'menu_after_escape'
        Stop-App $p
    }
} catch {
    Write-Host ('SMOKE FAILED: ' + $_.Exception.Message)
    $exitCode = 1
} finally {
    if ([System.IO.File]::Exists($saveFile)) { [System.IO.File]::Delete($saveFile) }
    if ([System.IO.File]::Exists($saveBackup)) { [System.IO.File]::Move($saveBackup, $saveFile) }
    Get-Process SudokuInk -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host ('save restored, screenshots in ' + $OutDir)
}
exit $exitCode
