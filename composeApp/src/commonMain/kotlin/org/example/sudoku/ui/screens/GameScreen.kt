package org.example.sudoku.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.Screen
import org.example.sudoku.ui.components.BoardCanvas
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkDivider
import org.example.sudoku.ui.components.InkPanel
import org.example.sudoku.ui.components.InkText
import org.example.sudoku.ui.components.InkToggleRow
import org.example.sudoku.ui.components.NumberPad
import org.example.sudoku.ui.theme.DesignTokens
import org.example.sudoku.ui.theme.Ink

/**
 * 对局界面（手写纸 · 油墨）。
 *
 * 布局自适应：
 * - **宽屏**（宽 ≥ [DesignTokens.Sizes.WideBreakpoint] 且宽 > 高）：棋盘左 + 控制右，棋盘取可用高宽的正方形；
 * - **窄屏**：纵向排布并整体可滚动，棋盘占满宽度。
 * 两种形态都保证"数字键盘与棋盘同屏可见"，空间不足时宁可滚动也不压缩键盘。
 *
 * 输入方式：鼠标 / 触屏点选；桌面键盘由 [handleKeyEvent] 处理
 * （1–9 填数、0/Delete 擦除、方向键选格并跳过给定格、N 笔记、H 提示、P/空格 暂停、Esc 继续、Ctrl+Z/Y 撤销重做）。
 */
@Composable
fun GameScreen(
    state: GameState,
    conflicts: BooleanArray,
    progress: Pair<Int, Int>,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = state.game ?: return
    val (done, total) = progress
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val boardDescription = remember(game, state.selected, state.paused, done, total) {
        buildString {
            append("数独棋盘，难度 ${game.difficulty.label}，已填 $done / $total")
            if (state.paused) append("，已暂停")
            if (state.noteMode) append("，笔记模式")
            state.selected?.let {
                append("，当前选中第 ${Sudoku.rowOf(it) + 1} 行第 ${Sudoku.colOf(it) + 1} 列")
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { handleKeyEvent(it, state, onAction) }
            .focusable(),
    ) {
        val wide = maxWidth >= DesignTokens.Sizes.WideBreakpoint && maxWidth > maxHeight

        if (wide) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DesignTokens.Spacing.Md),
                horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Md),
            ) {
                BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    // 顶部对齐并预留提示条高度：这样 Snackbar 弹出时不会压住棋盘最后一行
                    val side = minOf(
                        maxWidth,
                        (maxHeight - DesignTokens.Sizes.SnackbarReserve).coerceAtLeast(160.dp),
                    )
                    BoardArea(
                        game = game,
                        state = state,
                        conflicts = conflicts,
                        description = boardDescription,
                        onAction = onAction,
                        modifier = Modifier.align(Alignment.TopCenter).size(side),
                    )
                }
                Column(
                    modifier = Modifier
                        .width(DesignTokens.Sizes.ControlPanel)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    StatusLine(game, state, done, total)
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                    PadPanel(game, state, onAction)
                    Spacer(Modifier.height(DesignTokens.Spacing.Md))
                    OptionsPanel(state, onAction)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(DesignTokens.Spacing.Md),
            ) {
                StatusLine(game, state, done, total)
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                BoardArea(
                    game = game,
                    state = state,
                    conflicts = conflicts,
                    description = boardDescription,
                    onAction = onAction,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                PadPanel(game, state, onAction)
                Spacer(Modifier.height(DesignTokens.Spacing.Md))
                OptionsPanel(state, onAction)
                Spacer(Modifier.height(DesignTokens.Spacing.Sm))
            }
        }
    }
}

/** 棋盘区域：棋盘 + 暂停遮挡（暂停时用白纸盖住题面，不能继续读题）。 */
@Composable
private fun BoardArea(
    game: Game,
    state: GameState,
    conflicts: BooleanArray,
    description: String,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        BoardCanvas(
            game = game,
            selected = state.selected,
            notes = state.notes,
            conflicts = conflicts,
            noteMode = state.noteMode,
            showNotes = state.showNotes,
            modifier = Modifier.fillMaxSize().semantics { contentDescription = description },
            onCellClick = { onAction(GameAction.Select(it)) },
        )
        if (state.paused) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                InkText(text = "已暂停", style = Ink.style(28.sp, Ink.Black, letterSpacing = 6.sp))
                Spacer(Modifier.height(DesignTokens.Spacing.Lg))
                InkButton(
                    text = "继续",
                    onClick = { onAction(GameAction.Resume) },
                    modifier = Modifier.width(160.dp),
                    emphasized = true,
                    compact = true,
                )
            }
        }
    }
}

/** 状态行：难度 / 计时 / 提示次数 / 进度，下方压一条手绘分隔线。 */
@Composable
private fun StatusLine(game: Game, state: GameState, done: Int, total: Int) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            InkText(text = game.difficulty.label, style = Ink.style(15.sp, Ink.Grey, letterSpacing = 2.sp))
            InkText(
                text = if (state.paused) "已暂停" else Sudoku.formatDuration(state.elapsed),
                style = Ink.style(18.sp, Ink.Black, letterSpacing = 1.sp),
            )
            if (state.hintsCount > 0) {
                InkText(text = "提示 ×${state.hintsCount}", style = Ink.style(14.sp, Ink.Grey))
            }
            InkText(text = "$done / $total", style = Ink.style(15.sp, Ink.Grey))
        }
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkDivider(seed = 5)
    }
}

