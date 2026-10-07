package org.example.sudoku.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 棋盘光标的纯逻辑单测：初始位置、方向键导航、填字后的自动跳转。
 *
 * 这里最有价值的是**两种跳转策略的对比用例**——"九宫格优先"能被选中的理由
 * （宫内空格按读序排列时它与横向优先逐格相同、只在宫里还有更靠前的空格时才不同）
 * 是一条**可验证的断言**，不是一句审美判断。改策略前先跑这里。
 */
class CellCursorTest {

    /**
     * 造一个可控盘面：其余格子都填上 [fill]，[blanks] 留空，[givens] 里的格子算题面
     * （题面格 = `puzzle != 0`，会被方向键跳过）。
     */
    private fun gameWith(blanks: Set<Int>, givens: Set<Int> = emptySet(), fill: Int = 1): Game {
        val current = IntArray(81) { if (it in blanks) 0 else fill }
        val puzzle = IntArray(81) { pos -> if (pos in givens) current[pos] else 0 }
        return Game(puzzle = puzzle, current = current, solution = current.copyOf(), difficulty = Difficulty.Easy)
    }

    // ---------- 初始位置 ----------

    @Test
    fun firstEmptyIsTheFirstBlankInReadingOrder() {
        val board = gameWith(blanks = setOf(40, 5, 61)).current
        assertEquals(5, CellCursor.firstEmpty(board), "读序：从左到右、从上到下")
    }

    @Test
    fun firstEmptyIsNullWhenTheBoardIsFull() {
        assertNull(CellCursor.firstEmpty(gameWith(blanks = emptySet()).current))
    }

    // ---------- 自动跳转：九宫格优先 ----------

    @Test
    fun afterFillKeepsTheCursorInsideTheCurrentBox() {
        // 宫 0（行 1–3、列 1–3）里 0 号格还空着，而 0 在 20 的**读序之前**——
        // 横向优先会从 20 跳到 60（本宫之外），九宫格优先回头把本宫填完
        val game = gameWith(blanks = setOf(0, 20, 60))
        assertEquals(0, CellCursor.nextAfterFill(game.current, 20), "本宫还有空格 → 留在本宫")
        assertEquals(60, CellCursor.nextByReadingOrder(game.current, 20), "（对照）横向优先会跳出本宫")
    }

    @Test
    fun afterFillAgreesWithReadingOrderWhenBoxBlanksAreInReadingOrder() {
        // 这是选九宫格优先的关键论据：最常见的情形下它**就是**横向优先，
        // 因此不会让习惯横向推进的玩家吃亏
        val game = gameWith(blanks = setOf(10, 20, 60))
        assertEquals(20, CellCursor.nextAfterFill(game.current, 10))
        assertEquals(20, CellCursor.nextByReadingOrder(game.current, 10))
    }

    @Test
    fun afterFillFallsBackToReadingOrderWhenTheBoxIsFull() {
        val game = gameWith(blanks = setOf(20, 60))
        // 20 是本宫唯一的空格（填完它本宫就满了）→ 退回横向优先
        assertEquals(60, CellCursor.nextAfterFill(game.current, 20))
    }

    @Test
    fun afterFillReturnsNullWhenThereIsNoBlankLeft() {
        assertNull(CellCursor.nextAfterFill(gameWith(blanks = emptySet()).current, 40), "盘面已满 → 停在原地")
    }

    @Test
    fun afterFillAlwaysLandsOnABlankOrNull() {
        // 不变量：跳转的落点必须是空格（或 null）。逐格走一遍整盘，防"跳到已填格"这类越界
        val game = gameWith(blanks = setOf(0, 8, 20, 33, 44, 60, 72, 80))
        for (pos in 0..80) {
            val next = CellCursor.nextAfterFill(game.current, pos)
            if (next != null) {
                assertEquals(0, game.current[next], "从 $pos 跳转到了非空格 $next")
            }
        }
    }

    // ---------- 方向键 ----------

