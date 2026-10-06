package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game

/**
 * **跨对局保留**的偏好：严格模式 / 显示笔记 / 笔记模式 / 候选提示。
 *
 * 它们是"设置"而不是"对局状态"——换一局、重置、通关都不应该把它们清回默认值，
 * 因此随存档一起落盘（[SaveCodec] v2），并在新开一局时由 reducer 显式继承。
 */
data class GameSettings(
    val strictMode: Boolean = false,
    val showNotes: Boolean = true,
    val noteMode: Boolean = false,
    val hintCandidates: Boolean = false,
)

/** 存档中的一局对弈：题面 / 当前盘 / 答案由 [Game] 承载，外加笔记与已用时。 */
data class SavedGame(
    val game: Game,
    val notes: IntArray,
    val elapsed: Int,
)

/**
 * 存档文件内容：**设置总是保存**，未完成对局可选。
 *
 * 设置与对局的生命周期不同（设置长期有效、对局随时可能结算），但都不值得各开一个文件；
 * 用 `game == null` 表达"当前没有可继续的对局"，一个文件即可覆盖两种情形。
 */
data class SaveFile(
    val settings: GameSettings = GameSettings(),
    val game: SavedGame? = null,
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
    /** 读取存档；文件不存在或内容非法时返回 null。 */
    fun load(): SaveFile?

    /** 写入存档：设置总是写入，`game == null` 表示没有可继续的对局。 */
    fun save(file: SaveFile)
}

/** 默认实现：什么都不做（用于测试或不希望落盘的场景）。 */
object NoopGameStore : GameStore {
    override fun load(): SaveFile? = null
    override fun save(file: SaveFile) = Unit
}

/**
 * 存档编解码：纯文本、无第三方序列化依赖，因此可在 `commonTest` 里直接单测。
 *
 * **v2 格式**（每行 `key=value`；设置在前，对局块可选）：
 * ```
 * v=2
 * strict=1
 * showNotes=1
 * noteMode=0
 * hintCandidates=0
 * game=1                       ← 仅当有未完成对局时出现，随后 6 行一并出现
 * difficulty=Easy
 * puzzle=<81 位数字，0 表示空格>
 * current=<81 位数字>
 * solution=<81 位数字>
 * notes=<81 个十进制整数，逗号分隔>
 * elapsed=<秒>
 * ```
 *
 * **向后兼容**：v1 文件没有设置行、v2 早期文件没有 `hintCandidates` 行，[decode] 都对缺失项取默认值，
 * 并按"v1 必定带对局"解析；`v` 仍是必填字段且必须落在 `1..VERSION`，因此不会把无关文本误判成存档。
 * 读到的旧文件会在下一次落盘时自动补全并升级。
 */
object SaveCodec {
    /** 当前写入版本。 */
    const val VERSION = 2

    private const val MIN_VERSION = 1
    private const val BOARD_SIZE = 81

    fun encode(file: SaveFile): String = buildString {
        appendLine("v=$VERSION")
        appendLine("strict=${file.settings.strictMode.flag()}")
        appendLine("showNotes=${file.settings.showNotes.flag()}")
        appendLine("noteMode=${file.settings.noteMode.flag()}")
        appendLine("hintCandidates=${file.settings.hintCandidates.flag()}")

        val saved = file.game ?: return@buildString
        val game = saved.game
        appendLine("game=1")
        appendLine("difficulty=${game.difficulty.name}")
        appendLine("puzzle=${game.puzzle.toDigits()}")
        appendLine("current=${game.current.toDigits()}")
        appendLine("solution=${game.solution.toDigits()}")
        appendLine("notes=${saved.notes.joinToString(",")}")
        appendLine("elapsed=${saved.elapsed}")
    }

    fun decode(text: String): SaveFile? = runCatching {
        val fields = text.lineSequence()
            .filter { it.contains('=') }
            .associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }

        val version = fields["v"]?.toIntOrNull()
        require(version != null && version in MIN_VERSION..VERSION) { "版本不匹配" }

        val settings = GameSettings(
            strictMode = fields["strict"].toFlag(default = false),
            showNotes = fields["showNotes"].toFlag(default = true),
            noteMode = fields["noteMode"].toFlag(default = false),
            // v1 文件、以及 v2 早期文件都没有这一行 → 取默认值（关闭）
            hintCandidates = fields["hintCandidates"].toFlag(default = false),
        )
        // v1 一定带对局；v2 由 game=1 显式标记（缺失即"只有设置"）
        val hasGame = if (version == MIN_VERSION) true else fields["game"] == "1"
        SaveFile(settings = settings, game = if (hasGame) fields.toSavedGame() else null)
    }.getOrNull()

    private fun Map<String, String>.toSavedGame(): SavedGame {
        val difficulty = Difficulty.valueOf(getValue("difficulty"))
        val puzzle = getValue("puzzle").toBoard()
        val current = getValue("current").toBoard()
        val solution = getValue("solution").toBoard()
        val notes = getValue("notes").split(',').map { it.trim().toInt() }.toIntArray()
        require(notes.size == BOARD_SIZE) { "笔记数量不为 81" }
        val elapsed = getValue("elapsed").toInt()
        require(elapsed >= 0) { "计时为负" }
        return SavedGame(Game(puzzle, current, solution, difficulty), notes, elapsed)
    }

    private fun Boolean.flag(): Int = if (this) 1 else 0

    /** 宽松解析：`1/true` 为真、`0/false` 为假，其余（含缺行）取默认值。 */
    private fun String?.toFlag(default: Boolean): Boolean = when (this) {
        null -> default
        "1", "true" -> true
        "0", "false" -> false
        else -> default
    }

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
