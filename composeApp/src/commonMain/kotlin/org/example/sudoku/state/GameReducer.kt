package org.example.sudoku.state

import org.example.sudoku.core.Sudoku
import kotlin.random.Random

/**
 * `reduce` 的产物：新状态 + 一次性提示。
 *
 * 提示本质是**事件**而非状态，放在状态里会带来两类问题：
 * ① 相同文案被等值比较"吞掉"（第二次相同的冲突提示不弹）；
 * ② 消息在 Snackbar 存活期间一直挂在状态上，语义含混。
 * 因此改为随 reduce 的返回值交给 [GameViewModel]，再经事件通道送到 UI。
 */
data class Reduction(
    val state: GameState,
    val message: String? = null,
)

/**
 * 纯状态机：`reduce(state, action) -> Reduction`。
 *
 * 不碰 UI、不做 IO、不读时钟——随机源由参数注入，计时值也由调用方算好传入
 * （见 [GameAction.SyncElapsed]），因此完全可单测、可回放。
 */
object GameReducer {
    private const val UNDO_LIMIT = 300

    fun reduce(state: GameState, action: GameAction, rng: Random): Reduction = when (action) {
        is GameAction.NewGame -> Reduction(
            GameState(screen = Screen.Game, game = Sudoku.generate(action.difficulty, rng)),
        )

        is GameAction.Select ->
            Reduction(if (!state.interactive) state else state.copy(selected = action.pos))

        is GameAction.Digit -> place(state, action.value)
        is GameAction.Move -> move(state, action.dRow, action.dCol)

        GameAction.ToggleNoteMode -> Reduction(state.copy(noteMode = !state.noteMode))
        is GameAction.ToggleStrict -> Reduction(state.copy(strictMode = action.enabled))
        GameAction.ToggleShowNotes -> Reduction(state.copy(showNotes = !state.showNotes))

        GameAction.Hint -> hint(state)
        GameAction.Reveal -> reveal(state)
        GameAction.Reset -> reset(state)

        GameAction.Undo -> undo(state)
        GameAction.Redo -> redo(state)

        GameAction.Pause ->
            Reduction(if (state.screen == Screen.Game && !state.paused) state.copy(paused = true) else state)

        GameAction.Resume -> Reduction(state.copy(paused = false))

        is GameAction.SyncElapsed -> syncElapsed(state, action.seconds)

        is GameAction.Navigate -> Reduction(state.copy(screen = action.screen))
    }

    // ---------- 内部实现 ----------

    private fun place(state: GameState, value: Int): Reduction {
        if (!state.interactive) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        val pos = state.selected ?: return Reduction(state, "请先选择一个格子")

        if (Sudoku.isGiven(game, pos)) return Reduction(state, "题目给定的数字不可修改")

        val notes = state.notes.copyOf()
        val current = game.current.copyOf()

        // 无变化的输入不写入历史：否则 Undo 会表现为"点了没反应"
        if (value == 0 && current[pos] == 0 && notes[pos] == 0) return Reduction(state)

        // 笔记模式只对空格生效
        if (state.noteMode && value != 0 && current[pos] != 0) {
            return Reduction(state, "该格已填入数字，无法记笔记")
        }

        if (value != 0 && state.strictMode) {
            val allowed = Sudoku.legalMask(current, pos)
            if ((allowed and (1 shl (value - 1))) == 0) {
                return Reduction(state, "严格模式：该数字与规则冲突")
            }
        }

        val base = pushUndo(state)

        when {
            value == 0 -> {
                current[pos] = 0
                notes[pos] = 0
            }
            state.noteMode -> notes[pos] = notes[pos] xor (1 shl (value - 1))
            current[pos] == value -> {
                current[pos] = 0
                notes[pos] = 0
            }
            else -> {
                current[pos] = value
                notes[pos] = 0
                stripPeerNotes(notes, pos, value)
            }
        }

        val message = if (value != 0 && !state.noteMode && Sudoku.conflictFlags(current)[pos]) {
            "数字 $value 与所在行 / 列 / 宫冲突"
        } else {
            null
        }

        val next = base.copy(
            game = game.copy(current = current),
            notes = notes,
        )
        return settle(next, message)
    }

    /** 方向键选格：沿线逐格前进并**跳过题目给定格**（与设计规范一致）。 */
    private fun move(state: GameState, dRow: Int, dCol: Int): Reduction {
        if (!state.interactive) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        val from = state.selected ?: return Reduction(state)

        var pos = from
        var steps = 0
        do {
            val row = (Sudoku.rowOf(pos) + dRow + 9) % 9
            val col = (Sudoku.colOf(pos) + dCol + 9) % 9
            pos = row * 9 + col
            steps++
            // 一直跳到非给定格，或绕行一圈回到起点（极端情况下整行都是给定格）
        } while (steps < 81 && Sudoku.isGiven(game, pos))

        return Reduction(state.copy(selected = pos))
    }

