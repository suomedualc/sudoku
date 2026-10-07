package org.example.sudoku.ui

import org.example.sudoku.core.Difficulty
import org.example.sudoku.state.GameAction
import org.example.sudoku.state.GameAction.Navigate
import org.example.sudoku.state.GameReducer
import org.example.sudoku.state.GameState
import org.example.sudoku.state.Screen
import org.example.sudoku.ui.components.FloatingPadPolicy
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 悬浮数字面板的纯逻辑测试：位置计算（越界翻转 / 贴边）与开合判据。
 *
 * 这些行为靠肉眼验收成本很高（要把窗口拖到各种尺寸、点不同位置的格子），
 * 抽成纯函数后几行断言就能锁住。
 */
class FloatingPadPolicyTest {

    private val cell = 80f      // 单格边长（示意：720 / 9）
    private val padWidth = 132f
    private val padHeight = 186f
    private val board = 720f
    private val gap = 8f

    private fun place(cellIndex: Int, boardSide: Float = board, cellSide: Float = cell) =
        FloatingPadPolicy.placement(
            cell = cellIndex,
            cellSide = cellSide,
            padWidth = padWidth,
            padHeight = padHeight,
            boardSide = boardSide,
            gap = gap,
        )

    @Test
    fun padSizeShrinksWithCandidateCountSoKeysStayLarge() {
        val key = 48f
        val padGap = 6f
        val padding = 10f

        // 列数按候选个数自适应（最多 3 列）
        assertEquals(1, FloatingPadPolicy.columns(1))
        assertEquals(2, FloatingPadPolicy.columns(2))
        assertEquals(3, FloatingPadPolicy.columns(5))
        assertEquals(3, FloatingPadPolicy.columns(9))
        // 行数：候选为 0 时也留一行，用来放"本格无可填数字"
        assertEquals(1, FloatingPadPolicy.rows(0))
        assertEquals(1, FloatingPadPolicy.rows(3))
        assertEquals(2, FloatingPadPolicy.rows(4))
        assertEquals(3, FloatingPadPolicy.rows(9))

        val one = FloatingPadPolicy.padWidth(1, key, padGap, padding)
        val two = FloatingPadPolicy.padWidth(2, key, padGap, padding)
        val three = FloatingPadPolicy.padWidth(3, key, padGap, padding)
        assertTrue(one < two && two < three, "候选越少面板越窄：$one / $two / $three")
        assertEquals(padding * 2 + key, one)
        assertEquals(padding * 2 + 3 * key + 2 * padGap, three)
        // 一行候选 → 面板高度只有一个键高（+ 内边距）
        assertEquals(padding * 2 + key, FloatingPadPolicy.padHeight(2, key, padGap, padding))
        assertEquals(padding * 2 + 2 * key + padGap, FloatingPadPolicy.padHeight(4, key, padGap, padding))
    }

    @Test
    fun placementSitsRightOfTheCell() {
        // 中间一行一列：右侧放得下 → 贴右侧，且与该格垂直居中
        val p = place(cellIndex = 4 * 9 + 4)
        assertEquals(4 * cell + cell + gap, p.x)
        assertEquals(4 * cell + cell / 2f - padHeight / 2f, p.y)
    }

    @Test
    fun placementFlipsToLeftNearRightEdge() {
        val p = place(cellIndex = 0 * 9 + 8)
        assertEquals(8 * cell - padWidth - gap, p.x)
    }

    @Test
    fun placementClampsVerticallyInsideBoard() {
        val topRow = place(cellIndex = 0 * 9 + 0)
        assertEquals(0f, topRow.y, "第一行居中会越上界 → 贴顶")
        val bottomRow = place(cellIndex = 8 * 9 + 4)
        assertTrue(bottomRow.y >= 0f)
        assertTrue(bottomRow.y + padHeight <= board, "第八行不能把面板推出棋盘下沿")
    }

    @Test
    fun placementStaysInsideBoardWhenBothSidesAreTooNarrow() {
        // 极窄棋盘（面板比任一空档都宽）：兜底贴右，至少要完整可见
        val p = place(cellIndex = 0, boardSide = 140f, cellSide = 140f / 9f)
        assertTrue(p.x >= 0f)
        assertTrue(p.x + padWidth <= 140f)
        assertEquals(0f, p.y, "棋盘比面板还矮时贴顶")
    }

    @Test
    fun padStaysOpenOnlyWhileSelectingOrTicking() {
        assertTrue(FloatingPadPolicy.keepsOpen(GameAction.Select(3)))
        assertTrue(FloatingPadPolicy.keepsOpen(GameAction.Move(0, 1)), "方向键换格：面板跟着走")
        assertTrue(FloatingPadPolicy.keepsOpen(GameAction.SyncElapsed(12)), "每秒心跳不能让面板闪掉")

        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Digit(5)), "填数后面板应关闭")
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Digit(0)), "擦除后应关闭")
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Undo))
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Redo))
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Hint))
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Pause))
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.Resume))
        assertFalse(FloatingPadPolicy.keepsOpen(GameAction.ToggleNoteMode))
        assertFalse(FloatingPadPolicy.keepsOpen(Navigate(Screen.Menu)))
    }

    @Test
    fun padOpensOnlyForEmptyCellWithPrecisePointer() {
        val state = GameReducer.reduce(
            GameState(),
            GameAction.NewGame(Difficulty.Easy),
            Random(20261006L),
        ).state
        val empty = (0..80).first { state.game!!.current[it] == 0 }
        val given = (0..80).first { state.game!!.current[it] != 0 }

        assertTrue(FloatingPadPolicy.shouldOpen(state, empty, precisePointer = true))
        assertFalse(
            FloatingPadPolicy.shouldOpen(state, given, precisePointer = true),
            "给定格只能选中，不弹输入面板",
        )
        assertFalse(
            FloatingPadPolicy.shouldOpen(state, empty, precisePointer = false),
            "手指（触屏）不弹：面板会被手指挡住，走常驻数字键盘",
        )
        assertFalse(
            FloatingPadPolicy.shouldOpen(state.copy(paused = true), empty, precisePointer = true),
            "暂停时棋盘不可交互，不弹",
        )
        assertFalse(
            FloatingPadPolicy.shouldOpen(GameState(), empty, precisePointer = true),
            "还没开局不弹",
        )
    }

    @Test
    fun clickingTheOpenCellAgainClosesThePad() {
        val state = GameReducer.reduce(
            GameState(),
            GameAction.NewGame(Difficulty.Easy),
            Random(20261006L),
        ).state
        val first = (0..80).first { state.game!!.current[it] == 0 }
        val second = (0..80).first { it != first && state.game!!.current[it] == 0 }
        val given = (0..80).first { state.game!!.current[it] != 0 }

        assertEquals(first, FloatingPadPolicy.nextOnCellClick(null, state, first, true), "首次点空格 → 打开")
        assertNull(
            FloatingPadPolicy.nextOnCellClick(first, state, first, true),
            "再点一次同一格 → 收起（否则只能靠 Esc 关，是交互死角）",
        )
        assertEquals(
            second,
            FloatingPadPolicy.nextOnCellClick(first, state, second, true),
            "已开着时点另一个空格 → 直接换过去",
        )
        assertNull(
            FloatingPadPolicy.nextOnCellClick(first, state, given, true),
            "点给定格 → 只选中，面板收起",
        )
        assertNull(
            FloatingPadPolicy.nextOnCellClick(null, state, first, false),
            "手指点击不弹（任何时候都不弹）",
        )
    }
}
