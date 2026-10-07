# smoke-e2e.ps1 -- real-app smoke test for the packaged desktop build.
#
# Injection strategy (learned the hard way):
#   * KEYBOARD goes straight to the app window with PostMessage(WM_KEYDOWN/WM_KEYUP).
#     It needs no foreground, cannot hit another window, and - verified - still works
#     while the workstation is locked (GetForegroundWindow() is NULL then, so the old
#     keybd_event approach died with "FOREGROUND LOST").
#   * MOUSE still needs the real foreground (SetCursorPos + mouse_event are global),
#     so clicks keep the assert-and-abort guard.
#   * the window is pinned HWND_TOPMOST at a fixed rect (default 100,100 1180x900) so
#     screenshots by window rect always frame the app.
#   * screenshots are captured by window rect; if the frame looks like a lock screen
#     (very dark), the run reports that the shots are unusable instead of pretending.
#
# Covered flows:
#   menu    -- difficulty drawer: Enter opens, Down x2 highlights Hard, Enter starts Hard
#   exit    -- exit drawer: confirm quit really exits the process
#   win     -- win drawer: seeded "one empty cell" save -> Hint fills it -> win drawer -> Esc to menu
#   theme   -- night mode: seeded darkMode=1 save -> board in night ink -> click the corner icon back
#   bar     -- floating undo/redo bar follows the pointer (hover top / bottom half of the board)
#   ux      -- icon tooltips, click-outside clears highlights, given vs filled digits
#   keys    -- keyboard cursor: starts on the first blank, arrows walk blanks,
#              filling advances box-first, and falls back to reading order when the box is full
#   keymap  -- key binding drawer: rebind, backspace clears, hint row follows the settings
#   stats   -- stats drawer: entries render from the save file
#   lang    -- language drawer: switch to English and back, whole page re-renders instantly
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
    [ValidateSet('all', 'menu', 'exit', 'win', 'theme', 'bar', 'ux', 'keys', 'keymap', 'stats', 'lang')]
    [string]$Flow = 'all'
)

$ErrorActionPreference = 'Stop'
# The screenshot writer saves into -OutDir as-is; a missing directory surfaces as an
# opaque "GDI+ generic error", so make sure it exists up front.
if (-not (Test-Path -LiteralPath $OutDir)) {
    New-Item -ItemType Directory -Path $OutDir -Force | Out-Null
}
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
    [DllImport("kernel32.dll")] public static extern uint GetCurrentThreadId();
    [DllImport("user32.dll")] public static extern bool AttachThreadInput(uint a, uint b, bool attach);
    [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
    [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, IntPtr e);
    [DllImport("user32.dll")] public static extern void keybd_event(byte vk, byte scan, uint f, IntPtr e);
    [DllImport("user32.dll")] public static extern uint MapVirtualKey(uint c, uint m);
    [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint m, IntPtr w, IntPtr l);
}
'@

$script:WM_KEYDOWN = 0x0100
$script:WM_KEYUP = 0x0101

$script:HWND_TOPMOST = [IntPtr](-1)
$script:shot = 0
$script:shotsSuspect = $false   # 只要有任意一帧不像应用（锁屏 / 被遮挡），收尾就报警
$script:VK = @{
    Enter = 0x0D; Escape = 0x1B; Space = 0x20; Hint = 0x48
    # 注意 VK_LEFT = 0x25 / VK_RIGHT = 0x27（这里曾经写反，方向键会朝相反方向走）
    Down = 0x28; Up = 0x26; Left = 0x25; Right = 0x27
    # 主键盘数字键 1..9（应用里对应填数）
    Digit1 = 0x31; Digit2 = 0x32; Digit3 = 0x33; Digit4 = 0x34; Digit5 = 0x35
    Digit6 = 0x36; Digit7 = 0x37; Digit8 = 0x38; Digit9 = 0x39
    Digit0 = 0x30; Backspace = 0x08
    # 自定义键位冒烟用：W 绑「向上」、D 绑「向右」
    W = 0x57
    D = 0x44
}
$script:KeyExtended = 0x0001   # KEYEVENTF_EXTENDEDKEY -- arrow keys need it to read as arrow keys

