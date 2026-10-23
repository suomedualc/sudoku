package org.example.sudoku.state

import org.example.sudoku.core.Difficulty
import org.example.sudoku.core.Game

/**
 * **跨对局保留**的偏好：严格模式 / 显示笔记 / 笔记模式 / 候选提示 / 夜墨模式。
 *
 * 它们是"设置"而不是"对局状态"——换一局、重置、通关都不应该把它们清回默认值，
 * 因此随存档一起落盘（[SaveCodec] v2），并在新开一局时由 reducer 显式继承。
 */
data class GameSettings(
    val strictMode: Boolean = false,
    val showNotes: Boolean = true,
    val noteMode: Boolean = false,
    val hintCandidates: Boolean = false,
    val darkMode: Boolean = false,
    /** 键位：五项令牌逗号分隔（见 [KeyMap.encode]）。 */
    val keyMap: KeyMap = KeyMap(),
    /** 界面语言。 */
    val language: AppLanguage = AppLanguage.ZhCn,
)

/** 存档中的一局对弈：题面 / 当前盘 / 答案由 [Game] 承载，外加笔记、已用时与本局提示标记。 */
data class SavedGame(
    val game: Game,
    val notes: IntArray,
    val elapsed: Int,
    /** 本局是否用过提示（通关提示条"用了提示"依赖它；续局后不该归零）。 */
    val hintUsed: Boolean = false,
    /** 本局提示次数（顶栏"提示 ×N"与通关抽屉依赖它；续局后不该归零）。 */
    val hintsCount: Int = 0,
)

/**
 * 存档文件内容：**设置总是保存**，未完成对局可选。
 *
 * 设置与对局的生命周期不同（设置长期有效、对局随时可能结算），但都不值得各开一个文件；
 * 用 `game == null` 表达"当前没有可继续的对局"，一个文件即可覆盖两种情形。
 */
data class SaveFile(
    val settings: GameSettings = GameSettings(),
    /** 终身游玩统计（跨对局累计，随本文件落盘；与偏好分开——它不是"偏好"而是"履历"）。 */
    val stats: GameStats = GameStats(),
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

    /**
     * 本实例**是否能真正写盘**（默认 true）。
     *
     * 只有 [MutexGameStore] 在抢锁失败时返回 false。UI 用它决定是否提示玩家"本次只读"——
     * 没有这条通路的话，只读降级就是**完全静默**的：玩家会玩完整局，然后发现进度一条没存，
     * 而全程没有任何异常或提示。
     */
    val writable: Boolean get() = true
}

/** 默认实现：什么都不做（用于测试或不希望落盘的场景）。 */
object NoopGameStore : GameStore {
    override fun load(): SaveFile? = null
    override fun save(file: SaveFile) = Unit
}

/**
 * 存档写锁端口（**纯契约**，实现放平台层）：多实例场景下，同一份存档只允许一个实例持有写锁。
 *
 * 做成端口而不是直接在 [GameStore] 里加锁：锁是 IO / 平台能力（文件锁、进程锁），
 * 而 `state/` 保持零平台依赖；测试可用内存实现精确模拟"抢到 / 抢不到"。
 */
interface SaveMutex {
    /** 尝试获取排他写锁并**保持到 [release]**；失败（已被其他实例持有）返回 false。 */
    fun acquire(): Boolean

    /** 释放写锁。 */
    fun release()
}

/**
 * 带互斥的存档装饰器：启动时抢写锁，抢不到则降级为**只读**——
 * [load] 照常（让"第二实例"仍能看盘面），[save] 静默跳过（不去覆盖主实例的数据）。
 *
 * 这是"多实例不并发写同一存档"的唯一守门点：真正的文件锁在平台层的 `SaveMutex` 实现里，
 * 这里只表达"抢不到就不写"的策略，因此可单测、可回放。
 */
