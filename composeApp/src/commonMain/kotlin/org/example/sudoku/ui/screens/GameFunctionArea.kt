package org.example.sudoku.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.example.sudoku.core.Game
import org.example.sudoku.core.Sudoku
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.ui.components.InkButton
import org.example.sudoku.ui.components.InkToggleRow
import org.example.sudoku.ui.components.NumberPad
import org.example.sudoku.ui.i18n.Strings
import org.example.sudoku.ui.theme.DesignTokens

/*
 * 功能区：数字键（横排 / 九宫格）+ 偏好开关 + 「游戏设置」入口。
 *
 * 从 GameScreen.kt 拆出（2026-10-24）：只搬位置、不改逻辑。
 */

/**
 * 功能区：**纵向排列**——先数字键一行，再偏好开关一行。
 *
 * 此前数字键盘与开关立在棋盘**右侧**（360dp），现在挪到棋盘**下方**并压成两行：
 * 棋盘左右两侧因此彻底空出来，整页只有一条自上而下的中轴，视线不用左右来回找。
 * 撤走的不是功能——撤销 / 重做在棋盘上下的浮动条，提示 / 暂停 / 重置 / 菜单在顶栏，
 * 四项偏好就在下面这一行里。
 */
@Composable
internal fun FunctionArea(
    strings: Strings,
    game: Game,
    state: GameState,
    narrow: Boolean,
    /** 是否启用双键盘（笔记块）；由页面按纵向预算决定（见 `showNotePad`）。 */
    showNotePad: Boolean,
    onOpenToggles: () -> Unit,
    onAction: (GameAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
        NumberRow(strings, game, state, onAction, grid = narrow, showNotePad = showNotePad)
        if (narrow) {
            // 窄屏：四项偏好收进「游戏设置」抽屉（TopDrawer，覆盖层唯一形态）——
            // 常驻开关行在窄屏会把勾选框挤出屏幕（docs/08 §4-D1）
            InkButton(
                text = strings.gameSettings,
                onClick = onOpenToggles,
                compact = true,
            )
        } else {
            ToggleRow(strings, state, onAction)
        }
    }
}

/** 第一排：数字输入（宽屏 1–9 横排；窄屏 3×3 九宫格 + 竖置擦除，见 [NumberPad]）。 */
@Composable
internal fun NumberRow(
    strings: Strings,
    game: Game,
    state: GameState,
    onAction: (GameAction) -> Unit,
    grid: Boolean,
    showNotePad: Boolean = false,
) {
    // 笔记模式下不限制（要能记"不可能的数字"）；其余按盘面 + 选中格缓存——
    // 计时每秒触发一次重组，不缓存就是每秒白扫一遍 81 格。
    // 注意把判空放进 remember 的 key 里，而不是在分支里各调一次 remember。
    val legalMask = remember(game.current, state.selected, state.noteMode) {
        if (state.noteMode) null else state.selected?.let { Sudoku.legalMask(game.current, it) }
    }
    // 每个数字还剩几个：只在盘面变化时重算（心跳不触发重组计算）
    val remaining = remember(game) {
        IntArray(9) { digit -> 9 - game.current.count { it == digit + 1 } }
    }
    // 当前格的笔记掩码（空心键已记下的显示为实心）；无选中格时为 0
    val noteMask = remember(state.notes, state.selected) {
        state.selected?.let { state.notes[it] } ?: 0
    }
    // 双键盘只在"选中了一个**空格**"时出现：
    // - 没选格 → 点了会回 Msg.SelectFirst；
    // - 选中的是已填格 → 笔记写不进去（reducer 回 Msg.NoNoteOnFilled）。
    // 两种情况下摆一排点了会报错的键都没有意义。
    val noteRowUseful = state.selected?.let { game.current[it] == 0 } == true
    NumberPad(
        strings = strings,
        legalMask = legalMask,
        enabled = state.interactive,
        remaining = remaining,
        onDigit = { onAction(GameAction.Digit(it)) },
        onErase = { onAction(GameAction.Digit(0)) },
        grid = grid,
        // 两个条件**同时**成立才画：这一格能记笔记，且纵向预算放得下（见 showNotePad）
        showNoteRow = noteRowUseful && showNotePad,
        onNoteDigit = { onAction(GameAction.NoteDigit(it)) },
        noteMask = noteMask,
    )
}

/**
 * 第二排：四项偏好**一行**排开。
 *
 * 竖着排要占 4×42 ≈ 170dp，棋盘就得再矮 130dp——而它们是"设一次就不管"的开关，
 * 不值得拿棋盘的尺寸去换。横排后整条功能区只有 102dp。
 * 代价是每项宽度只有棋盘宽的 1/4：标签一律 4 个字以内，`maxLines = 1` 兜住更长的文案。
 */
@Composable
internal fun ToggleRow(strings: Strings, state: GameState, onAction: (GameAction) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.Sm)) {
        InkToggleRow(
            label = strings.toggleNoteMode,
            checked = state.noteMode,
            onCheckedChange = { onAction(GameAction.ToggleNoteMode) },
            modifier = Modifier.weight(1f),
        )
        InkToggleRow(
            label = strings.toggleShowNotes,
            checked = state.showNotes,
            onCheckedChange = { onAction(GameAction.ToggleShowNotes) },
            modifier = Modifier.weight(1f),
        )
        InkToggleRow(
            label = strings.toggleHintCandidates,
            checked = state.hintCandidates,
            onCheckedChange = { onAction(GameAction.ToggleHintCandidates) },
            modifier = Modifier.weight(1f),
        )
        InkToggleRow(
            label = strings.toggleStrict,
            checked = state.strictMode,
            onCheckedChange = { enabled -> onAction(GameAction.ToggleStrict(enabled)) },
            modifier = Modifier.weight(1f),
        )
    }
}

