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

    data object Hint : GameAction
    data object Reveal : GameAction
    data object Reset : GameAction

    data object Undo : GameAction
    data object Redo : GameAction

    data object Pause : GameAction
    data object Resume : GameAction
    data object Tick : GameAction

    data class Navigate(val screen: Screen) : GameAction
    data object ConsumeMessage : GameAction
}
