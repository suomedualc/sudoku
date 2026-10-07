package org.example.sudoku.ui.i18n

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.AppLanguage
import org.example.sudoku.state.KeyTokens
import org.example.sudoku.state.Msg

/**
 * 全部**可见文字**的字典（中 / 英两份）。
 *
 * 为什么手写字典而不用 Compose 的字符串资源：
 * ① 零构建改动、零依赖（本项目刻意不引第三方组件库 / 资源管线）；
 * ② 切换语言 = 换一个对象，快照状态驱动，**当场生效、无需重启**；
 * ③ 纯 Kotlin，可单测（键不会拼错——编译器替你查）。
 *
 * 纪律：**界面上的任何文字都不许直接写字面量**，一律从这里取——
 * 加一个新句子时中英两份**同批给齐**（没有自动化的键一致性守卫，漏了英文，
 * 切到 English 就是中文夹英文）。
 */
interface Strings {
    // —— 首页 ——
    val appTitle: String
    val menuStart: String
    val menuResume: String
    val menuExit: String
    val menuNoResume: String
    val menuKeys: String
    val viewStats: String
    val chooseDifficulty: String
    val difficultyBlanks: String // "%1$s　%2$d 空"
    val back: String
    val menuKeysEsc: String
    val exitTitle: String
    val exitNote: String
    val cancel: String
    val quit: String

    // —— 主题切换（两页共用同一枚图标） ——
    val toLight: String
    val toNight: String
    val tooltipPaper: String
    val tooltipNight: String

    // —— 难度名 ——
    val difficultyEasy: String
    val difficultyNormal: String
    val difficultyHard: String
    val difficultyExpert: String

    // —— 对局页顶栏 ——
    val backToMenu: String
    val keymapA11y: String
    val keymapTooltip: String
    val hint: String
    val pause: String
    val resume: String
    val resetGame: String
    val pausedLabel: String
    val hintsUsed: String // "提示 ×%1$d"

    // —— 棋盘无障碍 ——
    val boardDesc: String // "数独棋盘，难度 %1$s，已填 %2$d / %3$d"
    val boardPaused: String // "，已暂停"
    val boardNoteMode: String // "，笔记模式"
    val boardCandidates: String // "，候选提示开启"
    val boardSelected: String // "，当前选中第 %1$d 行第 %2$d 列"
    val cellPos: String // "第 %1$d 行第 %2$d 列"
    val cellSelected: String // "，已选中"
    val cellEmpty: String // "，空"
    val cellNotes: String // "，笔记 %1$s"
    val cellGiven: String // "，给定 %1$d"
    val cellFilled: String // "，填入 %1$d"
    val cellConflict: String

    /** 笔记数字之间的分隔符（中文"、"，英文", "——读屏逐格念笔记时按语言断句）。 */
    val notesSeparator: String // "，与同行列宫重复"

    // —— 暂停遮罩 / 通关抽屉 ——
    val pauseVeilTitle: String
    val winTitle: String
    val winTimeLabel: String
    val winDifficultyLabel: String
    val winHintsLabel: String
    val hintsNone: String
    val playAgain: String
    val backHome: String
    val winKeys: String

    // —— 功能区 ——
    val erase: String
    val undo: String
    val redo: String
    val gameSettings: String
    val toggleNoteMode: String
    val toggleShowNotes: String
    val toggleHintCandidates: String
    val toggleStrict: String
    val candidatesOffHint: String
    val candidatesOnHint: String

    // —— 悬浮输入面板 ——
    val padNoCandidates: String

    // —— 键位设置 ——
    val keymapTitle: String
    val keySpace: String
    val keyBackspace: String
    val keyDel: String
    val keymapHintCapture: String
    val keyCapturing: String
    val keymapReset: String
    val keymapHintGestures: String
    val keymapHintFallbacks: String
    val digitsReserved: String
    val actionUp: String
    val actionDown: String
    val actionLeft: String
    val actionRight: String
    val actionErase: String
    val keyCapCapturing: String // "%1$s 键位：正在等待按键，按下 Esc 取消"
    val keyCapBound: String // "%1$s 键位：%2$s，点击后按下新键"

    // —— 底部键位提示行 ——
    val hintsMove: String
    val hintsErase: String
    val hintsFill: String
    val hintsNote: String
    val hintsHint: String
    val hintsPause: String
    val hintsUndo: String
    val hintStep: String
    val hintJump: String

