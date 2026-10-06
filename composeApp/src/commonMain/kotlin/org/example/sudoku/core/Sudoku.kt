package org.example.sudoku.core

import kotlin.random.Random

//! 数独领域内核：零平台依赖，可在 Android / iOS / Desktop / Web 复用。
//! 算法沿用原项目的「位掩码约束 + MRV（最少候选优先）回溯」。

typealias Board = IntArray

/** 可移植的二进制位计数（公共标准库无 countOneBits，避免依赖 JVM 专属 API）。 */
private fun bitCount(value: Int): Int {
    var v = value
    var count = 0
    while (v != 0) {
        count += v and 1
        v = v ushr 1
    }
    return count
}

enum class Difficulty(val label: String, val targetBlanks: Int) {
    Easy("简单", 40),
    Normal("普通", 46),
    Hard("困难", 52),
    Expert("大师", 56),
}

data class Game(
    val puzzle: Board,
    val current: Board,
    val solution: Board,
    val difficulty: Difficulty,
)

object Sudoku {
    const val ALL_MASK = 0x1FF

    fun rowOf(pos: Int) = pos / 9
    fun colOf(pos: Int) = pos % 9
    fun boxOf(pos: Int) = rowOf(pos) / 3 * 3 + colOf(pos) / 3

    /** 求解器：行 / 列 / 宫各用一个 9 位掩码记录已占用数字。 */
    class Solver(board: Board) {
        private val cells = board.copyOf()
        private val rows = IntArray(9)
        private val cols = IntArray(9)
        private val boxes = IntArray(9)

        init {
            for (pos in 0..80) {
                val value = cells[pos]
                if (value != 0) toggle(pos, value, true)
            }
        }

        fun get(pos: Int) = cells[pos]
        fun snapshot(): Board = cells.copyOf()

        private fun toggle(pos: Int, digit: Int, on: Boolean) {
            val bit = 1 shl (digit - 1)
            val r = rowOf(pos)
            val c = colOf(pos)
            val b = boxOf(pos)
            if (on) {
                rows[r] = rows[r] or bit
                cols[c] = cols[c] or bit
                boxes[b] = boxes[b] or bit
            } else {
                rows[r] = rows[r] and bit.inv()
                cols[c] = cols[c] and bit.inv()
                boxes[b] = boxes[b] and bit.inv()
            }
        }

        fun candidates(pos: Int): Int =
            ALL_MASK and (rows[rowOf(pos)] or cols[colOf(pos)] or boxes[boxOf(pos)]).inv()

        fun place(pos: Int, digit: Int) {
            cells[pos] = digit
            toggle(pos, digit, true)
        }

        fun unplace(pos: Int) {
            val digit = cells[pos]
            if (digit == 0) return
            toggle(pos, digit, false)
            cells[pos] = 0
        }

        /** MRV：返回候选最少的空格。 */
        fun pick(): PickResult {
            var best = -1
            var bestMask = 0
            var bestCount = 99
            for (pos in 0..80) {
                if (cells[pos] != 0) continue
                val mask = candidates(pos)
                val count = bitCount(mask)
                if (count == 0) return PickResult.Dead
                if (count < bestCount) {
                    bestCount = count
                    best = pos
                    bestMask = mask
                    if (count == 1) break
                }
            }
            if (best < 0) return PickResult.Solved
            return PickResult.Choose(best, bestMask)
        }
    }

    sealed interface PickResult {
        data object Solved : PickResult
        data object Dead : PickResult
        data class Choose(val pos: Int, val mask: Int) : PickResult
    }

    private class SearchOut {
        var count = 0
        var solution: Board? = null
    }

