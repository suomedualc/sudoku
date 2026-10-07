package org.example.sudoku.state

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import org.example.sudoku.core.Sudoku
import kotlin.random.Random
import kotlin.time.TimeSource

/**
 * 状态容器：UI 与纯状态机之间唯一的桥。
 *
 * 职责：
 * 1. 持有 [GameState]，把 [GameAction] 交给 [GameReducer]；
 * 2. 把 reducer 返回的一次性提示送进**事件通道**（不再塞进状态）；
 * 3. 用**真实时间源**驱动计时（[syncClock]），并负责暂停/离开界面时的"并账"；
 * 4. 与 [GameStore] 打交道：启动恢复、改动落盘、结算清档；
 * 5. 缓存派生状态（冲突 / 进度），避免每秒心跳触发重组时重算。
 *
 * @param seed 固定种子用于测试与回放；生产传 null 使用默认随机源。
 * @param store 存档端口；默认 [NoopGameStore]（不落盘，便于测试）。
 * @param clock 单调时钟（毫秒）。测试可注入假时钟，从而**不依赖 sleep** 验证计时逻辑。
 */
class GameViewModel(
    seed: Long? = null,
    private val store: GameStore = NoopGameStore,
    private val clock: () -> Long = monotonicClock(),
) {
    private val rng = seed?.let(::Random) ?: Random.Default
    private val restored: SaveFile? = store.load()

    var state: GameState by mutableStateOf(restored?.toState() ?: GameState())
        private set

    private val messageChannel = Channel<String>(Channel.BUFFERED)

    /** 一次性提示流：UI 侧逐条 collect 后弹 Snackbar（相同文案也会逐条送达）。 */
    val messages: ReceiveChannel<String> get() = messageChannel

    /** 计时：锚点 + 累计值，`state.elapsed` 始终由真实时间推导，不再"每秒 +1"。 */
    private var anchorMs: Long? = null
    private var accumulatedSeconds: Int = restored?.game?.elapsed ?: 0

    fun dispatch(action: GameAction) {
        val reduction = GameReducer.reduce(state, action, rng)
        state = reduction.state
        reduction.message?.let { messageChannel.trySend(it) }

        if (action is GameAction.NewGame || action is GameAction.Reset) {
            accumulatedSeconds = 0
            anchorMs = null
        }
        syncAnchor()

        // 只有"改动了可持久数据"的动作才落盘：Select / Move 只改当前选中格（存档里根本没有这一项），
        // 为它们写盘是纯浪费——否则每移动一次光标都要整文件重写。心跳由 syncClock 单独节流。
        if (persistsToDisk(action)) persist()
    }

    /** 存档里只有 settings + game/notes/elapsed，因此"只改选中格"与"只报时"的动作都不必写盘。 */
    private fun persistsToDisk(action: GameAction): Boolean =
        action !is GameAction.Select &&
            action !is GameAction.Move &&
            action !is GameAction.SyncElapsed

    /**
     * 由 UI 心跳（约 250ms 一次）调用：按真实时间推进计时。
     * 只在整秒发生变化时才写状态，避免无谓重组。
     */
    fun syncClock() {
        syncAnchor()
        val seconds = accumulatedSeconds + secondsSince(anchorMs)
        if (seconds != state.elapsed) {
            state = GameReducer.reduce(state, GameAction.SyncElapsed(seconds), rng).state
            if (seconds % PERSIST_EVERY_SECONDS == 0) persist()
        }
    }

    /** 派生状态：逐格冲突标记（`derivedStateOf` 缓存，只在盘面真正变化时重算）。 */
    val conflicts: BooleanArray by derivedStateOf {
        state.game?.let { Sudoku.conflictFlags(it.current) } ?: EMPTY_FLAGS
    }

    /** 派生状态：已完成格数 / 总空格数。 */
    val progress: Pair<Int, Int> by derivedStateOf {
        val game = state.game ?: return@derivedStateOf 0 to 0
        val total = game.puzzle.count { it == 0 }
        val done = total - game.current.count { it == 0 }
        done to total
    }

    /** 首页「继续游戏」的可用性依据：有未完成且未看答案的对局。 */
    val canResume: Boolean
        get() = state.game != null && !state.won && !state.revealed && !state.settled

    // ---------- 内部实现 ----------

    /**
     * 根据当前状态决定是否走表：
     * - 开始走表 → 记下锚点；
     * - 停表（暂停 / 结算 / 离开对局界面）→ 把"锚点到现在"的时间并进累计值。
     */
    private fun syncAnchor() {
        val running = state.screen == Screen.Game && state.game != null && !state.paused && !state.settled
        val now = clock()
        val anchor = anchorMs
        when {
            running && anchor == null -> anchorMs = now
            !running && anchor != null -> {
                accumulatedSeconds += secondsSince(anchor)
                anchorMs = null
            }
        }
    }

    private fun secondsSince(anchorMs: Long?): Int =
        anchorMs?.let { ((clock() - it) / 1000).toInt().coerceAtLeast(0) } ?: 0

    /**
     * 落盘：**设置总是写入**，对局只在"可继续"时写入。
     *
     * 结算（通关 / 看答案）后不再保留对局，但玩家选好的偏好会留着——
     * 这正是把两者放进同一个 [SaveFile] 的原因。
     */
    private fun persist() {
        val game = state.game
        val resumable = game != null && !state.won && !state.settled && !state.revealed
        store.save(
            SaveFile(
                settings = state.settings,
                game = if (resumable) SavedGame(game, state.notes, state.elapsed) else null,
            ),
        )
    }

    private companion object {
        /** 每 10 秒落一次盘即可，避免每秒写文件。 */
        const val PERSIST_EVERY_SECONDS = 10
        val EMPTY_FLAGS = BooleanArray(81)
    }
}

/** 单调时钟工厂：返回"自创建时刻起毫秒数"的时钟（跨平台，不受系统时间调整影响）。 */
private fun monotonicClock(): () -> Long {
    val start = TimeSource.Monotonic.markNow()
    return { start.elapsedNow().inWholeMilliseconds }
}

/**
 * 存档 → 初始状态：恢复盘面 / 笔记 / 计时 / 设置，但仍然停在首页（由玩家点「继续游戏」进入）。
 * 只有设置、没有对局时也能正常启动（`game == null` ⇒ 首页「继续游戏」置灰）。
 */
private fun SaveFile.toState(): GameState = GameState(
    screen = Screen.Menu,
    game = game?.game,
    notes = game?.notes ?: IntArray(81),
    elapsed = game?.elapsed ?: 0,
    strictMode = settings.strictMode,
    showNotes = settings.showNotes,
    noteMode = settings.noteMode,
    hintCandidates = settings.hintCandidates,
)
