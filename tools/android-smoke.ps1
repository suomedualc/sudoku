# android-smoke.ps1 -- per-device emulator functional test for the sudoku Android build.
# ASCII-only script; Chinese UI strings come from android-smoke-strings.txt (UTF-8, read explicitly).
#
# Coverage: boot / install / cold start, menu render (corpus), start game, touch select,
# pad fill / erase, hint, pause veil, background auto-pause, kill-restore, night toggle,
# narrow-layout settings drawer + BACK-closes-drawer (D2), grid pad fill,
# BACK-at-game -> menu (D2), BACK-at-menu -> default exit.
#
# Usage (one device per invocation):
#   powershell -File tools\android-smoke.ps1 -AvdName Medium_Phone_API_35
#   add -ReportOnly to just rebuild the aggregated live report and exit (no emulator).
param(
    [Parameter(Mandatory = $true)][string]$AvdName,
    [string]$SdkRoot = 'D:\env\Android-SDK',
    [string]$Apk = 'D:\LESSON\sudoku\composeApp\build\outputs\apk\debug\composeApp-debug.apk',
    [string]$StringsFile = 'D:\LESSON\sudoku\tools\android-smoke-strings.txt',
    [string]$OutRoot = 'D:\LESSON\sudoku\build\android-test',
    [switch]$ReportOnly
)
$ErrorActionPreference = 'Continue'
# .android/.gradle/.vcpkg migrated to D: drive with C: junction fallback (2026-10-08);
# user-level env (ANDROID_AVD_HOME / ANDROID_USER_HOME / ANDROID_VENDOR_KEYS etc.) is set.
# This script no longer sets any env vars; it fully inherits the launching environment.
$adb = Join-Path $SdkRoot 'platform-tools\adb.exe'
$emu = Join-Path $SdkRoot 'emulator\emulator.exe'
$outDir = Join-Path $OutRoot $AvdName
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

# localized strings
$S = @{}
[System.IO.File]::ReadAllLines($StringsFile, [System.Text.Encoding]::UTF8) | ForEach-Object {
    if ($_ -match '^(\w+)=(.*)$') { $S[$Matches[1]] = $Matches[2] }
}

# ---- results archive + live report -----------------------------------------------
# results.csv (per device): append-only archive, one line per verdict, UTF8.
# report.html (shared): rebuilt from ALL */results.csv after EVERY verdict; the browser
# tab auto-refreshes every 3s, so progress / metrics / final verdicts are visible live.
$csvPath = Join-Path $outDir 'results.csv'
$reportPath = Join-Path $OutRoot 'report.html'
$caseOrder = @('BOOT','INSTALL','COLD_START','T1_MENU_RENDER','T2_START_GAME','T3_SELECT_CELL','T4_FILL_DIGIT','T5_ERASE','T6_HINT','T7_PAUSE','T7_RESUME','T8_BACKGROUND_PAUSE','T8_RESUME_AFTER_BG','T9_KILL_RESTORE','T10_NIGHT_TOGGLE','T11_SETTINGS_DRAWER','T11_BACK_CLOSES_DRAWER','T12_GRID_FILL','T13_BACK_TO_MENU','T14_BACK_MENU_EXIT','MEM_PSS')

