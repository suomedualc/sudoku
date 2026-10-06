package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Sudoku
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail
import kotlin.test.assertTrue

/**
 * 状态机单测：固定随机种子，覆盖落子 / 笔记 / 严格模式 / 撤销重做 / 提示 / 方向键 / 判胜 / 计时。
 * 与 `SudokuTest` 一样写在 commonTest，各目标共用。
 */
class GameReducerTest {
    private val seed = 20261006L

    private fun newGame(difficulty: Difficulty = Difficulty.Easy): GameState =
        GameReducer.reduce(GameState(), GameAction.NewGame(difficulty), Random(seed))

    private fun reduce(state: GameState, action: GameAction): GameState =
        GameReducer.reduce(state, action, Random(seed))

    private fun firstEmpty(state: GameState): Int =
        (0..80).first { state.game!!.current[it] == 0 }

    private fun firstGiven(state: GameState): Int =
        (0..80).first { Sudoku.isGiven(state.game!!, it) }

    @Test
    fun newGamePreparesFreshState() {
        val state = newGame()
        assertEquals(Screen.Game, state.screen)
        assertNotNull(state.game)
        assertEquals(0, state.elapsed)
        assertNull(state.selected)
        assertTrue(state.undoStack.isEmpty())
        assertTrue(state.notes.all { it == 0 })
        assertTrue(state.interactive)
    }

    @Test
    fun givenCellRejectsInput() {
        val pos = firstGiven(newGame())
        val state = reduce(newGame(), GameAction.Select(pos))
        val after = reduce(state, GameAction.Digit(5))
        assertEquals(state.game!!.current[pos], after.game!!.current[pos], "给定格不可被修改")
        assertNotNull(after.message)
        assertTrue(after.undoStack.isEmpty(), "被拒绝的输入不应进入撤销栈")
    }

    @Test
    fun digitPlacesAndSecondPressClears() {
        val pos = firstEmpty(newGame())
        var state = reduce(newGame(), GameAction.Select(pos))
        state = reduce(state, GameAction.Digit(3))
        assertEquals(3, state.game!!.current[pos])
        assertEquals(1, state.undoStack.size)

        state = reduce(state, GameAction.Digit(3))
        assertEquals(0, state.game!!.current[pos], "再按同一数字应清除")
    }

    @Test
    fun erasingEmptyCellDoesNotPolluteHistory() {
        val pos = firstEmpty(newGame())
        val state = reduce(reduce(newGame(), GameAction.Select(pos)), GameAction.Digit(0))
        assertTrue(state.undoStack.isEmpty(), "无变化的操作不应写入撤销栈")
    }

    @Test
    fun noteModeTogglesCandidateAndRejectsFilledCell() {
        val pos = firstEmpty(newGame())
        var state = reduce(newGame(), GameAction.Select(pos))
        state = reduce(state, GameAction.ToggleNoteMode)

        state = reduce(state, GameAction.Digit(7))
        assertEquals(1 shl 6, state.notes[pos])
        assertEquals(0, state.game!!.current[pos], "笔记不写入盘面")

        state = reduce(state, GameAction.Digit(7))
        assertEquals(0, state.notes[pos], "重复按同一数字应取消该候选")

        state = reduce(state, GameAction.ToggleNoteMode)
        state = reduce(state, GameAction.Digit(9))
        state = reduce(state, GameAction.ToggleNoteMode)
        val after = reduce(state, GameAction.Digit(4))
        assertEquals(9, after.game!!.current[pos])
        assertEquals(0, after.notes[pos])
        assertTrue(after.message!!.contains("无法记笔记"))
    }

    @Test
    fun strictModeRejectsDigitAlreadyInSameRow() {
        val game = newGame().game!!
        val given = firstGiven(newGame())
        val value = game.current[given]
        val target = (0..80).first {
            Sudoku.rowOf(it) == Sudoku.rowOf(given) && game.current[it] == 0
        }

        var state = reduce(newGame(), GameAction.Select(target))
        state = reduce(state, GameAction.ToggleStrict(true))
        val after = reduce(state, GameAction.Digit(value))

        assertEquals(0, after.game!!.current[target])
        assertTrue(after.message!!.contains("严格模式"))
    }