    private fun search(solver: Solver, limit: Int, out: SearchOut) {
        when (val pick = solver.pick()) {
            is PickResult.Dead -> return
            is PickResult.Solved -> {
                out.count++
                if (out.solution == null) out.solution = solver.snapshot()
                return
            }
            is PickResult.Choose -> {
                for (digit in 1..9) {
                    if ((pick.mask and (1 shl (digit - 1))) == 0) continue
                    solver.place(pick.pos, digit)
                    search(solver, limit, out)
                    solver.unplace(pick.pos)
                    if (out.count >= limit) return
                }
            }
        }
    }

    fun solve(board: Board): Board? {
        val out = SearchOut()
        search(Solver(board), 1, out)
        return out.solution
    }

    /** 统计解的个数，最多数到 limit（判唯一解只需知道是否 ≥ 2）。 */
    fun countSolutions(board: Board, limit: Int = 2): Int {
        val out = SearchOut()
        search(Solver(board), limit, out)
        return out.count
    }

    private fun fill(solver: Solver, rng: Random): Boolean {
        var pos = -1
        for (p in 0..80) {
            if (solver.get(p) == 0) {
                pos = p
                break
            }
        }
        if (pos < 0) return true
        val digits = (1..9).filter { (solver.candidates(pos) and (1 shl (it - 1))) != 0 }.shuffled(rng)
        for (digit in digits) {
            solver.place(pos, digit)
            if (fill(solver, rng)) return true
            solver.unplace(pos)
        }
        return false
    }

    fun generateSolution(rng: Random): Board {
        val solver = Solver(IntArray(81))
        fill(solver, rng)
        return solver.snapshot()
    }

    /** 出题：随机终盘 → 逐格挖洞并校验唯一解。 */
    fun generate(difficulty: Difficulty, rng: Random): Game {
        val solution = generateSolution(rng)
        val puzzle = solution.copyOf()
        val order = (0..80).toList().shuffled(rng)
        var blanks = 0
        for (pos in order) {
            if (blanks >= difficulty.targetBlanks) break
            val backup = puzzle[pos]
            puzzle[pos] = 0
            if (countSolutions(puzzle, 2) == 1) blanks++ else puzzle[pos] = backup
        }
        return Game(puzzle, puzzle.copyOf(), solution, difficulty)
    }

    /** 逐格冲突标记：同行 / 同列 / 同宫出现重复数字。 */
    fun conflictFlags(board: Board): BooleanArray {
        val flags = BooleanArray(81)
        val counts = IntArray(10)
        val groups = buildList {
            for (i in 0..8) add((0..8).map { i * 9 + it })
            for (i in 0..8) add((0..8).map { it * 9 + i })
            for (b in 0..8) {
                val r0 = b / 3 * 3
                val c0 = b % 3 * 3
                add((0..8).map { (r0 + it / 3) * 9 + c0 + it % 3 })
            }
        }
        for (group in groups) {
            counts.fill(0)
            for (pos in group) counts[board[pos]]++
            for (pos in group) if (board[pos] != 0 && counts[board[pos]] > 1) flags[pos] = true
        }
        return flags
    }

    fun isSolved(board: Board): Boolean {
        for (pos in 0..80) if (board[pos] == 0) return false
        return !conflictFlags(board).any { it }
    }

    /** 该格可填数字的位掩码（排除自身已填值）。 */
    fun legalMask(board: Board, pos: Int): Int {
        var used = 0
        for (peer in 0..80) {
            if (peer == pos) continue
            if (rowOf(peer) != rowOf(pos) && colOf(peer) != colOf(pos) && boxOf(peer) != boxOf(pos)) continue
            val value = board[peer]
            if (value != 0) used = used or (1 shl (value - 1))
        }
        return ALL_MASK and used.inv()
    }

    fun isGiven(game: Game, pos: Int) = game.puzzle[pos] != 0

    /** 时长格式化：MM:SS 或 H:MM:SS（手动拼接，避免依赖平台专属的 String.format）。 */
    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val mm = m.toString().padStart(2, '0')
        val ss = s.toString().padStart(2, '0')
        return if (h > 0) "$h:$mm:$ss" else "$mm:$ss"
    }
}