    // —— 统计 ——
    val statsTitle: String
    val statsGames: String
    val statsAbandoned: String
    val statsWinRate: String
    val statsStreak: String
    val statsBestStreak: String
    val statsTotalTime: String
    val statsMoves: String
    val statsAvgPerMove: String
    val statsHintsCount: String
    val statsFastest: String // "%1$s 最快"
    val statsEmpty: String

    // —— 首页句子 ——
    val sentenceAnother: String

    /** 脑筋急转弯的答案前缀（"答案：" / "Answer: "）。 */
    val riddleAnswerPrefix: String

    // —— 统计的单位 ——
    val secondsUnit: String

    // —— 语言设置 ——
    val languageTitle: String

    /** 一次性提示的渲染（含数字 / 时长的拼装）。 */
    fun render(msg: Msg): String = when (msg) {
        Msg.SelectFirst -> msgSelectFirst
        is Msg.Conflict -> msgConflict.format(msg.value)
        Msg.GivenImmutable -> msgGivenImmutable
        Msg.StrictConflict -> msgStrictConflict
        Msg.NoNoteOnFilled -> msgNoNoteOnFilled
        is Msg.Win -> if (msg.usedHints) msgWinWithHints.format(Sudoku.formatDuration(msg.elapsed)) else msgWin.format(Sudoku.formatDuration(msg.elapsed))
        Msg.ResetDone -> msgResetDone
        Msg.HintFilled -> msgHintFilled
        Msg.NoHintLeft -> msgNoHintLeft
        Msg.Revealed -> msgRevealed
        Msg.NothingToUndo -> msgNothingToUndo
        Msg.NothingToRedo -> msgNothingToRedo
        Msg.DarkModeOn -> msgDarkOn
        Msg.DarkModeOff -> msgDarkOff
        Msg.HintCandidatesOn -> msgCandidatesOn
        Msg.HintCandidatesOff -> msgCandidatesOff
    }

    // —— 一次性提示文案 ——
    val msgSelectFirst: String
    val msgConflict: String // "数字 %1$d 与所在行 / 列 / 宫冲突"
    val msgGivenImmutable: String
    val msgStrictConflict: String
    val msgNoNoteOnFilled: String
    val msgWin: String // "恭喜通关！用时 %1$s"
    val msgWinWithHints: String
    val msgResetDone: String
    val msgHintFilled: String
    val msgNoHintLeft: String
    val msgRevealed: String
    val msgNothingToUndo: String
    val msgNothingToRedo: String
    val msgDarkOn: String
    val msgDarkOff: String
    val msgCandidatesOn: String
    val msgCandidatesOff: String

    /** 难度名。 */
    fun difficulty(difficulty: Difficulty): String = when (difficulty) {
        Difficulty.Easy -> difficultyEasy
        Difficulty.Normal -> difficultyNormal
        Difficulty.Hard -> difficultyHard
        Difficulty.Expert -> difficultyExpert
    }
}

/** 简体中文。 */
object ZhStrings : Strings {
    override val appTitle = "数独"
    override val menuStart = "开始游戏"
    override val menuResume = "继续游戏"
    override val menuExit = "退出游戏"
    override val menuNoResume = "暂无未完成的对局"
    override val menuKeys = "↑↓ 选择 · Enter 确认"
    override val viewStats = "查看游玩统计"
    override val chooseDifficulty = "选择难度"
    override val difficultyBlanks = "%1\$s　%2\$d 空"
    override val back = "返回"
    override val menuKeysEsc = "↑↓ 选择 · Enter 确认 · Esc 返回"
    override val exitTitle = "退出游戏？"
    override val exitNote = "未完成的对局会自动保存"
    override val cancel = "取消"
    override val quit = "退出"

    override val toLight = "切回浅色纸面"
    override val toNight = "切换到夜墨模式"
    override val tooltipPaper = "纸面模式"
    override val tooltipNight = "夜墨模式"

    override val difficultyEasy = "简单"
    override val difficultyNormal = "普通"
    override val difficultyHard = "困难"
    override val difficultyExpert = "大师"

