package org.example.sudoku.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.example.sudoku.core.Sudoku
import kotlin.random.Random

/**
 * 状态容器：UI 与纯状态机之间唯一的桥。
 *
 * 职责：
 * 1. 持有 `GameState`，把 `GameAction` 交给 `GameReducer`；
 * 2. 与 [GameStore] 打交道——启动时尝试恢复未完成对局，改动后落盘、结算后清档。
 *
 * 计时由 UI 侧心跳派发 `Tick` 驱动，因此本类不持有定时器。
 *
 * @param seed 固定种子用于测试与回放；生产传 null 使用默认随机源。
 * @param store 存档端口；默认 [NoopGameStore]（不落盘，便于测试）。
 */
class GameViewModel(
    seed: Long? = null,
    private val store: GameStore = NoopGameStore,
) {
    private val rng = seed?.let(::Random) ?: Random.Default

    var state: GameState by mutableStateOf(store.load()?.toState() ?: GameState())
        private set

    fun dispatch(action: GameAction) {
        state = GameReducer.reduce(state, action, rng)
        // Tick 每秒一次：每 10 秒落一次盘即可，避免每秒写文件
        val shouldPersist = action !is GameAction.Tick || state.elapsed % 10 == 0
        if (shouldPersist) persist()
    }

    /** 派生状态：逐格冲突标记。 */
    val conflicts: BooleanArray
        get() = state.game?.let { Sudoku.conflictFlags(it.current) } ?: BooleanArray(81)

    /** 派生状态：已完成格数 / 总空格数。 */
    val progress: Pair<Int, Int>
        get() {
            val game = state.game ?: return 0 to 0
            val total = game.puzzle.count { it == 0 }
            val done = total - game.current.count { it == 0 }
            return done to total
        }

    /** 首页「继续游戏」的可用性依据：有未完成且未看答案的对局。 */
    val canResume: Boolean
        get() = state.game != null && !state.won && !state.revealed && !state.settled

    private fun persist() {
        val game = state.game
        if (game == null || state.won || state.settled || state.revealed) {
            store.clear()
        } else {
            store.save(SavedGame(game, state.notes, state.elapsed))
        }
    }
}

/** 存档 → 初始状态：恢复盘面 / 笔记 / 计时，但仍然停在首页（由玩家点「继续游戏」进入）。 */
private fun SavedGame.toState(): GameState = GameState(
    screen = Screen.Menu,
    game = game,
    notes = notes,
    elapsed = elapsed,
)
