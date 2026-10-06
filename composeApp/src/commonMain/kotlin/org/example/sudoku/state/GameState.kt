package org.example.sudoku.state

import org.example.sudoku.core.Game

/**
 * 页面导航：首页（含难度选择与退出确认）与对局页。
 *
 * 统计 / 记录页已随"首页只保留三个入口"的改版移除（规划见 `docs/05-发展规划.md` M3）。
 */
enum class Screen { Menu, Game }

/**
 * 撤销 / 重做快照：对局中会被玩家操作改动的全部字段。
 *
 * 其中 `hintsCount` 必须一起快照——否则撤销一次提示后 `hintUsed` 回退而计数不回退，两个标记会互相矛盾。
 */
data class Snapshot(
    val current: IntArray,
    val notes: IntArray,
    val hintUsed: Boolean,
    val hintsCount: Int,
    val revealed: Boolean,
)

/**
 * 全局状态：只描述"游戏此刻是什么样子"，不含任何 UI 细节。
 * 所有变更都必须经由 `GameReducer.reduce`，保证可预测、可回放、可单测。
 *
 * 注意：`notes` 与 `Game` 内部使用 `IntArray`，`data class` 生成的 `equals` 对数组是**引用比较**，
 * 因此不要依赖 `state == other` 做业务判断（Compose 依赖的是实例变化触发重组）。
 */
data class GameState(
    val screen: Screen = Screen.Menu,
    val game: Game? = null,
    val selected: Int? = null,
    /** 每格笔记位掩码。 */
    val notes: IntArray = IntArray(81),
    val noteMode: Boolean = false,
    val strictMode: Boolean = false,
    val showNotes: Boolean = true,
    val elapsed: Int = 0,
    val paused: Boolean = false,
    val hintUsed: Boolean = false,
    val hintsCount: Int = 0,
    val revealed: Boolean = false,
    val settled: Boolean = false,
    val won: Boolean = false,
    val undoStack: List<Snapshot> = emptyList(),
    val redoStack: List<Snapshot> = emptyList(),
) {
    /** 是否接受棋盘输入。 */
    val interactive: Boolean
        get() = screen == Screen.Game && game != null && !paused && !settled && !won

    /**
     * 三项跨对局保留的偏好（`strictMode` / `showNotes` / `noteMode`）。
     * 换局、重置、通关都不清空，并由 [GameViewModel] 随存档落盘。
     */
    val settings: GameSettings
        get() = GameSettings(strictMode = strictMode, showNotes = showNotes, noteMode = noteMode)
}