    private fun hint(state: GameState): Reduction {
        if (!state.interactive) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        val target = state.selected?.takeIf { game.current[it] == 0 }
            ?: (0..80).firstOrNull { game.current[it] == 0 }
        if (target == null) return Reduction(state, "已无可提示的空格")

        val notes = state.notes.copyOf()
        val current = game.current.copyOf()
        current[target] = game.solution[target]
        notes[target] = 0
        stripPeerNotes(notes, target, current[target])

        val next = pushUndo(state).copy(
            game = game.copy(current = current),
            notes = notes,
            hintUsed = true,
            hintsCount = state.hintsCount + 1,
            selected = target,
        )
        return settle(next, "已填入正确答案")
    }

    private fun reveal(state: GameState): Reduction {
        if (!state.interactive) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        return Reduction(
            pushUndo(state).copy(
                game = game.copy(current = game.solution.copyOf()),
                notes = IntArray(81),
                revealed = true,
                settled = true,
            ),
            "已显示答案，本局不计入胜场",
        )
    }

    private fun reset(state: GameState): Reduction {
        val game = state.game ?: return Reduction(state)
        return Reduction(
            pushUndo(state).copy(
                game = game.copy(current = game.puzzle.copyOf()),
                notes = IntArray(81),
                elapsed = 0,
                won = false,
                settled = false,
                revealed = false,
                paused = false,
                hintUsed = false,
                hintsCount = 0,
            ),
            "本局已重置",
        )
    }

    private fun undo(state: GameState): Reduction {
        val game = state.game ?: return Reduction(state)
        val last = state.undoStack.lastOrNull() ?: return Reduction(state, "没有可撤销的步骤")
        return Reduction(
            state.copy(
                game = game.copy(current = last.current.copyOf()),
                notes = last.notes.copyOf(),
                hintUsed = last.hintUsed,
                hintsCount = last.hintsCount,
                revealed = last.revealed,
                undoStack = state.undoStack.dropLast(1),
                redoStack = state.redoStack + snapshotOf(state),
                won = false,
                settled = false,
            ),
        )
    }

    private fun redo(state: GameState): Reduction {
        val game = state.game ?: return Reduction(state)
        val last = state.redoStack.lastOrNull() ?: return Reduction(state, "没有可重做的步骤")
        val next = state.copy(
            game = game.copy(current = last.current.copyOf()),
            notes = last.notes.copyOf(),
            hintUsed = last.hintUsed,
            hintsCount = last.hintsCount,
            revealed = last.revealed,
            // 重做后重新推导"输入是否被锁住"：重做一次 Reveal 不应留下
            // revealed=true 却 settled=false 的僵尸状态（棋盘已填满却还能继续操作）。
            settled = last.revealed,
            redoStack = state.redoStack.dropLast(1),
            undoStack = state.undoStack + snapshotOf(state),
        )
        return settle(next, null)
    }

    /**
     * 计时同步：由 [GameViewModel] 用真实时间源算出秒数后写回。
     * 只在"对局中且未结算"时接受新值；暂停 / 离开对局界面时 UI 会传入同一个值，因此不会变动。
     */
    private fun syncElapsed(state: GameState, seconds: Int): Reduction {
        if (state.screen != Screen.Game || state.settled) return Reduction(state)
        val value = seconds.coerceAtLeast(0)
        if (value == state.elapsed) return Reduction(state)
        return Reduction(state.copy(elapsed = value))
    }

    // ---------- 工具 ----------

    private fun snapshotOf(state: GameState): Snapshot =
        Snapshot(
            current = state.game?.current?.copyOf() ?: IntArray(81),
            notes = state.notes.copyOf(),
            hintUsed = state.hintUsed,
            hintsCount = state.hintsCount,
            revealed = state.revealed,
        )

    private fun pushUndo(state: GameState): GameState {
        val stack = (state.undoStack + snapshotOf(state)).takeLast(UNDO_LIMIT)
        return state.copy(undoStack = stack, redoStack = emptyList())
    }

    private fun stripPeerNotes(notes: IntArray, pos: Int, value: Int) {
        val bit = (1 shl (value - 1)).inv()
        for (peer in 0..80) {
            if (peer == pos) continue
            val sameGroup = Sudoku.rowOf(peer) == Sudoku.rowOf(pos) ||
                Sudoku.colOf(peer) == Sudoku.colOf(pos) ||
                Sudoku.boxOf(peer) == Sudoku.boxOf(pos)
            if (sameGroup) notes[peer] = notes[peer] and bit
        }
    }

    /**
     * 判胜（方案乙：填满无冲突 **且** 与标准答案一致）。
     * 通关时把"恭喜"文案与状态一起返回；[message] 是本次动作原本要提示的内容（如冲突提示）。
     */
    private fun settle(state: GameState, message: String?): Reduction {
        val game = state.game ?: return Reduction(state, message)
        val solved = Sudoku.isSolved(game.current) && game.current.contentEquals(game.solution)
        if (!solved || state.settled || state.revealed) return Reduction(state, message)
        val suffix = if (state.hintUsed) "（使用过提示）" else ""
        return Reduction(
            state.copy(
                settled = true,
                won = true,
            ),
            "恭喜通关$suffix！用时 ${Sudoku.formatDuration(state.elapsed)}",
        )
    }
}