    override val backToMenu = "返回菜单"
    override val keymapA11y = "键位设置"
    override val keymapTooltip = "键位"
    override val hint = "提示"
    override val pause = "暂停"
    override val resume = "继续"
    override val resetGame = "重置本局"
    override val pausedLabel = "已暂停"
    override val hintsUsed = "提示 ×%1\$d"

    override val boardDesc = "数独棋盘，难度 %1\$s，已填 %2\$d / %3\$d"
    override val boardPaused = "，已暂停"
    override val boardNoteMode = "，笔记模式"
    override val boardCandidates = "，候选提示开启"
    override val boardSelected = "，当前选中第 %1\$d 行第 %2\$d 列"
    override val cellPos = "第 %1\$d 行第 %2\$d 列"
    override val cellSelected = "，已选中"
    override val cellEmpty = "，空"
    override val cellNotes = "，笔记 %1\$s"
    override val cellGiven = "，给定 %1\$d"
    override val cellFilled = "，填入 %1\$d"
    override val cellConflict = "，与同行列宫重复"
    override val notesSeparator = "、"

    override val pauseVeilTitle = "已暂停"
    override val winTitle = "通关"
    override val winTimeLabel = "本局用时"
    override val winDifficultyLabel = "难度"
    override val winHintsLabel = "提示次数"
    override val hintsNone = "未使用"
    override val playAgain = "再来一局"
    override val backHome = "返回首页"
    override val winKeys = "Enter 再来一局 · Esc 返回首页"

    override val erase = "擦除"
    override val undo = "撤销"
    override val redo = "重做"
    override val gameSettings = "游戏设置"
    override val toggleNoteMode = "笔记模式"
    override val toggleShowNotes = "显示笔记"
    override val toggleHintCandidates = "候选提示"
    override val toggleStrict = "严格模式"
    override val candidatesOffHint = "开：在空格里显示可填数字（半透明灰）"
    override val candidatesOnHint = "空格里的半透明灰数字 = 规则允许的候选"
    override val padNoCandidates = "本格无可填数字"

    override val keymapTitle = "键位设置"
    override val keySpace = "空格"
    override val keyBackspace = "退格"
    override val keyDel = "Del"
    override val keymapHintCapture = "点一下右侧的键位，再按下你想用的键"
    override val keyCapturing = "按下按键…"
    override val keymapReset = "恢复默认键位"
    override val keymapHintGestures = "点键位 → 按新键；按退格清除该键位（恢复默认），按 Esc 取消"
    override val keymapHintFallbacks = "方向键逐格移动，可停在已填的格子上回去改 / 擦；" +
        "Shift + 方向键跳到下一个空格；退格 / Del 始终可擦除"
    override val digitsReserved = "数字 1–9 留给填数，不能绑定"
    override val actionUp = "向上"
    override val actionDown = "向下"
    override val actionLeft = "向左"
    override val actionRight = "向右"
    override val actionErase = "擦除"
    override val keyCapCapturing = "%1\$s 键位：正在等待按键，按下 Esc 取消"
    override val keyCapBound = "%1\$s 键位：%2\$s，点击后按下新键"

    override val hintsMove = "移动"
    override val hintsErase = "擦除"
    override val hintsFill = "填数"
    override val hintsNote = "笔记"
    override val hintsHint = "提示"
    override val hintsPause = "暂停"
    override val hintsUndo = "撤销"
    override val hintStep = "方向键逐格（可回到已填格）"
    override val hintJump = "Shift+方向 跳下一个空格"

    override val statsTitle = "游玩统计"
    override val statsGames = "完成对局"
    override val statsAbandoned = "放弃对局"
    override val statsWinRate = "胜率"
    override val statsStreak = "当前连胜"
    override val statsBestStreak = "最长连胜"
    override val statsTotalTime = "总游玩时长"
    override val statsMoves = "填数步数"
    override val statsAvgPerMove = "平均每步"
    override val statsHintsCount = "提示次数"
    override val statsFastest = "%1\$s 最快"
    override val statsEmpty = "还没有完成过一局——先去开一局吧"

    override val sentenceAnother = "换一句"
    override val riddleAnswerPrefix = "答案："

    override val secondsUnit = "秒"

    override val languageTitle = "语言 · Language"