    @Test
    fun arrowMovesToTheNearestBlankInThatDirection() {
        val game = gameWith(blanks = setOf(30, 33)) // 第 4 行：列 4 与列 7
        assertEquals(30, CellCursor.nextInDirection(game, 27, 0, 1), "→ 停在最近的空格")
        assertEquals(33, CellCursor.nextInDirection(game, 30, 0, 1))
        assertEquals(30, CellCursor.nextInDirection(game, 33, 0, 1), "环绕：本行末尾之后回到本行开头")
    }

    @Test
    fun arrowStaysInItsOwnRowOrColumn() {
        val game = gameWith(blanks = setOf(4, 22, 58))
        assertEquals(22, CellCursor.nextInDirection(game, 4, 1, 0), "↓ 只在同一列里走")
        assertEquals(58, CellCursor.nextInDirection(game, 22, 1, 0), "↓ 绕过本列的题面 / 已填格")
        assertEquals(4, CellCursor.nextInDirection(game, 22, -1, 0), "↑ 回到本列上方的空格")
    }

    @Test
    fun arrowSkipsGivenCells() {
        // 第 4 行：30 是题面格，33 是空格
        val game = gameWith(blanks = setOf(27, 33), givens = setOf(30))
        assertEquals(33, CellCursor.nextInDirection(game, 27, 0, 1), "题面格改不动，不停在它上面")
    }

    @Test
    fun arrowFallsBackToAnEditableCellWhenThatRowHasNoBlank() {
        // 整行都填满了：若只认空格，键盘玩家就再也回不到自己填过的格子上去改它
        val game = gameWith(blanks = setOf(0), givens = setOf(30))
        val landed = CellCursor.nextInDirection(game, 27, 0, 1)
        assertEquals(28, landed, "本行无空格 → 退到第一个可编辑格")
        assertEquals(0, game.puzzle[landed], "退到的必须是玩家自己填的格（可改 / 可擦），不能是题面格")
    }

    @Test
    fun shiftArrowStepsThroughFilledCells() {
        // 默认方向键是"空格优先"，会跳过自己填过的格子；
        // Shift（blanksFirst = false）是**逐格**模式——回到刚填过的那一格去改 / 擦的通道
        val game = gameWith(blanks = setOf(30, 33))
        assertEquals(33, CellCursor.nextInDirection(game, 30, 0, 1), "普通模式：跳过已填格，停在空格")
        assertEquals(31, CellCursor.nextInDirection(game, 30, 0, 1, blanksFirst = false), "逐格模式：就走一格")
    }

    @Test
    fun shiftArrowStillSkipsGivenCells() {
        // 逐格模式也不能停在题面格上：停在它上面按数字没反应，等于"键盘失灵"
        val game = gameWith(blanks = setOf(27, 33), givens = setOf(28, 29))
        val landed = CellCursor.nextInDirection(game, 27, 0, 1, blanksFirst = false)
        assertEquals(30, landed, "连着两个题面格都要跳过，停在下一个可编辑格")
        assertEquals(0, game.puzzle[landed], "落点必须是玩家自己能改的格")
    }

    @Test
    fun arrowNeverLandsOnAGivenCell() {
        val game = gameWith(blanks = setOf(10), givens = setOf(4, 13, 22, 31))
        for (dir in listOf(0 to 1, 0 to -1, 1 to 0, -1 to 0)) {
            val landed = CellCursor.nextInDirection(game, 40, dir.first, dir.second)
            assertEquals(0, game.puzzle[landed], "从 40 往 $dir 不应落在题面格 $landed 上")
        }
    }

    @Test
    fun boxCellsCoverExactlyOneBoxInReadingOrder() {
        val cells = CellCursor.boxCells(4) // 中宫：行 4–6、列 4–6
        assertEquals(listOf(30, 31, 32, 39, 40, 41, 48, 49, 50), cells.toList())
        cells.forEach { assertEquals(4, Sudoku.boxOf(it)) }
    }
}