function Esc([string]$s) {
    if (-not $s) { return '' }
    return ($s -replace '&', '&amp;' -replace '<', '&lt;' -replace '>', '&gt;')
}
function Case-Label([string]$case) {
    $k = 'LBL_' + $case
    if ($S.ContainsKey($k)) { return $S[$k] }
    return $case
}
# merge one device's csv into a case -> verdict map; strongest verdict wins per case
# (a case may record several lines on failure paths): PASS > FAIL > SKIP > INFO
function Merge-CaseRows([string]$dir) {
    $rows = @{}
    $csv = Join-Path $dir 'results.csv'
    if (-not (Test-Path $csv)) { return $rows }
    foreach ($ln in [System.IO.File]::ReadAllLines($csv, [System.Text.Encoding]::UTF8)) {
        if (-not $ln) { continue }
        $i1 = $ln.IndexOf(',')
        if ($i1 -lt 1) { continue }
        $case = $ln.Substring(0, $i1)
        $rest = $ln.Substring($i1 + 1)
        $i2 = $rest.IndexOf(',')
        if ($i2 -ge 0) { $status = $rest.Substring(0, $i2); $detail = $rest.Substring($i2 + 1) }
        else { $status = $rest; $detail = '' }
        $rank = @{ PASS = 4; FAIL = 3; SKIP = 2; INFO = 1 }[$status]
        if (-not $rank) { $rank = 0 }
        $old = $rows[$case]
        $oldRank = if ($old) { $old['rank'] } else { -1 }
        if ($rank -ge $oldRank) { $rows[$case] = @{ status = $status; detail = $detail; rank = $rank } }
    }
    return $rows
}
function Write-Report {
    $devDirs = @(Get-ChildItem $OutRoot -Directory -ErrorAction SilentlyContinue | Where-Object { Test-Path (Join-Path $_.FullName 'results.csv') } | Sort-Object Name)
    $maps = @{}
    foreach ($d in $devDirs) { $maps[$d.Name] = Merge-CaseRows $d.FullName }
    $sb = New-Object System.Text.StringBuilder
    [void]$sb.AppendLine('<!DOCTYPE html><html><head><meta charset="utf-8"><meta http-equiv="refresh" content="3"><title>' + (Esc $S['REPORT_TITLE']) + '</title>')
    [void]$sb.AppendLine('<style>body{font-family:Segoe UI,system-ui,sans-serif;background:#171512;color:#e8e0d0;margin:24px}h1{font-size:22px;font-weight:600;margin:0 0 6px}.sub{color:#a89f8d;font-size:12px;margin-bottom:20px}.cards{display:flex;gap:14px;flex-wrap:wrap;margin-bottom:22px}.card{background:#201d19;border:1px solid #35302a;border-radius:10px;padding:14px 18px;min-width:230px}.card h2{margin:0 0 8px;font-size:15px;color:#f0e9da}.state{font-size:11px;padding:2px 8px;border-radius:10px;margin-left:8px}.done{background:#2e4425;color:#a5d68a}.running{background:#443a25;color:#e5c07b}.metric{font-size:12px;color:#a89f8d;margin:3px 0}.metric b{color:#e8e0d0;font-weight:600}.vpass{color:#a5d68a;font-weight:700}.vfail{color:#ef9a8a;font-weight:700}.bar{height:6px;background:#2b2721;border-radius:3px;margin-top:8px;overflow:hidden}.bar i{display:block;height:100%;background:#7da271}.bar i.warn{background:#d98f5f}table{border-collapse:collapse;width:100%;max-width:1120px;background:#201d19;border:1px solid #35302a;border-radius:10px;overflow:hidden}th,td{padding:9px 14px;text-align:left;border-bottom:1px solid #2b2721;font-size:13px}th{background:#26221d;color:#a89f8d;font-size:12px;letter-spacing:1px}tr:last-child td{border-bottom:none}td.case{color:#f0e9da}.badge{display:inline-block;font-size:11px;font-weight:700;padding:2px 10px;border-radius:10px;letter-spacing:.5px}.PASS{background:#2e4425;color:#a5d68a}.FAIL{background:#4a2320;color:#ef9a8a}.SKIP{background:#33302b;color:#9c948a}.INFO{background:#1f3340;color:#8ec3e0}.small{color:#a89f8d;font-size:11px;margin-left:8px}</style></head><body>')
    [void]$sb.AppendLine('<h1>' + (Esc $S['REPORT_TITLE']) + '</h1>')
    [void]$sb.AppendLine('<div class="sub">' + (Esc $S['R_META']) + ' &middot; ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss') + '</div>')
    if ($devDirs.Count -eq 0) { [void]$sb.AppendLine('<div class="sub">' + (Esc $S['R_EMPTY']) + '</div>') }
    [void]$sb.AppendLine('<div class="cards">')
    foreach ($d in $devDirs) {
        $rows = $maps[$d.Name]
        $isDone = $rows.ContainsKey('T14_BACK_MENU_EXIT')
        $stateCls = 'done'; $stateTxt = $S['R_STATE_DONE']
        if (-not $isDone) { $stateCls = 'running'; $stateTxt = $S['R_STATE_RUNNING'] }
        $cold = '-'
        if ($rows.ContainsKey('COLD_START') -and ($rows['COLD_START']['detail'] -match 'TotalTime=(\d+)ms')) {
            $ms = [int]$Matches[1]; if ($ms -gt 0) { $cold = '' + $ms + ' ms' }
        }
        $mem = '-'
        if ($rows.ContainsKey('MEM_PSS') -and ($rows['MEM_PSS']['detail'] -match '(\d+)\s*kB')) {
            $mem = '' + [int]([int]$Matches[1] / 1024) + ' MB'
        }
        $doneN = 0; $failN = 0
        foreach ($c in $caseOrder) {
            if ($c -eq 'COLD_START' -or $c -eq 'MEM_PSS') { continue }
            if ($rows.ContainsKey($c)) {
                $doneN++
                if ($rows[$c]['status'] -eq 'FAIL') { $failN++ }
            }
        }
        $totalN = $caseOrder.Count - 2
        $pct = if ($totalN -gt 0) { [int](100 * $doneN / $totalN) } else { 0 }
        [void]$sb.AppendLine('<div class="card"><h2>' + (Esc $d.Name) + '<span class="state ' + $stateCls + '">' + (Esc $stateTxt) + '</span></h2>')
        [void]$sb.AppendLine('<div class="metric">' + (Esc $S['R_COLD']) + ' <b>' + $cold + '</b> &middot; ' + (Esc $S['R_MEM']) + ' <b>' + $mem + '</b></div>')
        [void]$sb.AppendLine('<div class="metric">' + (Esc $S['R_PROGRESS']) + ' <b>' + $doneN + '/' + $totalN + '</b></div>')
        if ($isDone) {
            $v = $S['R_ALLPASS']; $vc = 'vpass'
            if ($failN -gt 0) { $v = $S['R_HASFAIL'] + ' (' + $failN + ')'; $vc = 'vfail' }
            [void]$sb.AppendLine('<div class="metric ' + $vc + '">' + (Esc $v) + '</div>')
        }
        $barCls = ''; if ($failN -gt 0) { $barCls = ' class="warn"' }
        [void]$sb.AppendLine('<div class="bar"><i' + $barCls + ' style="width:' + $pct + '%"></i></div></div>')
    }
    [void]$sb.AppendLine('</div>')
    [void]$sb.AppendLine('<table><tr><th>' + (Esc $S['R_CASE']) + '</th>')
    foreach ($d in $devDirs) { [void]$sb.AppendLine('<th>' + (Esc $d.Name.Replace('_API_35', '')) + '</th>') }
    [void]$sb.AppendLine('</tr>')
    $known = New-Object System.Collections.Generic.List[string]
    foreach ($c in $caseOrder) { $known.Add($c) }
    foreach ($d in $devDirs) { foreach ($c in $maps[$d.Name].Keys) { if (-not $known.Contains($c)) { $known.Add($c) } } }
    foreach ($c in $known) {
        [void]$sb.AppendLine('<tr><td class="case">' + (Esc (Case-Label $c)) + '</td>')
        foreach ($d in $devDirs) {
            $rows = $maps[$d.Name]
            if ($rows.ContainsKey($c)) {
                $r = $rows[$c]
                [void]$sb.AppendLine('<td title="' + (Esc $r['detail']) + '"><span class="badge ' + $r['status'] + '">' + $r['status'] + '</span><span class="small">' + (Esc $r['detail']) + '</span></td>')
            } else {
                [void]$sb.AppendLine('<td><span class="small">-</span></td>')
            }
        }
        [void]$sb.AppendLine('</tr>')
    }
    [void]$sb.AppendLine('</table></body></html>')
    [System.IO.File]::WriteAllText($reportPath, $sb.ToString(), [System.Text.Encoding]::UTF8)
}

