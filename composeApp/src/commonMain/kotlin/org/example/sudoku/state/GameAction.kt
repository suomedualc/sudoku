package org.example.sudoku.state

import org.example.sudoku.core.CellCursor
import org.example.sudoku.core.Difficulty

/** 全部用户输入与系统事件。UI 层只负责把它们派发出来。 */
sealed interface GameAction {
    data class NewGame(val difficulty: Difficulty) : GameAction
    data class Select(val pos: Int) : GameAction

    /**
     * 填数（0 = 擦除）。
     *
     * [advance] 为 true 时，若本次**确实填入了一个数字**（不是擦除、不是笔记、
     * 也不是"再按一次同一个数字取消"），光标自动跳到下一个空格（策略见 [CellCursor.nextAfterFill]）。
     *
     * **只有物理键盘输入才传 true**：键盘玩家没有"点"这个动作，填完就得靠自动跳转往前走；
     * 而鼠标玩家点的是屏幕上的数字键，他自己点下一格更自然（点哪是哪），
     * 光标乱跳反而会打断"看盘面 → 点格子"的节奏。
     */
    data class Digit(val value: Int, val advance: Boolean = false) : GameAction

    /**
     * 把光标放到**第一个空格**（读序：从左到右、从上到下）。
     *
     * 只在还没有选中格时生效（已有选中就原样返回）——因此它既可以当"开局定位"用，
     * 又不会因为重复派发而把玩家正在看的那格顶掉（比如计时心跳里误派发）。
     */
    data object FocusFirstEmpty : GameAction

    /** 方向键移动光标（沿方向找下一个空格，见 [CellCursor.nextInDirection]）。 */
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
