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
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * 状态机单测：固定随机种子，覆盖落子 / 笔记 / 严格模式 / 撤销重做 / 提示 / 方向键 / 判胜 / 计时同步。
 * 与 `SudokuTest` 一样写在 commonTest，各目标共用。
 */
class GameReducerTest {
    private val seed = 20261006L

    private fun newGame(difficulty: Difficulty = Difficulty.Easy): GameState =
        GameReducer.reduce(GameState(), GameAction.NewGame(difficulty), Random(seed)).state

    /** 只关心新状态时的便捷写法。 */
    private fun reduce(state: GameState, action: GameAction): GameState =
        GameReducer.reduce(state, action, Random(seed)).state

    /** 需要检查一次性提示时的写法。 */
    private fun reduction(state: GameState, action: GameAction): Reduction =
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

        val outcome = reduction(state, GameAction.Digit(5))

        assertEquals(state.game!!.current[pos], outcome.state.game!!.current[pos], "给定格不可被修改")
        assertNotNull(outcome.message, "应给出提示")
        assertTrue(outcome.state.undoStack.isEmpty(), "被拒绝的输入不应进入撤销栈")
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

        val outcome = reduction(state, GameAction.Digit(4))
        assertEquals(9, outcome.state.game!!.current[pos])
        assertEquals(0, outcome.state.notes[pos])
        assertTrue(outcome.message!!.contains("无法记笔记"))
    }

    @Test
    fun hintCandidatesToggleIsPureSettingAndSurvivesNewGame() {
        val fresh = newGame()
        assertFalse(fresh.hintCandidates, "候选提示默认关闭（是否要看推演结果由玩家决定）")

        // 效果体现在所有空格的候选数字上，因此不应顺手改动玩家的选中格
        val on = reduce(fresh, GameAction.ToggleHintCandidates)
        assertTrue(on.hintCandidates)
        assertEquals(fresh.selected, on.selected, "设置类开关不该移动选中格")
        assertNotNull(reduction(on, GameAction.ToggleHintCandidates).message, "切换应有文案反馈")

        // 换一局：偏好保留（与另三项开关同一约定）
        val next = reduce(on, GameAction.NewGame(Difficulty.Normal))
        assertTrue(next.hintCandidates, "新开一局应继承候选提示开关")
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

        val outcome = reduction(state, GameAction.Digit(value))

        assertEquals(0, outcome.state.game!!.current[target])
        assertTrue(outcome.message!!.contains("严格模式"))
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
        var winMessage: String? = null
        for (pos in 0..80) {
            if (initial.game!!.current[pos] != 0) continue
            state = reduce(state, GameAction.Select(pos))
            val outcome = reduction(state, GameAction.Digit(solution[pos]))
            state = outcome.state
            if (outcome.message != null) winMessage = outcome.message
        }
        assertTrue(state.won)
        assertTrue(state.settled)
        assertFalse(state.interactive)
        assertTrue(winMessage!!.contains("恭喜通关"), "通关提示应随最后一次落子返回")
    }

    @Test
    fun syncElapsedWritesRealSecondsOnlyInGame() {
        val state = newGame()
        val once = reduce(state, GameAction.SyncElapsed(5))
        assertEquals(5, once.elapsed, "对局中应写入真实秒数")
        assertSame(once, reduce(once, GameAction.SyncElapsed(5)), "秒数未变化时不应产生新状态")

        val away = reduce(state, GameAction.Navigate(Screen.Menu))
        assertEquals(0, reduce(away, GameAction.SyncElapsed(9)).elapsed, "离开对局界面不接受计时更新")

        val paused = reduce(state, GameAction.Pause)
        assertNull(reduce(paused, GameAction.Select(0)).selected, "暂停时不接受选格")
    }

    @Test
    fun resetClearsPlayerInputHintsAndClock() {
        val pos = firstEmpty(newGame())
        var state = reduce(newGame(), GameAction.Select(pos))
        state = reduce(state, GameAction.Digit(4))
        state = reduce(state, GameAction.SyncElapsed(42))
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

    /**
     * "点棋盘以外的空白处 → 棋盘回到干净样子"：只清 `selected`——选中框、同行列宫高亮、
     * 同数字高亮都是从它派生出来的，清掉它就都没了。
     *
     * **盘面 / 笔记 / 计时 / 提示次数一律不动**：要清盘面另有 [GameAction.Reset]。
     * 顺带说明冲突排线为什么也不清——那是对局数据（真的填重了），不是临时标记。
     */
    @Test
    fun deselectClearsOnlyTemporaryMarks() {
        val pos = firstEmpty(newGame())
        var state = reduce(newGame(), GameAction.Select(pos))
        state = reduce(state, GameAction.Digit(4))
        state = reduce(state, GameAction.SyncElapsed(42))
        state = reduce(state, GameAction.Hint)

        val boardBefore = state.game!!.current.copyOf()
        val hintsBefore = state.hintsCount
        val undoBefore = state.undoStack.size

        state = reduce(state, GameAction.Deselect)

        assertNull(state.selected, "选中的格子应被清掉")
        assertTrue(boardBefore.contentEquals(state.game!!.current), "盘面不动：清的是高亮，不是玩家填进去的数")
        assertEquals(42, state.elapsed, "计时不动")
        assertEquals(hintsBefore, state.hintsCount, "提示次数不动")
        assertEquals(undoBefore, state.undoStack.size, "撤销栈不动：撤销仍然能回到上一步")
    }
}