function Pin($p) {
    [WinE2E]::SetWindowPos($p.MainWindowHandle, $script:HWND_TOPMOST, $Left, $Top, $Width, $Height, 0x0040) | Out-Null
    Start-Sleep -Milliseconds 400
}

function Assert-Foreground($p) {
    $h = $p.MainWindowHandle
    Pin $p
    $me = [WinE2E]::GetCurrentThreadId()
    for ($try = 0; $try -lt 3; $try++) {
        if ([WinE2E]::GetForegroundWindow() -eq $h) { return }
        $pid2 = 0
        $targetTid = [WinE2E]::GetWindowThreadProcessId($h, [ref]$pid2)
        # 抢焦点必须先把本线程挂到"当前前台线程"上，否则 SetForegroundWindow 会被系统静默拒绝
        $fgTid = [WinE2E]::GetWindowThreadProcessId([WinE2E]::GetForegroundWindow(), [ref]$pid2)
        if ($fgTid -ne 0) { [WinE2E]::AttachThreadInput($me, $fgTid, $true) | Out-Null }
        [WinE2E]::AttachThreadInput($me, $targetTid, $true) | Out-Null
        [WinE2E]::BringWindowToTop($h) | Out-Null
        [WinE2E]::SetForegroundWindow($h) | Out-Null
        [WinE2E]::AttachThreadInput($me, $targetTid, $false) | Out-Null
        if ($fgTid -ne 0) { [WinE2E]::AttachThreadInput($me, $fgTid, $false) | Out-Null }
        Start-Sleep -Milliseconds 400
    }
    if ([WinE2E]::GetForegroundWindow() -ne $h) {
        throw ('FOREGROUND LOST - aborting before injecting input (would hit another window)')
    }
}

function Test-ShotLooksLikeTheApp($bmp) {
    # 纸墨风格：整屏大面积是纸。锁屏或被别的窗口盖住时整帧会偏到"中间灰"，
    # 这种截图没有验收价值——与其事后发现，不如当场报出来。
    #
    # 两套主题都要接受：明「纸墨」整屏偏亮（均值 ≥ 90），暗「夜墨」整屏偏暗（均值 8–45）。
    # 落在两者之间的"中间灰"仍然可疑——那正是被别的窗口盖住 / 锁屏的样子。
    $sum = 0.0
    $n = 0
    for ($i = 0; $i -lt 10; $i++) {
        for ($j = 0; $j -lt 10; $j++) {
            $x = [int]($bmp.Width * ($i + 0.5) / 10)
            $y = [int]($bmp.Height * ($j + 0.5) / 10)
            $c = $bmp.GetPixel($x, $y)
            $sum += (0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B)
            $n++
        }
    }
    $mean = $sum / $n
    return (($mean -ge 90) -or ($mean -ge 8 -and $mean -le 45))
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
    if (-not (Test-ShotLooksLikeTheApp $bmp)) {
        $script:shotsSuspect = $true
        Write-Host ("shot -> $file  WARN: frame is dark (locked screen / occluded?)")
    } else {
        Write-Host ("shot -> $file")
    }
    $bmp.Dispose()
}

