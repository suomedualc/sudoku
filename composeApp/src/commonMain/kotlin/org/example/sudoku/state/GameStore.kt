package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game

/** 存档中的一局对弈：题面 / 当前盘 / 答案由 [Game] 承载，外加笔记与已用时。 */
data class SavedGame(
    val game: Game,
    val notes: IntArray,
    val elapsed: Int,
)

/**
 * 存档端口（不持久化的默认实现见 [NoopGameStore]）。
 *
 * 之所以做成"端口 + 注入"而不是 `expect/actual`：
 * - `state` 层保持零平台依赖，桌面 / Android / iOS 各自提供实现即可；
 * - 单元测试可注入内存实现，不需要碰文件系统；
 * - 后续接入 DataStore / SQLDelight 时只换实现，不动状态机与 UI。
 */
interface GameStore {
    fun load(): SavedGame?
    fun save(saved: SavedGame)
    fun clear()
}

/** 默认实现：什么都不做（用于测试或不希望落盘的场景）。 */
object NoopGameStore : GameStore {
    override fun load(): SavedGame? = null
    override fun save(saved: SavedGame) = Unit
    override fun clear() = Unit
}

/**
 * 存档编解码：纯文本、无第三方序列化依赖，因此可在 `commonTest` 里直接单测。
 *
 * 格式（每行 `key=value`）：
 * ```
 * v=1
 * difficulty=Easy
 * puzzle=<81 位数字，0 表示空格>
 * current=<81 位数字>
 * solution=<81 位数字>
 * notes=<81 个十进制整数，逗号分隔>
 * elapsed=<秒>
 * ```
 */
object SaveCodec {
    private const val VERSION = 1
    private const val BOARD_SIZE = 81

    fun encode(saved: SavedGame): String = buildString {
        val game = saved.game
        appendLine("v=$VERSION")
        appendLine("difficulty=${game.difficulty.name}")
        appendLine("puzzle=${game.puzzle.toDigits()}")
        appendLine("current=${game.current.toDigits()}")
        appendLine("solution=${game.solution.toDigits()}")
        appendLine("notes=${saved.notes.joinToString(",")}")
        appendLine("elapsed=${saved.elapsed}")
    }

    fun decode(text: String): SavedGame? = runCatching {
        val fields = text.lineSequence()
            .filter { it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
        require(fields["v"]?.toIntOrNull() == VERSION) { "版本不匹配" }
        val difficulty = Difficulty.valueOf(fields.getValue("difficulty"))
        val puzzle = fields.getValue("puzzle").toBoard()
        val current = fields.getValue("current").toBoard()
        val solution = fields.getValue("solution").toBoard()
        val notes = fields.getValue("notes").split(',').map { it.trim().toInt() }.toIntArray()
        require(notes.size == BOARD_SIZE) { "笔记数量不为 81" }
        val elapsed = fields.getValue("elapsed").toInt()
        require(elapsed >= 0) { "计时为负" }
        SavedGame(Game(puzzle, current, solution, difficulty), notes, elapsed)
    }.getOrNull()

    private fun IntArray.toDigits(): String = joinToString("") { it.coerceIn(0, 9).toString() }

    private fun String.toBoard(): IntArray {
        require(length == BOARD_SIZE) { "盘面长度不为 81" }
        return IntArray(BOARD_SIZE) { i ->
            val ch = this[i]
            require(ch in '0'..'9') { "非法字符" }
            ch - '0'
        }
    }
}
