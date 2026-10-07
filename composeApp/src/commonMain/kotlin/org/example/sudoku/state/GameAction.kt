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

    /** 夜墨模式开关：深底浅墨（见 [GameState.darkMode]）。 */
    data object ToggleDarkMode : GameAction

    /**
     * 取消选格：清掉选中框、同行列宫高亮、同数字高亮等一切**临时**标记，让棋盘回到开局时的纯净配色。
     *
     * 由"点棋盘以外的空白处"触发。注意**不清**冲突排线——那是对局数据（真的填重了），
     * 不是临时标记；清掉它等于骗玩家。
     */
    data object Deselect : GameAction

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