function Send-Key($p, [string]$key, [int]$flags = 0, [int]$waitMs = 550) {
    $h = $p.MainWindowHandle
    Pin $p
    $vk = $script:VK[$key]
    if ($null -eq $vk) { throw ("unknown key name: " + $key) }
    $scan = [WinE2E]::MapVirtualKey([uint32]$vk, 0)
    # lParam layout: repeat(0) | scancode(16) | extended(24) ; key-up adds 0x80000000|0x40000000
    $down = 1 -bor ([int]$scan -shl 16)
    if (($flags -band $script:KeyExtended) -ne 0) { $down = $down -bor 0x01000000 }
    $up = $down -bor 0x80000000 -bor 0x40000000
    [WinE2E]::PostMessage($h, $script:WM_KEYDOWN, [IntPtr]$vk, [IntPtr]$down) | Out-Null
    Start-Sleep -Milliseconds 70
    [WinE2E]::PostMessage($h, $script:WM_KEYUP, [IntPtr]$vk, [IntPtr]$up) | Out-Null
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

function Send-Hover($p, [int]$x, [int]$y, [int]$waitMs = 500) {
    # 只移动指针、不点击：验证"浮动操作条随鼠标换边"这类**只在悬停时**发生的行为
    Assert-Foreground $p
    $r = New-Object RectE2E
    [WinE2E]::GetWindowRect($p.MainWindowHandle, [ref]$r) | Out-Null
    [WinE2E]::SetCursorPos(($r.L + $x), ($r.T + $y)) | Out-Null
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
function Write-OneEmptySave([string]$path, [bool]$dark = $false, [int]$Blank = 1) {
    # a complete, valid sudoku grid with only the last $Blank cells blank
    # ($Blank > 1 时留着不止一格：用来观察"给定数字 vs 玩家填入"的区分，且不触发通关)
    $sol = '534678912672195348198342567859761423426853791713924856961537284287419635345286179'
    if ($sol.Length -ne $SIZE) { throw ('bad seed grid') }
    if ($Blank -lt 1 -or $Blank -gt 9) { throw ('Blank must be 1..9') }
    $puz = $sol.Substring(0, ($SIZE - $Blank)) + ('0' * $Blank)
    $notes = (1..$SIZE | ForEach-Object { '0' }) -join ','
    $lines = @('v=2', 'strict=0', 'showNotes=1', 'noteMode=0', 'hintCandidates=0', 'game=1', 'difficulty=Easy')
    # 明暗是第五项偏好，随存档走：写进去就能让应用**开局即夜墨**，省掉一次点击
    $lines += ('darkMode=' + $(if ($dark) { '1' } else { '0' }))
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
        # 菜单现在是 5 个入口：0 开始 / 1 继续(置灰，跳过) / 2 统计 / 3 语言 / 4 退出
        # → 三次 ↓ 落在「退出游戏」
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
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

    if ($Flow -eq 'all' -or $Flow -eq 'theme') {
        Write-Host '== flow: night mode (夜墨) =='
        # 存档里直接带 darkMode=1：开局即夜墨，不用先点一次开关
        Write-OneEmptySave $saveFile $true
        $p = Start-App
        Save-Shot $p 'menu_night'  # 首页右上角也应有明暗切换（与对局页同一位置）
        Send-Key $p 'Down' $script:KeyExtended    # "Resume game"
        Send-Key $p 'Enter'
        Save-Shot $p 'board_night'
        # 点顶栏最右侧的明暗切换 → 应回到浅色纸面（这一步同时验证按钮位置与命中区）
        Send-Click $p 1146 59
        Save-Shot $p 'board_day_after_toggle'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'bar') {
        Write-Host '== flow: floating bar follows the pointer =='
        Write-OneEmptySave $saveFile
        $p = Start-App
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        # 鼠标移到棋盘**上半部**：条应搬到棋盘上方
        Send-Hover $p 400 220
        Save-Shot $p 'bar_at_top'
        # 鼠标移到棋盘**下半部**：条应搬回棋盘下方
        Send-Hover $p 400 620
        Save-Shot $p 'bar_at_bottom'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'ux') {
        Write-Host '== flow: icon tooltips + click-outside clears the board =='
        # 留 3 格空白：填掉一格不会通关，这才看得到"给定 vs 玩家填入"的区分
        Write-OneEmptySave $saveFile -Blank 3
        $p = Start-App
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        # ① 悬停顶栏最右侧的明暗切换 → 应在按钮下方浮出功能名
        Send-Hover $p 1146 59
        Save-Shot $p 'icon_tooltip'
        # ② 点棋盘中心 → 选中一格（同行列宫 / 同数字高亮随之出现）
        Send-Click $p 590 402
        Save-Shot $p 'cell_selected'
        # ③ 点棋盘以外的空白纸面 → 选中与一切临时高亮清空，回到开局的干净样子
        Send-Click $p 150 402
        Save-Shot $p 'board_cleared'
        # ④ 选最后一格并用键盘填 9 → 对比"印上去的给定数字"与"写上去的填入数字"
        Send-Click $p 813 626
        Send-Key $p 'Digit9'
        Send-Click $p 150 402
        Save-Shot $p 'given_vs_filled'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'keys') {
        Write-Host '== flow: keyboard cursor =='
        # 留 4 格空白：77（第 9 行第 6 列，**宫 7**）与 78 / 79 / 80（第 9 行第 7–9 列，**宫 8**）。
        # 这一组位置是刻意挑的：它能在同一条流里把三种行为都逼出来
        #   ① 开局光标落在读序第一个空格（77）
        #   ② → 只在本行内走到下一个空格（77 → 78）
        #   ③ 填数后**宫优先**推进（78 → 79 → 80）
        #   ④ 宫 8 填满后退回横向优先 → 跳回最靠前的 77（这一步最能说明为什么选宫优先）
        # 填的是标准答案里的正确数字（1 / 7 / 9），因此不会出现冲突排线，画面干净可判。
        Write-OneEmptySave $saveFile -Blank 4
        $p = Start-App
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        Save-Shot $p 'keys_cursor_on_first_blank'
        # 方向键必须带**扩展位**（否则窗口收到的不是方向键，表现为"按了没反应"——
        # 这一点是脚本里踩过的坑，其它流里的 Down 也都传了 KeyExtended）
        Send-Key $p 'Right' $script:KeyExtended
        Save-Shot $p 'keys_arrow_right'
        Send-Key $p 'Digit1'                      # 填 78
        Save-Shot $p 'keys_fill_advances_in_box'
        Send-Key $p 'Digit7'                      # 填 79
        Save-Shot $p 'keys_fill_advances_again'
        Send-Key $p 'Digit9'                      # 填 80 → 宫 8 已满 → 跳回 77
        Save-Shot $p 'keys_box_full_falls_back'
        # ⑦ **普通方向键**（不加 Shift）也要能回到已填的格子：
        #    ← 从 77 出发越过一串题面格，落在 80（刚填的 9）上
        Send-Key $p 'Left' $script:KeyExtended
        Save-Shot $p 'keys_back_to_filled'
        # ⑧ 擦掉它（光标停在已填格上，按 0 = 擦除；擦除不触发跳转）
        Send-Key $p 'Digit0'
        Save-Shot $p 'keys_erased'
        # ⑨ 再填上别的数（修改）
        Send-Key $p 'Digit9'
        Save-Shot $p 'keys_refilled'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'keymap') {
        Write-Host '== flow: key bindings =='
        # 留 9 格（整个末行）：光标开局落在读序第一个空格（第 9 行第 1 列），
        # 这样"向右"的绑定能用**真的走一格**来验证（"向上"在那列没有可编辑格，走了也不动——那是正确行为）
        Write-OneEmptySave $saveFile -Blank 9
        $p = Start-App
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        # ① 底部键位提示行（默认键位：方向键 + 0 擦除）
        Save-Shot $p 'keymap_hint_default'
        # ② 顶栏「键位设置」：右侧一组**最前面**一枚（明暗切换仍在最右，坐标不变）
        Send-Click $p 970 59
        Save-Shot $p 'keymap_drawer'
        # ③ 点「向上」的键位按钮 → 捕获态 → 按 W
        Send-Click $p 780 140
        Save-Shot $p 'keymap_capturing'
        Send-Key $p 'W'
        Save-Shot $p 'keymap_bound_w'
        # ④ 再点一次 → 捕获态 → 按**退格**：清除现有设置（复位为默认 ↑）
        Send-Click $p 780 140
        Send-Key $p 'Backspace'
        # ⑤ 退出捕获态看一眼：按钮应显示默认的 ↑ 而不是 W——这才证明"清掉的是自定义绑定"
        #    （若是 Esc 取消捕获，按钮还会停在 W）
        Send-Key $p 'Escape'
        Save-Shot $p 'keymap_cleared'
        # ⑥ 清完再点一次 → 按 W 绑回（"清除 → 再设"两步都走通）
        Send-Click $p 780 140
        Send-Key $p 'W'
        Save-Shot $p 'keymap_rebound_w'
        # ⑥ 点「向右」的键位按钮 → 捕获态 → 按 D
        Send-Click $p 780 278
        Send-Key $p 'D'
        Save-Shot $p 'keymap_bound_d'
        # ⑦ Esc 关抽屉（此时不在捕获态，Esc = 关抽屉）→ 提示行应已变成「W」「↓」「←」「D」
        Send-Key $p 'Escape'
        Save-Shot $p 'keymap_hint_custom'
        # ⑧ 真的按一下 D：光标应往右走一格（键位设置生效）
        Send-Key $p 'D'
        Save-Shot $p 'keymap_d_moves_cursor'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'stats') {
        Write-Host '== flow: statistics =='
        # 注入"只差一格"的存档：先用空统计看空态，再通关一局看重放
        Write-OneEmptySave $saveFile
        $p = Start-App
        # 主菜单光标从「开始游戏」起：↓↓（跳过置灰的"继续"）到「查看游玩统计」
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        Save-Shot $p 'stats_empty'
        Send-Key $p 'Escape'
        # 关抽屉后**光标停在「统计」**：↑ 一步到「继续游戏」→ 进局 → H 补满 → 通关 → Esc 回首页
        Send-Key $p 'Up' $script:KeyExtended
        Send-Key $p 'Enter'
        Send-Key $p 'Hint'
        Save-Shot $p 'stats_win_drawer'
        Send-Key $p 'Escape'
        # 再开统计：应出现 1 场胜局、连胜 1、最快纪录与总时长
        # （通关后"继续游戏"置灰，回首页光标从「开始游戏」起——**一次 ↓** 就到「统计」）
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        Save-Shot $p 'stats_after_win'
        Stop-App $p
    }

    if ($Flow -eq 'all' -or $Flow -eq 'lang') {
        Write-Host '== flow: language switch + sentence strip =='
        $p = Start-App
        Save-Shot $p 'lang_menu_zh'                # 首页（中文）+ 底部随机句子
        # ↓↓ 到「语言」→ Enter 开抽屉 → ↓ 到 English → Enter（抽屉不关，整页当场变英文）
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'
        Save-Shot $p 'lang_menu_en'
        Send-Key $p 'Escape'                       # 关抽屉
        Save-Shot $p 'lang_menu_en_closed'
        # 切回简体中文（入口文字已是英文："Language · 语言：English"）
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Down' $script:KeyExtended
        Send-Key $p 'Enter'                        # Language 抽屉（光标仍在 English）
        Send-Key $p 'Up' $script:KeyExtended
        Send-Key $p 'Up' $script:KeyExtended
        Send-Key $p 'Enter'                        # 选简体中文
        Send-Key $p 'Escape'
        Save-Shot $p 'lang_menu_back_zh'
        Stop-App $p
    }
} catch {
    Write-Host ('SMOKE FAILED: ' + $_.Exception.Message)
    $exitCode = 1
} finally {
    if ([System.IO.File]::Exists($saveFile)) { [System.IO.File]::Delete($saveFile) }
    if ([System.IO.File]::Exists($saveBackup)) { [System.IO.File]::Move($saveBackup, $saveFile) }
    Get-Process SudokuInk -ErrorAction SilentlyContinue | Stop-Process -Force
    if ($exitCode -eq 0 -and $script:shotsSuspect) {
        # 按键是投给窗口句柄的，锁屏也能生效；但屏幕捕捉拿不到应用画面。
        # 这时"流程跑通"不等于"画面验收过"，必须明说，不能拿锁屏图当证据。
        Write-Host 'SMOKE WARN: keys reached the app, but some frames do not look like it (locked screen or occluded window) - shots are NOT usable for review'
        $exitCode = 2
    }
    Write-Host ('save restored, screenshots in ' + $OutDir)
}
exit $exitCode