/** 数字键盘面板：键盘 + 当前模式提示（填数 / 笔记）。 */
@Composable
private fun PadPanel(game: Game, state: GameState, onAction: (GameAction) -> Unit) {
    InkPanel(
        padding = PaddingValues(DesignTokens.Spacing.Md),
        seed = 55,
    ) {
        InkText(
            text = if (state.noteMode) "笔记模式：点数字记候选" else "点数字填入选中格",
            modifier = Modifier.fillMaxWidth(),
            style = Ink.style(12.sp, Ink.Light, letterSpacing = 1.sp),
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        val legalMask = if (state.noteMode || state.selected == null) {
            null
        } else {
            Sudoku.legalMask(game.current, state.selected)
        }
        NumberPad(
            legalMask = legalMask,
            enabled = state.interactive,
            onDigit = { onAction(GameAction.Digit(it)) },
            onErase = { onAction(GameAction.Digit(0)) },
        )
    }
}

/** 玩法开关 + 对局操作。 */
@Composable
private fun OptionsPanel(state: GameState, onAction: (GameAction) -> Unit) {
    InkPanel(
        padding = PaddingValues(DesignTokens.Spacing.Md),
        seed = 41,
    ) {
        InkToggleRow(
            label = "笔记模式",
            checked = state.noteMode,
            onCheckedChange = { onAction(GameAction.ToggleNoteMode) },
        )
        InkToggleRow(
            label = "显示笔记",
            checked = state.showNotes,
            onCheckedChange = { onAction(GameAction.ToggleShowNotes) },
        )
        InkToggleRow(
            label = "严格模式",
            checked = state.strictMode,
            onCheckedChange = { enabled -> onAction(GameAction.ToggleStrict(enabled)) },
        )
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        InkDivider(seed = 42)
        Spacer(Modifier.height(DesignTokens.Spacing.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
            InkButton("撤销", { onAction(GameAction.Undo) }, Modifier.weight(1f), compact = true)
            InkButton("重做", { onAction(GameAction.Redo) }, Modifier.weight(1f), compact = true)
            InkButton(
                "提示",
                { onAction(GameAction.Hint) },
                Modifier.weight(1f),
                compact = true,
                emphasized = true,
            )
        }
        Spacer(Modifier.height(DesignTokens.Spacing.Sm))
        Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
            InkButton(
                if (state.paused) "继续" else "暂停",
                { onAction(if (state.paused) GameAction.Resume else GameAction.Pause) },
                Modifier.weight(1f),
                compact = true,
            )
            InkButton("重置", { onAction(GameAction.Reset) }, Modifier.weight(1f), compact = true)
            InkButton(
                "菜单",
                { onAction(GameAction.Navigate(Screen.Menu)) },
                Modifier.weight(1f),
                compact = true,
            )
        }
    }
}

/**
 * 桌面键盘映射（仅 KeyDown 生效；暂停 / 结算时只保留撤销重做与继续）。
 */
private fun handleKeyEvent(
    event: KeyEvent,
    state: GameState,
    onAction: (GameAction) -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false

    if (event.isCtrlPressed && event.key == Key.Z) {
        onAction(if (event.isShiftPressed) GameAction.Redo else GameAction.Undo)
        return true
    }
    if (event.isCtrlPressed && event.key == Key.Y) {
        onAction(GameAction.Redo)
        return true
    }
    if (!state.interactive) {
        if (event.key == Key.Escape || event.key == Key.Spacebar) {
            onAction(GameAction.Resume)
            return true
        }
        return false
    }

    val digit = digitOf(event.key)
    if (digit > 0) {
        onAction(GameAction.Digit(digit))
        return true
    }

    return when (event.key) {
        Key.Zero, Key.NumPad0, Key.Delete, Key.Backspace -> {
            onAction(GameAction.Digit(0))
            true
        }
        Key.DirectionLeft -> { onAction(GameAction.Move(0, -1)); true }
        Key.DirectionRight -> { onAction(GameAction.Move(0, 1)); true }
        Key.DirectionUp -> { onAction(GameAction.Move(-1, 0)); true }
        Key.DirectionDown -> { onAction(GameAction.Move(1, 0)); true }
        Key.N -> { onAction(GameAction.ToggleNoteMode); true }
        Key.H -> { onAction(GameAction.Hint); true }
        Key.P, Key.Spacebar -> { onAction(GameAction.Pause); true }
        else -> false
    }
}

private val NumberKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val NumpadKeys = listOf(
    Key.NumPad1, Key.NumPad2, Key.NumPad3,
    Key.NumPad4, Key.NumPad5, Key.NumPad6,
    Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

/** 主键盘与小键盘的数字键 → 1..9，非数字键返回 0。 */
private fun digitOf(key: Key): Int {
    val mainIndex = NumberKeys.indexOf(key)
    if (mainIndex >= 0) return mainIndex + 1
    val numpadIndex = NumpadKeys.indexOf(key)
    return if (numpadIndex >= 0) numpadIndex + 1 else 0
}
