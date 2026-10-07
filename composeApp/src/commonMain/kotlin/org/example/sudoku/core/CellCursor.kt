package org.example.sudoku.core

/**
 * 棋盘光标的纯逻辑：初始位置、方向键导航、填字后的自动跳转。
 *
 * 与 [Sudoku]、[GameReducer] 同一条纪律：只看盘面、不碰 UI、不做 IO，
 * 因此可单测、可回放（见 `CellCursorTest`）。
 *
 * ## 为什么"自动跳转"选九宫格优先（而不是横向优先）
 *
 * 两种策略都比"填完不动"强，但落点不同：
 *
 * | | 横向优先（读序） | **九宫格优先**（本方案） |
 * |---|---|---|
 * | 宫内空格按"从上到下"排列时 | 逐格落在宫内 | **与横向优先落点完全相同** |
 * | 宫里还有"读序更靠前"的空格 | 离开本宫，玩家得回头补 | **回头把本宫填完** |
 * | 本宫已填满 | 走读序 | 退回读序（同上） |
 *
 * 三个理由选九宫格优先：
 *
 * 1. **数独的最小推导单元是宫**——填完一格后，玩家下一步最常想的是
 *    "这个宫还差哪几个"，光标留在宫里省掉的导航最多；
 * 2. 在最常见的情形（宫内空格按读序排列）下，它与横向优先**逐格相同**，
 *    因此不会让习惯横向推进的玩家吃亏——差异只出现在"宫里还有更靠前的空格"，
 *    而那时回头补完正是玩家本来要做的事；
 * 3. **方向键与策略解耦**：按行 / 按列扫的玩家仍可用 ← → ↑ ↓ 精确控制
 *    （见 [nextInDirection]，它严格按方向走，不受跳转策略影响）。
 *    两条路径互不干扰，是本方案能同时服务两种思路的关键。
 */
object CellCursor {

    /** 第一个空格（读序：从左到右、从上到下）；盘面已满则返回 null。 */
    fun firstEmpty(board: Board): Int? = (0..80).firstOrNull { board[it] == 0 }

    /**
     * 方向键导航：沿 [dRow] / [dCol] 在**同一行 / 同一列内环绕**扫描，**停在第一个空格**。
     *
     * 三条规则，每一条都对应一个具体的失败情形：
     *
     * 1. **只在本行 / 本列内走**：按 ← 却跳到上一行，玩家会立刻失去方向感；
     * 2. **跳过题面格**：题面改不动，光标停在上面只会表现为"按数字没反应"；
     * 3. **该行 / 列没有空格时，退到"可编辑格"**（玩家自己填过的格）——
     *    否则一旦某行填满，键盘玩家就**再也回不到自己填过的格子上去改它**了。
     *    这条是"只在空格间跳"最容易漏掉的漏洞。
     */
    fun nextInDirection(game: Game, from: Int, dRow: Int, dCol: Int): Int {
        var firstEditable: Int? = null
        var pos = from
        // 环绕一圈（9 步，最后一步回到起点）：整行都是空格时也会停回起点，不会空转
        repeat(9) {
            val row = (Sudoku.rowOf(pos) + dRow + 9) % 9
            val col = (Sudoku.colOf(pos) + dCol + 9) % 9
            pos = row * 9 + col
            if (Sudoku.isGiven(game, pos)) return@repeat
            if (firstEditable == null) firstEditable = pos
            if (game.current[pos] == 0) return pos
        }
        return firstEditable ?: from
    }

    /**
     * 填字后的自动跳转（策略 b：**九宫格优先**）：
     *
     * 1. 先在本宫内环绕找空格（从当前格之后开始，绕回宫首）——把这一个宫填完；
     * 2. 本宫已填满 → 退回**横向优先**（从当前格之后按读序环绕全盘）；
     * 3. 全盘无空格 → 返回 null（保持原地，通常意味着刚通关）。
     */
    fun nextAfterFill(board: Board, from: Int): Int? {
        val cells = boxCells(Sudoku.boxOf(from))
        val start = cells.indexOf(from)
        // 只看本宫**其它** 8 格：`from` 刚刚被填上，把它算进来会退化成"原地不动"
        for (step in 1..8) {
            val pos = cells[(start + step) % 9]
            if (board[pos] == 0) return pos
        }
        return nextByReadingOrder(board, from)
    }

    /** 策略 a：从 [from] 之后按读序环绕全盘找空格（也是策略 b 的回退路径；不含 [from] 自身）。 */
    fun nextByReadingOrder(board: Board, from: Int): Int? {
        for (step in 1..80) {
            val pos = (from + step) % 81
            if (board[pos] == 0) return pos
        }
        return null
    }

    /** 某个宫的 9 个下标，按读序排列。 */
    fun boxCells(box: Int): IntArray {
        val topRow = box / 3 * 3
        val leftCol = box % 3 * 3
        return IntArray(9) { i -> (topRow + i / 3) * 9 + (leftCol + i % 3) }
    }
}