    override val msgSelectFirst = "请先选择一个格子"
    override val msgConflict = "数字 %1\$d 与所在行 / 列 / 宫冲突"
    override val msgGivenImmutable = "题目给定的数字不可修改"
    override val msgStrictConflict = "严格模式：该数字与规则冲突"
    override val msgNoNoteOnFilled = "该格已填入数字，无法记笔记"
    override val msgWin = "恭喜通关！用时 %1\$s"
    override val msgWinWithHints = "恭喜通关（使用过提示）！用时 %1\$s"
    override val msgResetDone = "本局已重置"
    override val msgHintFilled = "已填入正确答案"
    override val msgNoHintLeft = "已无可提示的空格"
    override val msgRevealed = "已显示答案，本局不计入胜场"
    override val msgNothingToUndo = "没有可撤销的步骤"
    override val msgNothingToRedo = "没有可重做的步骤"
    override val msgDarkOn = "夜墨模式：开"
    override val msgDarkOff = "夜墨模式：关"
    override val msgCandidatesOn = "候选提示：开（空格显示可填数字）"
    override val msgCandidatesOff = "候选提示：关"
}

/** English. */
object EnStrings : Strings {
    override val appTitle = "Sudoku"
    override val menuStart = "New game"
    override val menuResume = "Continue"
    override val menuExit = "Quit"
    override val menuNoResume = "No game in progress"
    override val menuKeys = "↑↓ to choose · Enter to confirm"
    override val viewStats = "View statistics"
    override val chooseDifficulty = "Choose difficulty"
    override val difficultyBlanks = "%1\$s · %2\$d blanks"
    override val back = "Back"
    override val menuKeysEsc = "↑↓ choose · Enter confirm · Esc back"
    override val exitTitle = "Quit the game?"
    override val exitNote = "Unfinished games are saved automatically"
    override val cancel = "Cancel"
    override val quit = "Quit"

    override val toLight = "Switch to light paper"
    override val toNight = "Switch to night ink"
    override val tooltipPaper = "Paper"
    override val tooltipNight = "Night ink"

    override val difficultyEasy = "Easy"
    override val difficultyNormal = "Normal"
    override val difficultyHard = "Hard"
    override val difficultyExpert = "Expert"

    override val backToMenu = "Back to menu"
    override val keymapA11y = "Key bindings"
    override val keymapTooltip = "Keys"
    override val hint = "Hint"
    override val pause = "Pause"
    override val resume = "Resume"
    override val resetGame = "Restart"
    override val pausedLabel = "Paused"
    override val hintsUsed = "Hints ×%1\$d"

    override val boardDesc = "Sudoku board, difficulty %1\$s, %2\$d of %3\$d filled"
    override val boardPaused = ", paused"
    override val boardNoteMode = ", notes on"
    override val boardCandidates = ", candidates on"
    override val boardSelected = ", cursor at row %1\$d, column %2\$d"
    override val cellPos = "Row %1\$d, column %2\$d"
    override val cellSelected = ", selected"
    override val cellEmpty = ", empty"
    override val cellNotes = ", notes %1\$s"
    override val cellGiven = ", given %1\$d"
    override val cellFilled = ", filled %1\$d"
    override val cellConflict = ", conflicts with peers"
    override val notesSeparator = ", "

    override val pauseVeilTitle = "Paused"
    override val winTitle = "Solved"
    override val winTimeLabel = "Time"
    override val winDifficultyLabel = "Difficulty"
    override val winHintsLabel = "Hints"
    override val hintsNone = "None"
    override val playAgain = "Play again"
    override val backHome = "Back to menu"
    override val winKeys = "Enter play again · Esc back to menu"

    override val erase = "Erase"
    override val undo = "Undo"
    override val redo = "Redo"
    override val gameSettings = "Game settings"
    override val toggleNoteMode = "Notes"
    override val toggleShowNotes = "Show notes"
    override val toggleHintCandidates = "Candidates"
    override val toggleStrict = "Strict"
    override val candidatesOffHint = "On: allowed digits show up in empty cells (faint grey)"
    override val candidatesOnHint = "Faint grey digits in empty cells = rule-allowed candidates"
    override val padNoCandidates = "No digit fits here"

