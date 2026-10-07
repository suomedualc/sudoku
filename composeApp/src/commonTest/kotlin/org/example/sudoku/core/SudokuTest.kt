package org.example.sudoku.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * core 层纯逻辑单元测试：在 commonTest 中编写，可在 JVM / Native / Web 各目标复用。
 * 锁定的关键不变量：出题唯一可解、求解器正确、时长格式、冲突检测。
 */
class SudokuTest {
    private val rng = Random(42)

    @Test
    fun generateProducesUniqueSolvablePuzzle() {
        val game = Sudoku.generate(Difficulty.Easy, rng)
        assertEquals(81, game.solution.size)
        assertFalse(Sudoku.conflictFlags(game.solution).any { it }, "解必须是合法终盘")
        assertEquals(1, Sudoku.countSolutions(game.puzzle, 2), "题目必须有唯一解")
        assertTrue(game.current.contentEquals(game.puzzle), "初始盘应等于题目盘")
    }

    @Test
    fun solveFindsTheEmbeddedSolution() {
        val game = Sudoku.generate(Difficulty.Normal, rng)
        val solved = Sudoku.solve(game.puzzle)
        assertTrue(solved.contentEquals(game.solution), "求解结果应等于内置解")
    }

    @Test
    fun formatDurationRendersCorrectly() {
        assertEquals("00:05", Sudoku.formatDuration(5))
        assertEquals("01:00", Sudoku.formatDuration(60))
        assertEquals("1:00:00", Sudoku.formatDuration(3600))
        assertEquals("1:02:03", Sudoku.formatDuration(3723))
    }

    @Test
    fun generateReachesTargetBlanksForEveryDifficulty() {
        // 旧实现单次贪心挖洞在大师档只有约 88% 能挖满 56 空，这条用例锁住"必须达标"
        Difficulty.entries.forEach { difficulty ->
            val game = Sudoku.generate(difficulty, Random(2026))
            assertEquals(
                difficulty.targetBlanks,
                game.puzzle.count { it == 0 },
                "${difficulty.name} 应挖满目标空格数",
            )
            assertEquals(1, Sudoku.countSolutions(game.puzzle, 2), "${difficulty.name} 题目必须唯一解")
            assertEquals(game.current.toList(), game.puzzle.toList(), "初始盘面应等于题面")
        }
    }

    @Test
    fun conflictFlagsDetectsDuplicates() {
        val board = IntArray(81)
        board[0] = 5
        board[1] = 5 // 同行重复
        val flags = Sudoku.conflictFlags(board)
        assertTrue(flags[0] && flags[1], "同行重复应被标记")
    }
}