$script:results = New-Object System.Collections.Generic.List[string]
$script:sw = [System.Diagnostics.Stopwatch]::StartNew()
function Record($case, $status, $detail) {
    $line = "$case,$status,$detail"
    $script:results.Add($line)
    # incremental archive: each verdict lands in results.csv the moment it is decided
    Add-Content -Path $csvPath -Value $line -Encoding UTF8
    Write-Host ("  [{0} +{1:mm\:ss}] {2} :: {3}" -f $status, $script:sw.Elapsed, $case, $detail)
    Write-Report
}

if ($ReportOnly) { Write-Report; Write-Host 'report.html regenerated (ReportOnly)'; exit 0 }

# fresh empty csv -> the report shows this device running from the first second; open the report once
[System.IO.File]::WriteAllText($csvPath, '')
Write-Report
Start-Process $reportPath
function Shot($name) {
    # binary-safe: screencap to device file, then adb pull (PS text pipeline corrupts exec-out)
    $dev = "/sdcard/_shot_$name.png"
    & $adb shell screencap -p $dev 2>&1 | Out-Null
    & $adb pull $dev (Join-Path $outDir "$name.png") 2>&1 | Out-Null
    & $adb shell rm $dev 2>&1 | Out-Null
}
function Get-Dump {
    # integrity check: only accept a complete dump containing </hierarchy>
    # (Compose UI may return a stale/partial hierarchy when not fully idle)
    for ($i = 0; $i -lt 6; $i++) {
        & $adb shell uiautomator dump /sdcard/ui.xml 2>&1 | Out-Null
        Start-Sleep -Milliseconds 600
        $xml = (& $adb shell cat /sdcard/ui.xml 2>&1 | Out-String)
        if ($xml -and $xml.Length -gt 100 -and $xml.Contains('</hierarchy>')) { return $xml }
        Start-Sleep -Milliseconds 900
    }
    return ''
}
# find the (n-th from top/bottom) node whose text or content-desc equals/contains $needle; returns center x,y or $null
# two-stage match: first capture the whole <node .../> tag ([^>] crosses quotes), then extract bounds
# from within the captured tag. A single regex with [^"]* between text and bounds can never match,
# because uiautomator XML has quoted attributes (resource-id="" etc.) in between.
# -Largest: pick the match with the biggest bounds area — pad key FACES and their little
# remaining-count BADGES share the same single-digit text ("5" badge on key 2 when 5 twos
# remain), so tree order alone misfires; the key face is always by far the largest node.
function Find-Node([string]$dump, [string]$needle, [switch]$ByDesc, [switch]$BottomMost, [switch]$Exact, [switch]$Largest) {
    $attr = if ($ByDesc) { 'content-desc' } else { 'text' }
    $escaped = [System.Text.RegularExpressions.Regex]::Escape($needle)
    $nodePattern = if ($Exact) {
        '<node[^>]*?' + $attr + '="' + $escaped + '"[^>]*?>'
    } else {
        '<node[^>]*?' + $attr + '="[^"]*' + $escaped + '[^"]*"[^>]*?>'
    }
    $ms = [regex]::Matches($dump, $nodePattern)
    if ($ms.Count -eq 0) { return $null }
    $boundsPattern = 'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    $picked = $null
    $bestArea = -1
    foreach ($m in $ms) {
        $bm = [regex]::Match($m.Value, $boundsPattern)
        if (-not $bm.Success) { continue }
        if ($Largest) {
            $area = ([int]$bm.Groups[3].Value - [int]$bm.Groups[1].Value) * ([int]$bm.Groups[4].Value - [int]$bm.Groups[2].Value)
            if ($area -gt $bestArea) { $bestArea = $area; $picked = $bm }
        } else {
            $picked = $bm; break
        }
    }
    if (-not $picked) { return $null }
    if ($BottomMost -and -not $Largest) {
        foreach ($m in $ms) {
            $bm = [regex]::Match($m.Value, $boundsPattern)
            if ($bm.Success) { $picked = $bm }
        }
    }
    $x = ([int]$picked.Groups[1].Value + [int]$picked.Groups[3].Value) / 2
    $y = ([int]$picked.Groups[2].Value + [int]$picked.Groups[4].Value) / 2
    return @([int]$x, [int]$y)
}
function Tap-Node([string]$needle, [switch]$ByDesc, [switch]$BottomMost, [switch]$Exact, [switch]$Largest, [int]$waitMs = 900) {
    for ($try = 0; $try -lt 5; $try++) {
        $dump = Get-Dump
        $c = Find-Node $dump $needle -ByDesc:$ByDesc -BottomMost:$BottomMost -Exact:$Exact -Largest:$Largest
        if ($c) { & $adb shell input tap $c[0] $c[1]; Start-Sleep -Milliseconds $waitMs; return $true }
        Start-Sleep -Milliseconds 600
    }
    return $false
}

