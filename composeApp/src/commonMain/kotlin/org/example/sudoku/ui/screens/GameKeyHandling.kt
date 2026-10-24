package org.example.sudoku.ui.screens

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameState
import org.example.sudoku.state.KeyMap
import org.example.sudoku.ui.components.KeyToken

/*
 * 桌面键盘映射：数字 / 擦除 / 方向 / 笔记 / 提示 / 暂停 / 撤销重做，以及三条硬兜底。
 *
 * 从 GameScreen.kt 拆出（2026-10-24）：只搬位置、不改逻辑。
 */

/**
 * 桌面键盘映射（仅 KeyDown 生效；暂停 / 结算时只保留撤销重做与继续）。
 *
 * **通关期不在本函数处理**：那时是模态的通关抽屉在管事（`Enter` 再来一局 / `Esc` 返回首页，
 * 其余键一律吞掉），所以这里既没有 `won` 分支，也不会出现"空格被当成继续"的串扰。
 */
internal fun handleKeyEvent(
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
        // 物理键盘填数 → 填完自动跳到下一个空格（策略见 CellCursor.nextAfterFill）。
        // 屏幕上的数字键不跳：鼠标玩家自己点下一格更自然。
        onAction(GameAction.Digit(digit, advance = true))
        return true
    }

    // 自定义键位：令牌匹配（1–9 留给填数，绑不到这里，见 KeyMap.accepts）
    val token = KeyToken.of(event)
    if (token != null) {
        val move = state.keyMap.move(token)
        if (move != null) {
            // 默认逐格（可停在已填格，回去改 / 擦）；Shift = 跳到下一个空格（快速推进）
            onAction(GameAction.Move(move.first, move.second, jumpToBlank = event.isShiftPressed))
            return true
        }
        if (state.keyMap.isErase(token)) {
            onAction(GameAction.Digit(0))
            return true
        }
    }

    return when (event.key) {
        // 兜底（**不受键位设置影响**）：方向键始终可移动、退格 / Del 始终可擦除。
        // 键位设置是为了更好用，不能让人把自己锁在门外——这是三条硬兜底。
        Key.Delete, Key.Backspace -> {
            onAction(GameAction.Digit(0))
            true
        }
        Key.DirectionLeft -> { onAction(GameAction.Move(0, -1, event.isShiftPressed)); true }
        Key.DirectionRight -> { onAction(GameAction.Move(0, 1, event.isShiftPressed)); true }
        Key.DirectionUp -> { onAction(GameAction.Move(-1, 0, event.isShiftPressed)); true }
        Key.DirectionDown -> { onAction(GameAction.Move(1, 0, event.isShiftPressed)); true }
        Key.N -> { onAction(GameAction.ToggleNoteMode); true }
        Key.H -> { onAction(GameAction.Hint); true }
        Key.P, Key.Spacebar -> { onAction(GameAction.Pause); true }
        else -> false
    }
}

internal val NumberKeys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
internal val NumpadKeys = listOf(
    Key.NumPad1, Key.NumPad2, Key.NumPad3,
    Key.NumPad4, Key.NumPad5, Key.NumPad6,
    Key.NumPad7, Key.NumPad8, Key.NumPad9,
)

/** 主键盘与小键盘的数字键 → 1..9，非数字键返回 0。 */
internal fun digitOf(key: Key): Int {
    val mainIndex = NumberKeys.indexOf(key)
    if (mainIndex >= 0) return mainIndex + 1
    val numpadIndex = NumpadKeys.indexOf(key)
    return if (numpadIndex >= 0) numpadIndex + 1 else 0
}

