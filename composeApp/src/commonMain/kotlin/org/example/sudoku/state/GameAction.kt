package org.example.sudoku.state

import org.example.sudoku.core.Difficulty

/** 全部用户输入与系统事件。UI 层只负责把它们派发出来。 */
sealed interface GameAction {
    data class NewGame(val difficulty: Difficulty) : GameAction
    data class Select(val pos: Int) : GameAction
    data class Digit(val value: Int) : GameAction
    data class Move(val dRow: Int, val dCol: Int) : GameAction

    data object ToggleNoteMode : GameAction
    data class ToggleStrict(val enabled: Boolean) : GameAction
    data object ToggleShowNotes : GameAction

    /** 候选提示开关：选中格里显示规则允许的数字（见 [GameState.hintCandidates]）。 */
    data object ToggleHintCandidates : GameAction

    data object Hint : GameAction
    data object Reveal : GameAction
    data object Reset : GameAction

    data object Undo : GameAction
    data object Redo : GameAction

    data object Pause : GameAction
    data object Resume : GameAction

    /**
     * 计时同步：把**由真实时间算出的**已用秒数写回状态（替代旧的"每秒 +1"）。
     * 时间源由 [GameViewModel] 注入，因此 reducer 依旧是纯函数。
     */
    data class SyncElapsed(val seconds: Int) : GameAction

    data class Navigate(val screen: Screen) : GameAction
}