    @Test
    fun undoRedoRestoresBoardNotesAndHintFlags() {
        val pos = firstEmpty(newGame())
        var state = reduce(newGame(), GameAction.Select(pos))
        state = reduce(state, GameAction.Digit(5))

        val undone = reduce(state, GameAction.Undo)
        assertEquals(0, undone.game!!.current[pos])
        assertEquals(1, undone.redoStack.size)

        val redone = reduce(undone, GameAction.Redo)
        assertEquals(5, redone.game!!.current[pos])
        assertTrue(redone.redoStack.isEmpty())

        // 提示用的标记与计数必须一起回滚，否则两个字段会互相矛盾
        val hinted = reduce(redone, GameAction.Hint)
        assertTrue(hinted.hintUsed)
        assertEquals(1, hinted.hintsCount)

        val hintUndone = reduce(hinted, GameAction.Undo)
        assertFalse(hintUndone.hintUsed)
        assertEquals(0, hintUndone.hintsCount)
    }

    @Test
    fun revealThenUndoAndRedoKeepFlagsConsistent() {
        val revealed = reduce(newGame(), GameAction.Reveal)
        assertTrue(revealed.revealed, "看答案后应标记 revealed")
        assertTrue(revealed.settled, "看答案后盘面应锁定")
        assertFalse(revealed.won, "看答案不计胜场")
        assertTrue(revealed.game!!.current.contentEquals(revealed.game!!.solution))

        val undone = reduce(revealed, GameAction.Undo)
        assertFalse(undone.revealed)
        assertFalse(undone.settled)
        assertTrue(undone.game!!.current.contentEquals(undone.game!!.puzzle))

        // 重做一次 Reveal 不应留下 revealed=true 而 settled=false 的僵尸状态
        val redone = reduce(undone, GameAction.Redo)
        assertTrue(redone.revealed)
        assertTrue(redone.settled)
        assertFalse(redone.interactive)
    }

    @Test
    fun moveSkipsGivenCells() {
        val game = newGame().game!!
        val from = (0..80).firstOrNull { pos ->
            pos % 9 < 8 && game.current[pos] == 0 && Sudoku.isGiven(game, pos + 1)
        } ?: fail("测试数据里应存在「空格 + 紧邻给定格」的相邻模式")

        val moved = reduce(reduce(newGame(), GameAction.Select(from)), GameAction.Move(0, 1))
        val landed = moved.selected!!
        assertNotEquals(from + 1, landed, "不应停在给定格上")
        assertFalse(Sudoku.isGiven(game, landed))
        assertEquals(Sudoku.rowOf(from), Sudoku.rowOf(landed), "只应在同一行内移动")

        var expected = from
        var steps = 0
        do {
            expected = Sudoku.rowOf(expected) * 9 + (Sudoku.colOf(expected) + 1) % 9
            steps++
        } while (steps < 9 && Sudoku.isGiven(game, expected))
        assertEquals(expected, landed, "应落在右侧第一个非给定格")
    }

    @Test
    fun fillingWholeBoardWins() {
        val initial = newGame()
        val solution = initial.game!!.solution
        var state = initial
        for (pos in 0..80) {
            if (initial.game!!.current[pos] != 0) continue
            state = reduce(state, GameAction.Select(pos))
            state = reduce(state, GameAction.Digit(solution[pos]))
        }
        assertTrue(state.won)
        assertTrue(state.settled)
        assertFalse(state.interactive)
        assertTrue(state.message!!.contains("恭喜通关"))
    }

    @Test
    fun tickAndSelectRespectPauseAndScreen() {
        val state = newGame()
        assertEquals(1, reduce(state, GameAction.Tick).elapsed)

        val paused = reduce(state, GameAction.Pause)
        assertEquals(0, reduce(paused, GameAction.Tick).elapsed)
        assertNull(reduce(paused, GameAction.Select(0)).selected, "暂停时不接受选格")

        val away = reduce(state, GameAction.Navigate(Screen.Menu))
        assertEquals(0, reduce(away, GameAction.Tick).elapsed, "离开对局界面应停表")
    }

    @Test
    fun resetClearsPlayerInputHintsAndClock() {
        val pos = firstEmpty(newGame())
        var state = reduce(newGame(), GameAction.Select(pos))
        state = reduce(state, GameAction.Digit(4))
        state = reduce(state, GameAction.Tick)
        state = reduce(state, GameAction.Hint)
        state = reduce(state, GameAction.Pause)
        state = reduce(state, GameAction.Reset)

        assertEquals(0, state.game!!.current[pos])
        assertEquals(0, state.elapsed)
        assertFalse(state.hintUsed)
        assertEquals(0, state.hintsCount)
        assertFalse(state.paused)
        assertTrue(state.game!!.current.contentEquals(state.game!!.puzzle))
    }
}
