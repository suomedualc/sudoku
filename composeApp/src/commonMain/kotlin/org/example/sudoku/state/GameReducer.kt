package org.example.sudoku.state

import org.example.sudoku.core.CellCursor
import org.example.sudoku.core.Game
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
        // 新开一局：对局字段全部归零，但**偏好跨对局保留**（否则玩家每局都要重设开关）
        is GameAction.NewGame -> Reduction(
            GameState(
                screen = Screen.Game,
                game = Sudoku.generate(action.difficulty, rng),
                strictMode = state.strictMode,
                showNotes = state.showNotes,
                noteMode = state.noteMode,
                hintCandidates = state.hintCandidates,
                darkMode = state.darkMode,
                keyMap = state.keyMap,
            ),
        )

        is GameAction.Select ->
            Reduction(if (!state.interactive) state else state.copy(selected = action.pos))

        // 开局 / 续局时没有选中格 → 光标落在**第一个空格**（读序），
        // 键盘玩家不用先按一次方向键才能开始填。由 UI 在"进入对局 / 换了一局"时派发。
        GameAction.FocusFirstEmpty -> focusFirstEmpty(state)

        // 取消选格：只清"选中"这一项，棋盘上的临时高亮都从它派生，因此一并消失。
        // 盘面 / 笔记 / 冲突 / 计时一律不动——要清盘面另有 Reset。
        GameAction.Deselect -> Reduction(state.copy(selected = null))

        is GameAction.Digit -> place(state, action.value, action.advance)
        is GameAction.Move -> move(state, action.dRow, action.dCol, action.jumpToBlank)
        // 键位：只改 keyMap，不动选中格也不动盘面；冲突（一键兼两职）由 KeyMap.bind 处理
        is GameAction.BindKey -> Reduction(state.copy(keyMap = state.keyMap.bind(action.action, action.token)))

        GameAction.ToggleNoteMode -> Reduction(state.copy(noteMode = !state.noteMode))
        is GameAction.ToggleStrict -> Reduction(state.copy(strictMode = action.enabled))
        GameAction.ToggleShowNotes -> Reduction(state.copy(showNotes = !state.showNotes))
        GameAction.ToggleHintCandidates -> toggleHintCandidates(state)
        // 夜墨只换配色，不动任何对局字段（进棋盘前的首页也能切，所以不要求 interactive）
        GameAction.ToggleDarkMode -> Reduction(
            state.copy(darkMode = !state.darkMode),
            if (!state.darkMode) "夜墨模式：开" else "夜墨模式：关",
        )

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

    private fun focusFirstEmpty(state: GameState): Reduction {
        if (state.selected != null) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        val first = CellCursor.firstEmpty(game.current) ?: return Reduction(state)
        return Reduction(state.copy(selected = first))
    }

    private fun place(state: GameState, value: Int, advance: Boolean): Reduction {
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

        // 只有"真的把一个数字写进格子"才算填字：擦除、笔记、以及"再按一次同一个数字取消"
        // 都不该触发跳转（跳了就等于把玩家从他正在改的那一格上赶走）。
        var filled = false
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
                filled = true
            }
        }

        val message = if (value != 0 && !state.noteMode && Sudoku.conflictFlags(current)[pos]) {
            "数字 $value 与所在行 / 列 / 宫冲突"
        } else {
            null
        }

        // 自动跳转：填完即走，键盘玩家不必每格都按一次方向键。
        // 盘面已满时 nextAfterFill 返回 null → 停在原地（通常意味着刚通关）。
        val landed = if (advance && filled) {
            CellCursor.nextAfterFill(current, pos) ?: pos
        } else {
            base.selected
        }

        val next = base.copy(
            game = game.copy(current = current),
            notes = notes,
            selected = landed,
        )
        return settle(next, message)
    }

    /**
     * 方向键选格：沿方向在本行 / 本列内环绕，**停在第一个空格**；
     * 该行 / 列没有空格时退到"可编辑格"，题面格一律跳过（细节见 [CellCursor.nextInDirection]）。
     *
     * 没有选中格时（开局、或刚点了棋盘外的空白清掉选中）**从第一个空格起算**——
     * 否则方向键按下去没有任何反应，玩家会以为键盘坏了。
     *
     * [jumpToBlank]（Shift + 方向键）跳到方向上的**下一个空格**；默认**逐格**，
     * 可以停在玩家自己填过的格子上——"填完回去改 / 擦"因此不需要任何修饰键。
     */
    private fun move(state: GameState, dRow: Int, dCol: Int, jumpToBlank: Boolean): Reduction {
        if (!state.interactive) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        val from = state.selected ?: CellCursor.firstEmpty(game.current) ?: return Reduction(state)
        return Reduction(
            state.copy(
                selected = CellCursor.nextInDirection(
                    game = game,
                    from = from,
                    dRow = dRow,
                    dCol = dCol,
                    jumpToBlank = jumpToBlank,
                ),
            ),
        )
    }

    /**
     * 候选提示开关。效果直接体现在**所有空格的候选数字**上，因此不做任何选中格改动——
     * 设置类开关不该顺手移动玩家选中的格子。
     */
    private fun toggleHintCandidates(state: GameState): Reduction {
        val enabled = !state.hintCandidates
        return Reduction(
            state.copy(hintCandidates = enabled),
            if (enabled) "候选提示：开（空格显示可填数字）" else "候选提示：关",
        )
    }

    /** 第一个空格（没有则返回 null）。提示与候选提示共用，保证"选中格优先"的语义一致。 */
    private fun firstEmpty(game: Game): Int? = CellCursor.firstEmpty(game.current)

    private fun hint(state: GameState): Reduction {
        if (!state.interactive) return Reduction(state)
        val game = state.game ?: return Reduction(state)
        val target = state.selected?.takeIf { game.current[it] == 0 } ?: firstEmpty(game)
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
            // 与 pushUndo 同样截断：只截一边的话，来回 undo/redo 会让栈无限增长
            undoStack = (state.undoStack + snapshotOf(state)).takeLast(UNDO_LIMIT),
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