class MutexGameStore(
    private val delegate: GameStore,
    private val mutex: SaveMutex,
) : GameStore {
    private val acquired: Boolean = mutex.acquire()

    /** 本实例是否持有写锁（false = 只读）。 */
    override val writable: Boolean get() = acquired

    override fun load(): SaveFile? = delegate.load()

    override fun save(file: SaveFile) {
        if (acquired) delegate.save(file)
    }
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
 * darkMode=0
 * keyMap=DirectionUp,DirectionDown,DirectionLeft,DirectionRight,0   ← 上/下/左/右/擦除 五个键位令牌
 * language=ZhCn                ← 界面语言
 * stats=3,1,2450,180,2,2,2,120,0,0,0   ← 统计（逗号分隔的 11 个数，见 GameStats.encode）
 * game=1                       ← 仅当有未完成对局时出现，随后 8 行一并出现
 * difficulty=Easy
 * puzzle=<81 位数字，0 表示空格>
 * current=<81 位数字>
 * solution=<81 位数字>
 * notes=<81 个十进制整数，逗号分隔>
 * elapsed=<秒>
 * hintUsed=0|1                 ← 本局是否用过提示
 * hintsCount=<次>              ← 本局提示次数（旧存档缺行取 0）
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
        appendLine("darkMode=${file.settings.darkMode.flag()}")
        appendLine("keyMap=${file.settings.keyMap.encode()}")
        appendLine("language=${file.settings.language.name}")
        appendLine("stats=${file.stats.encode()}")

        val saved = file.game ?: return@buildString
        val game = saved.game
        appendLine("game=1")
        appendLine("difficulty=${game.difficulty.name}")
        appendLine("puzzle=${game.puzzle.toDigits()}")
        appendLine("current=${game.current.toDigits()}")
        appendLine("solution=${game.solution.toDigits()}")
        appendLine("notes=${saved.notes.joinToString(",")}")
        appendLine("elapsed=${saved.elapsed}")
        appendLine("hintUsed=${saved.hintUsed.flag()}")
        appendLine("hintsCount=${saved.hintsCount}")
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
            // 同上：缺少 darkMode 行的旧存档按"浅色纸面"读，符合升级前的观感
            darkMode = fields["darkMode"].toFlag(default = false),
            // 同上：缺少 keyMap 行（旧存档）→ 取默认键位（方向键 + 0 擦除）
            keyMap = KeyMap.decode(fields["keyMap"]),
            language = AppLanguage.decode(fields["language"]),
        )
        // v1 一定带对局；v2 由 game=1 显式标记（缺失即"只有设置"）
        val hasGame = if (version == MIN_VERSION) true else fields["game"] == "1"
        SaveFile(
            settings = settings,
            // 缺少 stats 行（旧存档）→ 全 0：统计从这一刻起重新累计
            stats = GameStats.decode(fields["stats"]),
            game = if (hasGame) fields.toSavedGame() else null,
        )
    }.getOrNull()

    private fun Map<String, String>.toSavedGame(): SavedGame {
        // 难度只是个标签：解析不出来就退回 Normal，别让整局作废。
        // 与 [String?.toFlag] 的"宽松取默认"是同一条策略——存档是外部输入，不该因为一个字段废掉全部。
        val difficulty = runCatching { Difficulty.valueOf(getValue("difficulty")) }
            .getOrDefault(Difficulty.Normal)
        val puzzle = getValue("puzzle").toBoard()
        val current = getValue("current").toBoard()
        val solution = getValue("solution").toBoard()
        val notes = getValue("notes").split(',').map { it.trim().toInt() }.toIntArray()
        require(notes.size == BOARD_SIZE) { "笔记数量不为 81" }
        val elapsed = getValue("elapsed").toInt()
        require(elapsed >= 0) { "计时为负" }
        // 旧存档没有这两行 → 取默认（false / 0）：与旧行为一致，不因缺行而作废
        val hintUsed = this["hintUsed"].toFlag(default = false)
        val hintsCount = this["hintsCount"]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
        return SavedGame(Game(puzzle, current, solution, difficulty), notes, elapsed, hintUsed, hintsCount)
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