Write-Host "== $AvdName : booting emulator =="
Get-Process emulator -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force
# Wait until the previous instance is FULLY gone (processes exited). Starting a new
# emulator while the old one is still tearing down hits "FATAL: Running multiple
# emulators with the same AVD" — the new launcher then shut the old instance down
# mid-run and every case after that failed with "no devices".
$gone = $false
for ($i = 0; $i -lt 30; $i++) {
    $left = Get-Process emulator, qemu-system-x86_64 -ErrorAction SilentlyContinue
    if (-not $left) { $gone = $true; break }
    Start-Sleep -Seconds 1
}
if (-not $gone) { Record 'BOOT' 'FAIL' 'previous emulator instance never exited'; exit 1 }
Start-Sleep -Seconds 1
# Clean stale locks left by force-killed emulators: without this, a new instance
# exits immediately and wait-for-device hangs forever.
# A lock may be a file or a directory (hardware-qemu.ini.lock is a dir with a pid file).
$avdRoot = Join-Path $env:ANDROID_AVD_HOME '*'
$avdDirs = Get-ChildItem $avdRoot -Directory -Force -ErrorAction SilentlyContinue
foreach ($d in $avdDirs) {
    $locks = Get-ChildItem $d.FullName -Filter '*.lock' -Force -ErrorAction SilentlyContinue
    foreach ($l in $locks) {
        for ($try = 0; $try -lt 5; $try++) {
            try {
                if ($l.PSIsContainer) { [System.IO.Directory]::Delete($l.FullName, $true) }
                else { [System.IO.File]::Delete($l.FullName) }
                break
            } catch [System.IO.IOException] { Start-Sleep -Seconds 1 }
        }
    }
}
# NOTE: never use -wipe-data: a freshly wiped emulator system is unsettled and the app
# can hit a bind-time NPE (ActivityThread.handleBindApplication, seen 2026-10-08).
# Key continuity is guaranteed by ANDROID_VENDOR_KEYS at a fixed path; no wipe needed.
# -WindowStyle Hidden: emulator.exe is console-subsystem; without this a black console
# window lingers on the desktop for the whole run (report: 2026-10-08 user feedback).
$emuProc = Start-Process -FilePath $emu -ArgumentList @('-avd', $AvdName, '-no-snapshot', '-no-audio', '-no-window', '-no-boot-anim', '-gpu', 'swiftshader_indirect', '-port', '5554') -WindowStyle Hidden -RedirectStandardError (Join-Path $outDir 'emu_stderr.log') -RedirectStandardOutput (Join-Path $outDir 'emu_stdout.log') -PassThru
# bounded wait-for-device: if the emulator dies at startup this would hang forever otherwise
$devUp = $false
for ($i = 0; $i -lt 60; $i++) {
    $st = (& $adb devices 2>&1 | Out-String)
    if ($st -match 'emulator-\d+\s+(device|offline)') { $devUp = $true; break }
    Start-Sleep -Seconds 3
}
if (-not $devUp) {
    Record 'BOOT' 'FAIL' 'emulator never appeared (see emu_stderr.log)'
    try { Stop-Process -Id $emuProc.Id -Force -ErrorAction SilentlyContinue } catch {}
    exit 1
}
& $adb wait-for-device 2>&1 | Out-Null
$booted = $false
for ($i = 0; $i -lt 120; $i++) {
    $b = (& $adb shell getprop sys.boot_completed 2>&1 | Out-String).Trim()
    if ($b -eq '1') { $booted = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $booted) { Record 'BOOT' 'FAIL' 'emulator did not finish booting in 240s'; & $emuProc | Out-Null; Stop-Process -Id $emuProc.Id -Force; exit 1 }
Record 'BOOT' 'PASS' 'emulator boot_completed'
Start-Sleep -Seconds 4

# install + cold start
& $adb uninstall org.example.sudoku 2>&1 | Out-Null
$inst = (& $adb install -r $Apk 2>&1 | Out-String)
if ($inst -match 'Success') { Record 'INSTALL' 'PASS' 'apk installed' } else { Record 'INSTALL' 'FAIL' $inst.Trim(); Stop-Process -Id $emuProc.Id -Force; exit 1 }

$t0 = Get-Date
$launch = (& $adb shell am start -W -n org.example.sudoku/.MainActivity 2>&1 | Out-String)
$totalMs = if ($launch -match 'TotalTime:\s*(\d+)') { [int]$Matches[1] } else { -1 }
$wallMs = [int]((Get-Date) - $t0).TotalMilliseconds
Record 'COLD_START' 'INFO' ("TotalTime=${totalMs}ms wall=${wallMs}ms")
Start-Sleep -Seconds 2
Shot 's1_menu'

# T1: menu renders (5 entries + sentence from local corpus DB)
$dump = Get-Dump
$okStart = $null -ne (Find-Node $dump $S['MENU_START'])
$okStats = $null -ne (Find-Node $dump $S['MENU_STATS'])
$okSentence = $dump.Contains($S['SENTENCE_ATTR'])
if ($okStart -and $okStats -and $okSentence) { Record 'T1_MENU_RENDER' 'PASS' 'entries + corpus sentence visible' } else { Record 'T1_MENU_RENDER' 'FAIL' ("start=$okStart stats=$okStats sentence=$okSentence"); [System.IO.File]::WriteAllText((Join-Path $outDir 't1_dump.xml'), $dump, [System.Text.Encoding]::UTF8) }

# T2: start Easy game
if (-not (Tap-Node $S['MENU_START'])) { Record 'T2_START_GAME' 'FAIL' 'menu start button not found' }
if (-not (Tap-Node $S['DIFFICULTY_EASY'])) { Record 'T2_START_GAME' 'FAIL' 'difficulty easy not found' }
$dump = Get-Dump
if ($dump.Contains($S['BOARD_DESC_PREFIX'])) { Record 'T2_START_GAME' 'PASS' 'board screen reached' } else { Record 'T2_START_GAME' 'FAIL' 'board not reached'; Shot 's2_fail' }
Shot 's2_game_started'

# T3: select an empty cell (finger policy: no floating pad on touch)
$dump = Get-Dump
$c = Find-Node $dump $S['CELL_EMPTY_MARK'] -ByDesc -BottomMost
if ($c) {
    & $adb shell input tap $c[0] $c[1]; Start-Sleep -Milliseconds 1100
    Shot 's3_after_cell_tap'
    $dump = Get-Dump; $dump2 = Get-Dump   # second dump guards against stale hierarchy
    if (($dump + $dump2).Contains($S['CELL_SELECTED_MARK'])) { Record 'T3_SELECT_CELL' 'PASS' 'empty cell selected (no floating pad on touch)' } else { Record 'T3_SELECT_CELL' 'FAIL' 'no selected semantics' }
} else { Record 'T3_SELECT_CELL' 'FAIL' 'no empty cell found' }

# T4: fill digit 1 via number pad, verify filled semantics
# -Exact -Largest: the bottom key-hint row is ONE text node containing "1-9"/"erase chars",
# and pad badges are single-digit texts too — first/last/bottom-most matches can hit those
# instead of the key face. Exact + biggest bounds = the digit key face itself.
if (-not (Tap-Node '1' -Exact -Largest)) { Record 'T4_FILL_DIGIT' 'FAIL' 'digit key 1 not found' }
Shot 's4_after_digit'
$dump = Get-Dump; $dump2 = Get-Dump
if (($dump + $dump2).Contains($S['FILLED_1'])) { Record 'T4_FILL_DIGIT' 'PASS' 'cell shows filled 1' } else { Record 'T4_FILL_DIGIT' 'FAIL' 'no filled-1 semantics' }

# T5: erase via the erase key (-Exact: hint row text also contains the erase chars)
if (-not (Tap-Node $S['ERASE'] -Exact)) { Record 'T5_ERASE' 'FAIL' 'erase key not found' }
$dump = Get-Dump
if (-not ($dump + (Get-Dump)).Contains($S['FILLED_1'])) { Record 'T5_ERASE' 'PASS' 'cell back to empty' } else { Record 'T5_ERASE' 'FAIL' 'still filled' }

# T6: hint fills a cell and counter appears
if (-not (Tap-Node $S['HINT_DESC'] -ByDesc -Exact)) { Record 'T6_HINT' 'FAIL' 'hint icon not found' }
$dump = Get-Dump; $dump2 = Get-Dump
# the "hint xN" counter is wide-screen only (narrow top bar drops it, docs/08 §7);
# assert the hint's EFFECT instead: some cell now reads the filled semantics
if (($dump + $dump2).Contains($S['FILLED_ANY'])) { Record 'T6_HINT' 'PASS' 'hint filled a cell (counter is wide-screen only)' } else { Record 'T6_HINT' 'FAIL' 'no filled cell after hint' }

# T7: pause veil + resume
if (-not (Tap-Node $S['PAUSE_DESC'] -ByDesc -Exact)) { Record 'T7_PAUSE' 'FAIL' 'pause icon not found' }
$dump = Get-Dump
if ($dump.Contains($S['PAUSED_TEXT'])) {
    Record 'T7_PAUSE' 'PASS' 'pause veil covers board'
    Shot 's7_paused'
    if (Tap-Node $S['RESUME_BTN']) { Record 'T7_RESUME' 'PASS' 'resumed from veil' } else { Record 'T7_RESUME' 'FAIL' 'resume button not found' }
} else { Record 'T7_PAUSE' 'FAIL' 'no paused veil'; Shot 's7_fail' }

# T8: Home -> auto pause evidence on return
& $adb shell input keyevent 3; Start-Sleep -Seconds 2
& $adb shell am start -n org.example.sudoku/.MainActivity 2>&1 | Out-Null
Start-Sleep -Seconds 2
$dump = Get-Dump
if ($dump.Contains($S['PAUSED_TEXT'])) { Record 'T8_BACKGROUND_PAUSE' 'PASS' 'paused veil after returning from launcher' } else { Record 'T8_BACKGROUND_PAUSE' 'FAIL' 'no auto-pause on background' }
Shot 's8_background_return'
if (Tap-Node $S['RESUME_BTN']) { Record 'T8_RESUME_AFTER_BG' 'PASS' 'resumed' } else { Record 'T8_RESUME_AFTER_BG' 'FAIL' 'resume not found' }

# T9: kill process -> relaunch -> board restored
& $adb shell am force-stop org.example.sudoku
Start-Sleep -Seconds 1
& $adb shell am start -n org.example.sudoku/.MainActivity 2>&1 | Out-Null
Start-Sleep -Seconds 2
if (Tap-Node $S['MENU_RESUME']) {
    $dump = Get-Dump
    if ($dump.Contains($S['BOARD_DESC_PREFIX'])) { Record 'T9_KILL_RESTORE' 'PASS' 'board restored after process kill' } else { Record 'T9_KILL_RESTORE' 'FAIL' 'board missing after resume' }
} else { Record 'T9_KILL_RESTORE' 'FAIL' 'resume entry unavailable after kill' }
Shot 's9_restored'

# T10: night mode toggle
if (Tap-Node $S['NIGHT_DESC'] -ByDesc) { Record 'T10_NIGHT_TOGGLE' 'PASS' 'switched to night ink'; Start-Sleep -Milliseconds 600; Shot 's10_night' } else { Record 'T10_NIGHT_TOGGLE' 'FAIL' 'night toggle not found' }

# perf: memory (while app is alive)
$mem = (& $adb shell dumpsys meminfo org.example.sudoku 2>&1 | Out-String)
$m = [regex]::Match($mem, 'TOTAL PSS:\s*(\d+)')
if ($m.Success) { Record 'MEM_PSS' 'INFO' ("TOTAL PSS = " + $m.Groups[1].Value + " kB") } else { Record 'MEM_PSS' 'INFO' 'meminfo unavailable' }

# layout width in dp: min(wm size) / (density/160). Narrow breakpoint = 460dp (docs/08 §7).
$wmSize = (& $adb shell wm size 2>&1 | Out-String)
$wmDens = (& $adb shell wm density 2>&1 | Out-String)
$px = if ($wmSize -match '(\d+)x(\d+)') { [int]$Matches[1] } else { 0 }
$dpi = if ($wmDens -match '(\d+)') { [int]$Matches[1] } else { 160 }
$widthDp = if ($dpi -gt 0) { [int]($px / ($dpi / 160.0)) } else { 0 }
$wide = $widthDp -ge 460
Write-Host ("  layout: {0}dp -> {1}" -f $widthDp, $(if ($wide) { 'WIDE (persistent toggles row)' } else { 'NARROW (settings drawer)' }))

# T11: narrow-layout settings drawer: open via button, then BACK closes drawer first (D2)
# Wide layouts keep the four toggles as a persistent row — the drawer does not exist by design.
if ($wide) {
    Record 'T11_SETTINGS_DRAWER' 'SKIP' ("wide layout {0}dp: persistent toggles row, no drawer (by design)" -f $widthDp)
    Record 'T11_BACK_CLOSES_DRAWER' 'SKIP' 'narrow-only case'
} elseif (-not (Tap-Node $S['SETTINGS_BTN'])) { Record 'T11_SETTINGS_DRAWER' 'FAIL' 'settings button not found' }
else {
    $dump = Get-Dump; $dump2 = Get-Dump
    if (($dump + $dump2).Contains($S['NOTE_SWITCH'])) {
        Record 'T11_SETTINGS_DRAWER' 'PASS' 'settings drawer open (note-mode switch visible)'
        Shot 's11_settings_drawer'
        & $adb shell input keyevent 4; Start-Sleep -Milliseconds 1200
        $dump = Get-Dump; $dump2 = Get-Dump
        if (-not (($dump + $dump2).Contains($S['NOTE_SWITCH']))) { Record 'T11_BACK_CLOSES_DRAWER' 'PASS' 'back key closed drawer first (D2)' }
        else { Record 'T11_BACK_CLOSES_DRAWER' 'FAIL' 'drawer still open after back' }
    } else { Record 'T11_SETTINGS_DRAWER' 'FAIL' 'note-mode switch not found in drawer' }
}

# T12: grid pad fill (narrow layout 3x3 grid): select empty cell, fill 5
$dump = Get-Dump
$c = Find-Node $dump $S['CELL_EMPTY_MARK'] -ByDesc -BottomMost
if ($c) {
    & $adb shell input tap $c[0] $c[1]; Start-Sleep -Milliseconds 1100
    if (Tap-Node '5' -Exact -Largest) {
        $dump = Get-Dump; $dump2 = Get-Dump
        if (($dump + $dump2).Contains($S['FILLED_5'])) { Record 'T12_GRID_FILL' 'PASS' 'grid pad filled 5' }
        else {
            Record 'T12_GRID_FILL' 'FAIL' 'no filled-5 semantics'
            Shot 's12_fail'
            [System.IO.File]::WriteAllText((Join-Path $outDir 't12_dump.xml'), ($dump + "`n==== SECOND ====`n" + $dump2), [System.Text.Encoding]::UTF8)
        }
    } else { Record 'T12_GRID_FILL' 'FAIL' 'grid key 5 not found' }
} else { Record 'T12_GRID_FILL' 'FAIL' 'no empty cell found' }

# T13: BACK at game -> back to menu (D2); game auto-saved (resume entry stays available)
& $adb shell input keyevent 4; Start-Sleep -Seconds 2
$dump = Get-Dump; $dump2 = Get-Dump
if (($dump + $dump2).Contains($S['MENU_START'])) { Record 'T13_BACK_TO_MENU' 'PASS' 'back at game returns to menu (D2)' } else { Record 'T13_BACK_TO_MENU' 'FAIL' 'menu not shown after back'; Shot 's13_fail' }

# T14: BACK at menu -> system default = activity finishes (documented behavior).
# NOTE: the PROCESS usually stays cached after the activity finishes, so `pidof` is the
# wrong predicate (a cached pid survives an exit — observed pids 12006/3842). Assert the
# foreground instead: no top activity of our package remains.
& $adb shell input keyevent 4; Start-Sleep -Seconds 2
$top = (& $adb shell dumpsys activity top 2>&1 | Out-String)
if ($top -notmatch 'ACTIVITY org\.example\.sudoku') { Record 'T14_BACK_MENU_EXIT' 'PASS' 'back at menu finishes activity (default)' } else { Record 'T14_BACK_MENU_EXIT' 'FAIL' 'game activity still top after back at menu' }

& $adb shell am force-stop org.example.sudoku 2>&1 | Out-Null
Get-Process emulator -ErrorAction SilentlyContinue | Stop-Process -Force
Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 2

Write-Host ("== {0} DONE (total {1:mm\:ss}) ==" -f $AvdName, $script:sw.Elapsed)