    override val keymapTitle = "Key bindings"
    override val keySpace = "Space"
    override val keyBackspace = "Backspace"
    override val keyDel = "Del"
    override val keymapHintCapture = "Click a binding, then press the key you want"
    override val keyCapturing = "Press a key…"
    override val keymapReset = "Reset to defaults"
    override val keymapHintGestures = "Click a binding → press a key; Backspace clears it (back to default), Esc cancels"
    override val keymapHintFallbacks = "Arrows step one cell (land on filled ones to fix or erase); " +
        "Shift + arrows jump to the next empty cell; Backspace / Del always erase"
    override val digitsReserved = "Digits 1–9 are reserved for entering numbers"
    override val actionUp = "Up"
    override val actionDown = "Down"
    override val actionLeft = "Left"
    override val actionRight = "Right"
    override val actionErase = "Erase"
    override val keyCapCapturing = "%1\$s binding: waiting for a key, Esc to cancel"
    override val keyCapBound = "%1\$s binding: %2\$s, click to rebind"

    override val hintsMove = "Move"
    override val hintsErase = "Erase"
    override val hintsFill = "Fill"
    override val hintsNote = "Notes"
    override val hintsHint = "Hint"
    override val hintsPause = "Pause"
    override val hintsUndo = "Undo"
    override val hintStep = "Arrows step cells (can revisit filled)"
    override val hintJump = "Shift+arrows jump to next blank"

    override val statsTitle = "Statistics"
    override val statsGames = "Solved"
    override val statsAbandoned = "Given up"
    override val statsWinRate = "Win rate"
    override val statsStreak = "Current streak"
    override val statsBestStreak = "Best streak"
    override val statsTotalTime = "Total time"
    override val statsMoves = "Digits placed"
    override val statsAvgPerMove = "Avg / move"
    override val statsHintsCount = "Hints used"
    override val statsFastest = "%1\$s best"
    override val statsEmpty = "No finished game yet — go start one"

    override val sentenceAnother = "Another one"
    override val riddleAnswerPrefix = "Answer: "

    override val secondsUnit = "s"

    override val languageTitle = "Language · 语言"

    override val msgSelectFirst = "Pick a cell first"
    override val msgConflict = "%1\$d conflicts with its row / column / box"
    override val msgGivenImmutable = "Given digits cannot be changed"
    override val msgStrictConflict = "Strict mode: that digit breaks the rules"
    override val msgNoNoteOnFilled = "This cell already has a number"
    override val msgWin = "Solved in %1\$s!"
    override val msgWinWithHints = "Solved (hints used) in %1\$s!"
    override val msgResetDone = "Board reset"
    override val msgHintFilled = "Correct answer filled in"
    override val msgNoHintLeft = "No empty cell left to hint"
    override val msgRevealed = "Solution revealed — this game won't count as a win"
    override val msgNothingToUndo = "Nothing to undo"
    override val msgNothingToRedo = "Nothing to redo"
    override val msgDarkOn = "Night ink: on"
    override val msgDarkOff = "Night ink: off"
    override val msgCandidatesOn = "Candidates: on"
    override val msgCandidatesOff = "Candidates: off"
}

/** 按语言取字典；[AppLanguage.FollowSystem] 解析系统语言（见 `systemAppLanguage`）。 */
fun stringsFor(language: AppLanguage): Strings = when (language) {
    AppLanguage.ZhCn -> ZhStrings
    AppLanguage.En -> EnStrings
    AppLanguage.FollowSystem -> stringsFor(systemAppLanguage())
}

/** 系统语言（`expect/actual`：桌面读系统 Locale，测试里不走这里）。 */
expect fun systemAppLanguage(): AppLanguage

/**
 * 键位令牌 → 界面上显示的短名（键位设置按钮与底部提示行共用）。
 *
 * 箭头符号语言无关；"空格 / 退格"这类**词**是界面文字，必须按语言取——
 * 此前它在 `state/KeyMap.kt` 里返回中文硬编码，英文界面会混出「空格」。
 * 字母 / 数字令牌原样显示（"W"、"5"）。
 */
fun Strings.keyTokenLabel(token: String): String = when (token) {
    KeyTokens.UP -> "↑"
    KeyTokens.DOWN -> "↓"
    KeyTokens.LEFT -> "←"
    KeyTokens.RIGHT -> "→"
    KeyTokens.SPACE -> keySpace
    KeyTokens.BACKSPACE -> keyBackspace
    KeyTokens.DELETE -> keyDel
    else -> token
}
